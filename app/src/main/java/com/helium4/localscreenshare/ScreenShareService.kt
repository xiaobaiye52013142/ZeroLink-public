package com.helium4.localscreenshare

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.media.*
import android.media.projection.*
import android.net.nsd.*
import android.os.*
import android.util.DisplayMetrics
import android.app.KeyguardManager
import android.os.Handler
import android.os.Looper
import com.helium4.localscreenshare.AppLogger
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.math.max

private const val LOGTAG = "ZeroLink"

/**
 * Shares live sender-side session state with [SenderActivity]. Same pattern as ReceiverHolder.
 */
object SenderHolder {

    private val main = Handler(Looper.getMainLooper())

    /**
     * Compose snapshot writes from a background thread can be lost. Every mutator below goes
     * through this, because the writers are all service coroutines on Dispatchers.IO.
     */
    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    /** Number of receivers currently connected and streaming. */
    val viewerCount = mutableIntStateOf(0)

    /**
     * True while exactly one viewer is connected. Remote control is only permitted then —
     * with several viewers, concurrent gesture injection would fight over the same screen.
     */
    val controlEnabled = mutableStateOf(false)

    /** Set to true when a second viewer is rejected because multi-receiver is not unlocked. */
    val premiumRequired = mutableStateOf(false)

    /**
     * Set when the session has died for a reason the user needs to act on. The service can
     * stop itself (port in use, capture revoked) with no other way to tell the Activity, which
     * would otherwise keep displaying a live PIN and QR for a session that no longer exists.
     */
    val sessionError = mutableStateOf<String?>(null)

    fun setViewerCount(count: Int) = onMain { viewerCount.intValue = count }

    fun setControlEnabled(enabled: Boolean) = onMain { controlEnabled.value = enabled }

    fun setPremiumRequired() = onMain { premiumRequired.value = true }

    fun fail(message: String) = onMain { sessionError.value = message }

    fun reset() = onMain {
        viewerCount.intValue = 0
        controlEnabled.value = false
        premiumRequired.value = false
        sessionError.value = null
    }
}

class ScreenShareService : Service() {

    companion object {
        private const val CHANNEL_ID = "screen_share"
        private const val PORT = 5000
        private const val AUDIO_PORT = 5001
        private const val SERVICE_TYPE = "_localscreenshare._tcp."
        private const val ACTION_STOP = "com.helium4.localscreenshare.STOP_SERVICE"
        // Longest capture edge; the GPU downscales the mirror to this so capture,
        // copy and encode all shrink (~4-6x less CPU at typical phone resolutions).
        private const val MAX_CAPTURE_DIM = 1280
        private const val AUDIO_SAMPLE_RATE = 44100
        // Must stay comfortably under ReceiverService.STREAM_TIMEOUT_MS (10 s) so an idle
        // screen can never look like a dead connection.
        private const val KEEPALIVE_MS = 3000L
        // A connection must present its PIN within this window or it is dropped. Without it,
        // a socket that sends nothing parks a coroutine and an fd for the whole session.
        private const val AUTH_TIMEOUT_MS = 5000
        // Ceiling on sockets sitting in the handshake at once, so opening connections in bulk
        // can't exhaust the IO dispatcher and starve the accept/fan-out loops.
        private const val MAX_PENDING_AUTH = 8
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private val jpegQueue = ArrayBlockingQueue<ByteArray>(3)
    private var nsdManager: NsdManager? = null
    private var nsdListener: NsdManager.RegistrationListener? = null
    private var registered = false
    private var pairingCode = ""
    private var shareAudio = false
    private var audioRecord: AudioRecord? = null
    private var audioServerSocket: ServerSocket? = null
    /** An audio consumer. The socket is retained so a dropped client can actually be closed. */
    private class AudioClient(val socket: Socket, val out: DataOutputStream)

    private val audioClients = CopyOnWriteArrayList<AudioClient>()

    /**
     * One connected receiver. Each holds its OWN bounded frame queue drained by its own writer
     * coroutine, so a slow/lagging viewer drops only its own frames instead of blocking the
     * broadcast loop (and therefore every other viewer) on a stalled socket write.
     */
    private class Client(
        val socket: Socket,
        val input: DataInputStream,
        val out: DataOutputStream,
        val address: String
    ) {
        val queue = ArrayBlockingQueue<ByteArray>(2)
    }

    private val clients = CopyOnWriteArrayList<Client>()
    private var serverSocket: ServerSocket? = null
    // Sockets currently in the PIN handshake, i.e. not yet trusted.
    private val pendingAuth = java.util.concurrent.atomic.AtomicInteger(0)
    // Guards clients/controlJob transitions. onClientsChanged and the admission check run from
    // every client's IO coroutine, so the viewer-count and control state machine needs a lock.
    private val clientLock = Any()
    // Control back-channel job; runs only while exactly one viewer is connected.
    private var controlJob: Job? = null
    // Bumped each time a control loop is started, so a loop that dies can tell whether it is
    // still the current one before clearing shared state a newer loop now owns.
    private var controlGen = 0
    private var displayStarted = false
    private var handlerThread: HandlerThread? = null
    private var targetFps = 20
    // Read on the ImageReader thread, written on the binder thread in onStartCommand.
    @Volatile private var frameIntervalMs = 1000L / targetFps
    private var localIp: String? = null
    // Full real display size — used by control-gesture coordinate mapping; must stay full-res.
    private var displayWidth = 0
    private var displayHeight = 0
    // Downscaled capture size — what the VirtualDisplay actually renders and we encode.
    private var captureWidth = 0
    private var captureHeight = 0
    private var jpegQuality = 60
    // Rate-limit state and reusable buffers so we don't allocate/encode per raw frame.
    private var lastEncodeMs = 0L
    private var reusableBitmap: Bitmap? = null
    private val jpegStream = ByteArrayOutputStream()
    // Keep CPU + Wi-Fi alive so doze / screen-off / backgrounding can't freeze the capture loop.
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), Context.RECEIVER_NOT_EXPORTED)
        acquireLocks()
        AppLogger.i(LOGTAG, "[Service] Created and running foreground")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        pairingCode = intent?.getStringExtra("PAIR_PIN") ?: PinStore.pinForSession(this)
        targetFps = intent?.getIntExtra("TARGET_FPS", 20)?.coerceIn(5, 60) ?: 20
        jpegQuality = intent?.getIntExtra("JPEG_QUALITY", 60)?.coerceIn(20, 90) ?: 60
        frameIntervalMs = 1000L / max(1, targetFps)
        shareAudio = intent?.getBooleanExtra("SHARE_AUDIO", false) == true
        localIp = getLocalIpAddress()
        startForeground(1, buildNotification())

        AppLogger.i(LOGTAG, "[Service] onStartCommand targetFps=$targetFps ip=$localIp")

        val resultCode = ProjectionHolder.resultCode
        val data = ProjectionHolder.data
        if (resultCode == Activity.RESULT_OK && data != null) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(resultCode, data)
            SenderHolder.reset()
            // Capture starts immediately rather than on first connect: with multiple viewers there
            // is no single "first client" to hang the projection setup off, and frames must already
            // be flowing so a viewer joining later sees content on its very first read.
            if (!displayStarted) { displayStarted = true; startVirtualDisplay() }
            startSharing()
            if (shareAudio && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startAudioCapture()
            registerNsdService()
        } else stopSelf()
        // A MediaProjection token can't survive a process restart, so don't let the OS relaunch us
        // with a null intent and a stale/regenerated PIN — the sender re-grants consent explicitly.
        return START_NOT_STICKY
    }

    private fun startSharing() {
        // Accept loop: keeps listening so additional viewers can join an in-progress session.
        scope.launch {
            val server = try {
                AppLogger.i(LOGTAG, "[Service] Opening server socket on port $PORT")
                ServerSocket(PORT).also { serverSocket = it }
            } catch (e: Exception) {
                // Failing to bind is fatal and unrecoverable: no viewer can ever connect, so
                // say so instead of leaving the sender showing a PIN nothing is listening on.
                AppLogger.e(LOGTAG, "[Network] Could not open port $PORT: ${e.message}", e)
                SenderHolder.fail("Port $PORT is already in use. Close the other app or session, then try again.")
                stopSelf()
                return@launch
            }
            while (isActive) {
                val socket = try {
                    server.accept()
                } catch (e: Exception) {
                    // A close() during teardown lands here too. Either way, losing the accept
                    // loop must NOT kill a session that viewers are already watching — it only
                    // means no further viewers can join.
                    if (isActive) AppLogger.e(LOGTAG, "[Network] Accept loop ended: ${e.message}", e)
                    break
                }
                // Reject before spawning anything when the handshake queue is saturated.
                if (pendingAuth.get() >= MAX_PENDING_AUTH) {
                    AppLogger.w(LOGTAG, "[Network] Too many unauthenticated connections, dropping ${socket.inetAddress?.hostAddress}")
                    try { socket.close() } catch (_: Exception) {}
                    continue
                }
                launch { handleClient(socket) }
            }
        }

        // Fan-out loop: hands each encoded frame to every connected viewer's private queue.
        scope.launch {
            var lastFrame: ByteArray? = null
            while (isActive) {
                // A still screen produces no new ImageReader frames at all, so a plain take()
                // would block indefinitely and trip the receiver's socket read timeout. Re-send
                // the most recent frame as a keepalive well inside that timeout instead; this
                // needs no protocol change, so sender.py-compatible receivers are unaffected.
                val frame = jpegQueue.poll(KEEPALIVE_MS, TimeUnit.MILLISECONDS)
                if (frame != null) lastFrame = frame
                val outgoing = frame ?: lastFrame ?: continue
                for (c in clients) {
                    // Never block here. If this viewer is behind, drop its oldest frame and keep the
                    // newest, so it converges on current content instead of falling further behind.
                    if (!c.queue.offer(outgoing)) { c.queue.poll(); c.queue.offer(outgoing) }
                }
            }
        }
    }

    /** Authenticates one receiver, then serves it until it disconnects. */
    private suspend fun handleClient(socket: Socket) = coroutineScope {
        val address = socket.inetAddress.hostAddress ?: "?"
        AppLogger.i(LOGTAG, "[Network] Client connected: $address")
        var client: Client? = null
        var counted = false
        try {
            pendingAuth.incrementAndGet()
            counted = true
            val input = DataInputStream(socket.getInputStream())
            val output = DataOutputStream(socket.getOutputStream())
            val buffer = ByteArray(6)
            // Bound the handshake; a silent socket would otherwise block here forever.
            socket.soTimeout = AUTH_TIMEOUT_MS
            // readFully guarantees all 6 PIN bytes arrive before comparison.
            input.readFully(buffer)
            val receivedPin = String(buffer).trim()

            if (receivedPin != pairingCode) {
                // Reject this socket only — one bad PIN must never tear down a live session
                // that other viewers are watching.
                AppLogger.w(LOGTAG, "[Auth] Invalid PIN from $address, closing")
                return@coroutineScope
            }

            // Checked under clientLock together with the add below, so two simultaneous
            // connects can't both observe an empty list and both be admitted.
            if (synchronized(clientLock) {
                    clients.isNotEmpty() && !RemoteConfigManager.isMultiReceiverUnlocked()
                }
            ) {
                AppLogger.w(LOGTAG, "[Auth] $address rejected — multi-receiver is a premium feature (${clients.size} viewer(s) already connected)")
                SenderHolder.setPremiumRequired()
                return@coroutineScope
            }

            // Authenticated: drop the handshake deadline. The control loop's reads are
            // open-ended by design, and this socket is now trusted.
            socket.soTimeout = 0
            pendingAuth.decrementAndGet()
            counted = false

            val c = Client(socket, input, output, address)
            client = c
            clients.add(c)
            onClientsChanged()
            AppLogger.i(LOGTAG, "[Auth] $address authenticated, ${clients.size} viewer(s)")

            // Blocking writes happen here, on this client's own coroutine and queue.
            var frameCount = 0
            while (isActive) {
                val frame = c.queue.take()
                output.writeInt(frame.size)
                output.write(frame)
                output.flush()
                frameCount++
                if (frameCount % 50 == 0)
                    AppLogger.i(LOGTAG, "[Stream] $address: $frameCount frames, last=${frame.size} bytes")
            }
        } catch (e: Exception) {
            AppLogger.i(LOGTAG, "[Network] Client $address ended: ${e.message}")
        } finally {
            if (counted) pendingAuth.decrementAndGet()
            client?.let { clients.remove(it) }
            try { socket.close() } catch (_: Exception) {}
            // A viewer leaving is normal — the session stays up for everyone else, and for
            // nobody at all. Only an explicit Stop or MediaProjection.onStop ends the service.
            withContext(NonCancellable) { onClientsChanged() }
            AppLogger.i(LOGTAG, "[Network] $address removed, ${clients.size} viewer(s) remain")
        }
    }

    /**
     * Recomputes viewer count and control eligibility whenever a client connects or disconnects.
     * Remote control runs only while exactly one viewer is connected. When a second viewer joins
     * the loop is cancelled; when a viewer leaves and only one remains the loop is restarted on
     * that surviving viewer's input stream — derived from [clients] directly so the caller never
     * needs to know which client triggered the change.
     */
    private fun onClientsChanged() = synchronized(clientLock) {
        val count = clients.size
        SenderHolder.setViewerCount(count)
        if (count == 1 && controlJob == null) {
            // Start (or re-start after a second viewer left) on the sole remaining client's input.
            val sole = clients.firstOrNull() ?: return@synchronized
            controlGen++
            val gen = controlGen
            controlJob = scope.launch {
                try {
                    controlReadLoop(sole.input)
                } finally {
                    // The loop can also end on its own (a malformed command, a socket error).
                    // Clearing controlJob here is what lets onClientsChanged restart it;
                    // without this, remote control stayed dead for the rest of the session
                    // while the UI kept claiming it was enabled.
                    onControlLoopEnded(gen)
                }
            }
            SenderHolder.setControlEnabled(true)
            AppLogger.i(LOGTAG, "[Control] Enabled for the single connected viewer (${sole.address})")
        } else if (count != 1 && controlJob != null) {
            controlJob?.cancel()
            controlJob = null
            SenderHolder.setControlEnabled(false)
            AppLogger.i(LOGTAG, "[Control] Disabled — $count viewers connected")
        }
    }

    /** Releases control state, but only if [gen] is still the live control loop. */
    private fun onControlLoopEnded(gen: Int) = synchronized(clientLock) {
        if (gen != controlGen) return@synchronized
        controlJob = null
        SenderHolder.setControlEnabled(false)
        AppLogger.i(LOGTAG, "[Control] Loop ended, control released")
    }

    private suspend fun controlReadLoop(input: DataInputStream) {
        AppLogger.i(LOGTAG, "[Control] Read loop started")
        try {
            while (currentCoroutineContext().isActive) {
                val cmd = ControlProtocol.readCommand(input)
                val svc = ControlAccessibilityService.instance
                if (svc == null) {
                    AppLogger.w(LOGTAG, "[Control] Accessibility service not enabled, ignoring $cmd")
                    continue
                }
                // Clamp just inside the real display so gestures are never rejected for being off-screen.
                val maxX = (displayWidth - 1).coerceAtLeast(0).toFloat()
                val maxY = (displayHeight - 1).coerceAtLeast(0).toFloat()
                fun px(n: Float) = (n * maxX).coerceIn(0f, maxX)
                fun py(n: Float) = (n * maxY).coerceIn(0f, maxY)
                // Accessibility gesture/global calls run on the main thread.
                withContext(Dispatchers.Main) {
                    when (cmd) {
                        is ControlProtocol.Command.Tap ->
                            if (cmd.longPress) svc.longPress(px(cmd.nx), py(cmd.ny))
                            else svc.tap(px(cmd.nx), py(cmd.ny))
                        is ControlProtocol.Command.Swipe ->
                            svc.swipe(px(cmd.nx1), py(cmd.ny1), px(cmd.nx2), py(cmd.ny2), cmd.durationMs)
                        is ControlProtocol.Command.Global -> svc.globalAction(cmd.action)
                        is ControlProtocol.Command.Text -> svc.typeText(cmd.text)
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.i(LOGTAG, "[Control] Read loop ended: ${e.message}")
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun startAudioCapture() {
        val proj = projection ?: return
        try {
            val config = AudioPlaybackCaptureConfiguration.Builder(proj)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(
                AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(AUDIO_SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .build()
            audioRecord = record
            record.startRecording()
            AppLogger.i(LOGTAG, "[Audio] Capture started, minBuf=$minBuf")

            // Read PCM and fan out to audio clients.
            val buf = ByteArray(minBuf)
            scope.launch {
                while (isActive) {
                    val read = record.read(buf, 0, buf.size)
                    if (read > 0 && audioClients.isNotEmpty()) {
                        val chunk = buf.copyOf(read)
                        for (client in audioClients) {
                            try {
                                client.out.writeInt(chunk.size)
                                client.out.write(chunk)
                                client.out.flush()
                            } catch (_: Exception) {
                                // Also close the socket: removing it from the list alone
                                // leaked the fd for the process lifetime.
                                audioClients.remove(client)
                                try { client.socket.close() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            // Accept audio-port connections. This port carries live system audio, so it
            // requires the same PIN as the video port — it previously accepted anyone.
            scope.launch {
                try {
                    val server = ServerSocket(AUDIO_PORT).also { audioServerSocket = it }
                    AppLogger.i(LOGTAG, "[Audio] Listening on port $AUDIO_PORT")
                    while (isActive) {
                        val sock = server.accept()
                        launch { handleAudioClient(sock) }
                    }
                } catch (e: Exception) {
                    if (isActive) AppLogger.e(LOGTAG, "[Audio] Server error ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(LOGTAG, "[Audio] Capture setup failed ${e.message}", e)
        }
    }

    /** Authenticates an audio consumer with the session PIN before it is fed any audio. */
    private fun handleAudioClient(sock: Socket) {
        val address = sock.inetAddress?.hostAddress ?: "?"
        try {
            sock.soTimeout = AUTH_TIMEOUT_MS
            val buffer = ByteArray(6)
            DataInputStream(sock.getInputStream()).readFully(buffer)
            if (String(buffer).trim() != pairingCode) {
                AppLogger.w(LOGTAG, "[Audio] Invalid PIN from $address, closing")
                try { sock.close() } catch (_: Exception) {}
                return
            }
            sock.soTimeout = 0
            audioClients.add(AudioClient(sock, DataOutputStream(sock.getOutputStream())))
            AppLogger.i(LOGTAG, "[Audio] Client authenticated: $address")
        } catch (e: Exception) {
            AppLogger.i(LOGTAG, "[Audio] Handshake failed for $address: ${e.message}")
            try { sock.close() } catch (_: Exception) {}
        }
    }

    /**
     * Android 15 QPR1 and higher stop screen projection as soon as the device locks, where
     * earlier versions kept sharing. The framework gives no reason code with onStop, so the
     * keyguard state is the only signal available - and it is the overwhelmingly likely cause
     * on those versions, because FLAG_KEEP_SCREEN_ON only holds while SenderActivity is in the
     * foreground. Without this the user reads a routine screen timeout as a crash.
     */
    private fun stopReason(): String {
        val locked = try {
            (getSystemService(KEYGUARD_SERVICE) as? KeyguardManager)?.isKeyguardLocked == true
        } catch (_: Exception) {
            false
        }
        return if (locked) {
            "Sharing stopped because the screen locked. Android stops screen capture at the " +
                "lock screen - unlock and start sharing again."
        } else {
            "Screen capture was stopped. Start sharing again to continue."
        }
    }

    private fun startVirtualDisplay() {
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = (getSystemService(WINDOW_SERVICE) as WindowManager).maximumWindowMetrics.bounds
            metrics.widthPixels = bounds.width()
            metrics.heightPixels = bounds.height()
            metrics.densityDpi = resources.configuration.densityDpi
        } else {
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        displayWidth = width
        displayHeight = height

        // Downscale so the longest edge <= MAX_CAPTURE_DIM, preserving aspect ratio and
        // rounding to even dimensions. The GPU renders the mirror at this size for free.
        val longest = max(width, height)
        val scale = if (longest > MAX_CAPTURE_DIM) MAX_CAPTURE_DIM.toFloat() / longest else 1f
        captureWidth = ((width * scale).toInt() and 1.inv()).coerceAtLeast(2)
        captureHeight = ((height * scale).toInt() and 1.inv()).coerceAtLeast(2)
        AppLogger.i(LOGTAG, "[Projection] Display w=$width h=$height d=$density -> capture ${captureWidth}x$captureHeight")

        imageReader = ImageReader.newInstance(captureWidth, captureHeight, PixelFormat.RGBA_8888, 2)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                AppLogger.w(LOGTAG, "[Projection] MediaProjection stopped by system")
                imageReader?.close()
                SenderHolder.fail(stopReason())
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        projection?.createVirtualDisplay(
            "ScreenCapture", captureWidth, captureHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, null
        )

        handlerThread = HandlerThread("ImageReaderThread").apply { start() }
        val handler = Handler(handlerThread!!.looper)
        imageReader!!.setOnImageAvailableListener({ reader ->
            captureFrame(reader)
        }, handler)
        AppLogger.i(LOGTAG, "[Projection] Virtual display created")
    }

    private fun captureFrame(reader: ImageReader) {
        try {
            // Always acquire so the buffer is released, but drop frames that arrive faster
            // than targetFps BEFORE doing any bitmap copy / JPEG encode — the biggest heat win.
            reader.acquireLatestImage()?.use { image ->
                // Monotonic clock so a wall-clock adjustment can't freeze the stream.
                val now = SystemClock.uptimeMillis()
                if (now - lastEncodeMs < frameIntervalMs) return@use
                lastEncodeMs = now

                val plane = image.planes.firstOrNull() ?: return@use
                val buffer: ByteBuffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * captureWidth
                val paddedWidth = captureWidth + rowPadding / pixelStride

                // Reuse one bitmap; recreate only when the stride/size changes.
                var bmp = reusableBitmap
                if (bmp == null || bmp.width != paddedWidth || bmp.height != captureHeight) {
                    bmp?.recycle()
                    bmp = Bitmap.createBitmap(paddedWidth, captureHeight, Bitmap.Config.ARGB_8888)
                    reusableBitmap = bmp
                }
                buffer.rewind()
                bmp.copyPixelsFromBuffer(buffer)

                jpegStream.reset()
                if (rowPadding == 0) {
                    bmp.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegStream)
                } else {
                    // Only allocate a crop when the buffer carries row padding to strip.
                    val cropped = Bitmap.createBitmap(bmp, 0, 0, captureWidth, captureHeight)
                    cropped.compress(Bitmap.CompressFormat.JPEG, jpegQuality, jpegStream)
                    cropped.recycle()
                }
                val bytes = jpegStream.toByteArray()
                // Under backpressure keep the NEWEST frame: drop the oldest and enqueue this one,
                // so a paused screen still converges to the current content instead of a stale frame.
                if (!jpegQueue.offer(bytes)) {
                    jpegQueue.poll()
                    jpegQueue.offer(bytes)
                }
            }
        } catch (e: Exception) {
            AppLogger.e(LOGTAG, "[Frame] Capture error ${e.message}", e)
        }
    }

    private fun acquireLocks() {
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifi.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ZeroLink:senderWifi"
            ).apply { setReferenceCounted(false); acquire() }
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZeroLink:sender").apply {
                setReferenceCounted(false); acquire()
            }
        } catch (e: Exception) {
            AppLogger.e(LOGTAG, "[Service] Lock acquire failed ${e.message}")
        }
    }

    private fun releaseLocks() {
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
    }

    /**
     * The address a receiver on the same Wi-Fi should dial. A Wi-Fi Direct group's p2p
     * interface (192.168.49.x) is not reachable from the router's LAN, so it's only used as a
     * fallback — otherwise enabling Direct would make the notification advertise an address
     * nobody on the LAN can reach.
     */
    private fun getLocalIpAddress(): String? {
        var p2pFallback: String? = null
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr.isLoopbackAddress || addr !is Inet4Address) continue
                    val ip = addr.hostAddress ?: continue
                    if (ip.startsWith("169.254") || ip.startsWith("0.")) continue
                    val isP2p = intf.name.startsWith("p2p") || ip.startsWith("192.168.49.")
                    if (isP2p) {
                        if (p2pFallback == null) p2pFallback = ip
                    } else return ip
                }
            }
        } catch (e: Exception) {
            AppLogger.e(LOGTAG, "[Network] IP lookup failed ${e.message}")
        }
        return p2pFallback
    }

    /** "ZeroLink <model>", trimmed to the 63-byte mDNS instance-name limit. */
    private fun nsdServiceName(): String {
        val model = Build.MODEL?.trim()?.replace(Regex("[^A-Za-z0-9 _-]"), "").orEmpty()
        val name = if (model.isEmpty()) "ZeroLink" else "ZeroLink $model"
        return name.take(63)
    }

    private fun registerNsdService() {
        try {
            nsdManager = getSystemService(NSD_SERVICE) as NsdManager
            val info = NsdServiceInfo().apply {
                // Per-device name: a constant collided between two senders on one network, and
                // the receiver keys its device list by serviceName so one would mask the other.
                serviceName = nsdServiceName()
                serviceType = SERVICE_TYPE
                port = PORT
            }
            nsdListener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(info: NsdServiceInfo?) {
                    registered = true
                    AppLogger.i(LOGTAG, "[NSD] Registered ${info?.serviceName}")
                }
                override fun onRegistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    AppLogger.e(LOGTAG, "[NSD] Registration failed $errorCode")
                }
                override fun onServiceUnregistered(info: NsdServiceInfo?) {
                    AppLogger.i(LOGTAG, "[NSD] Unregistered")
                }
                override fun onUnregistrationFailed(info: NsdServiceInfo?, errorCode: Int) {
                    AppLogger.e(LOGTAG, "[NSD] Unregister failed $errorCode")
                }
            }
            nsdManager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener!!)
        } catch (e: Exception) {
            AppLogger.e(LOGTAG, "[NSD] Exception ${e.message}", e)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Screen Sharing Active")
            .setContentText("IP: $localIp • PIN: $pairingCode • FPS: $targetFps")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppLogger.i(LOGTAG, "[Service] Stop action received")
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Screen Share", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(ch)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.w(LOGTAG, "[Service] Destroying, cleaning up")
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        if (registered && nsdListener != null) {
            try { nsdManager?.unregisterService(nsdListener!!) } catch (_: Exception) {}
        }
        releaseLocks()
        // Drop every viewer and stop listening before the scope dies, so no writer coroutine is
        // left blocked on a socket the process is about to abandon.
        controlJob?.cancel(); controlJob = null
        for (c in clients) { try { c.socket.close() } catch (_: Exception) {} }
        clients.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        SenderHolder.reset()
        // Stop new frames dispatching before tearing down, to shrink the teardown race window.
        try { audioRecord?.stop() } catch (_: Exception) {}
        audioRecord?.release(); audioRecord = null
        for (client in audioClients) {
            try { client.out.close() } catch (_: Exception) {}
            try { client.socket.close() } catch (_: Exception) {}
        }
        audioClients.clear()
        try { audioServerSocket?.close() } catch (_: Exception) {}
        audioServerSocket = null
        imageReader?.setOnImageAvailableListener(null, null)
        imageReader?.close(); projection?.stop(); handlerThread?.quitSafely(); scope.cancel()
        // Don't recycle reusableBitmap here: a capture callback may still be mid-encode on the
        // ImageReader thread (quitSafely doesn't join). Let GC reclaim it with the service.
        reusableBitmap = null
        // Clear the consumed projection token so a same-process restart can't reuse a dead one.
        ProjectionHolder.resultCode = -1
        ProjectionHolder.data = null
        AppLogger.i(LOGTAG, "[Service] Cleanup complete")
    }
}
