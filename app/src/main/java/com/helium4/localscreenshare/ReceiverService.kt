package com.helium4.localscreenshare

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Network
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.helium4.localscreenshare.AppLogger
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

private const val LOGTAG = "ZeroLink"

enum class ConnectionState { CONNECTING, CONNECTED, ERROR }

/**
 * Shares the live session (frame, connection state, control channel) between
 * [ReceiverService] — which owns the socket and survives the screen locking — and
 * [ReceiverActivity], which only observes and renders. Same pattern as [ProjectionHolder].
 */
object ReceiverHolder {
    val connectionState = mutableStateOf(ConnectionState.CONNECTING)
    val errorMessage = mutableStateOf("")
    val frameCount = mutableIntStateOf(0)
    val currentBitmap = mutableStateOf<Bitmap?>(null)
    val controlSender = mutableStateOf<ControlSender?>(null)

    /**
     * Sender aspect ratio (width / height), taken from the first decoded frame. The wire protocol
     * carries no dimensions — deriving it here keeps the PC sender (sender.py) compatible while
     * still letting large-screen layouts reserve correctly-proportioned space for the stream.
     */
    val streamAspect = mutableStateOf<Float?>(null)

    // Set by ReceiverActivity: true only while it is started and actually rendering. The service
    // keeps draining the socket when this is false but stops decoding/recycling, so it can never
    // recycle a bitmap that a paused (non-recomposing) PhotoView still holds.
    @Volatile
    var rendererVisible = false

    /**
     * Clears state for a fresh session. Does NOT recycle the visible frame — a paused view may
     * still reference it; the reference is dropped and GC reclaims it.
     */
    fun reset() {
        connectionState.value = ConnectionState.CONNECTING
        errorMessage.value = ""
        frameCount.intValue = 0
        currentBitmap.value = null
        controlSender.value = null
        streamAspect.value = null
    }
}

/**
 * Foreground service that owns the receiver's socket, the frame read loop and the
 * [ControlSender]. Holding a Wi-Fi + partial wake lock keeps the connection alive when
 * the viewer's screen is off, so the OS can't freeze the process and trip the read timeout.
 */
class ReceiverService : Service() {

    companion object {
        const val EXTRA_IP = "DEVICE_IP"
        const val EXTRA_PIN = "DEVICE_PIN"
        const val EXTRA_PORT = "DEVICE_PORT"
        const val EXTRA_SSID = "DEVICE_SSID"
        const val EXTRA_PASS = "DEVICE_PASS"
        private const val CHANNEL_ID = "receiver_session"
        private const val NOTIF_ID = 2
        private const val ACTION_STOP = "com.helium4.localscreenshare.STOP_RECEIVER"
        private const val DEFAULT_PORT = 5000
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val STREAM_TIMEOUT_MS = 10_000
        private const val MAX_FRAME_BYTES = 25 * 1024 * 1024

        // Process-global "a session is live" flag so the Activity can tell a config-change
        // recreation (service alive) apart from a fresh launch or process-death restart.
        @Volatile
        var isRunning = false
            private set
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var streamJob: Job? = null
    private var socket: Socket? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var p2pConnection: P2pConnection? = null
    // The joined Wi-Fi Direct network, when this session came from a QR. Every socket for the
    // session must be created from its socketFactory: join() deliberately avoids
    // bindProcessToNetwork, so a plain Socket() goes out the default network and can never
    // reach the group owner at 192.168.49.1.
    private var p2pNetwork: Network? = null
    // Bitmap swapped out one frame ago; recycled only once the View has adopted its successor.
    private var toRecycle: Bitmap? = null
    // Identifies the currently-streaming target so a duplicate start is ignored but a new target restarts.
    private var currentKey: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        registerReceiver(stopReceiver, IntentFilter(ACTION_STOP), Context.RECEIVER_NOT_EXPORTED)
        acquireLocks()
        AppLogger.i(LOGTAG, "[ReceiverService] Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ip = intent?.getStringExtra(EXTRA_IP)
        val pin = intent?.getStringExtra(EXTRA_PIN)
        val port = intent?.getIntExtra(EXTRA_PORT, DEFAULT_PORT) ?: DEFAULT_PORT
        val ssid = intent?.getStringExtra(EXTRA_SSID)
        val pass = intent?.getStringExtra(EXTRA_PASS)
        startForegroundCompat(buildNotification(ip ?: "?"))
        if (ip == null || pin == null) {
            AppLogger.w(LOGTAG, "[ReceiverService] Missing ip/pin, stopping")
            stopSelf(); return START_NOT_STICKY
        }
        val newKey = "$ip|$port|${ssid.orEmpty()}|$pin"
        if (streamJob != null && newKey == currentKey) {
            // Same session re-delivered (e.g. duplicate start) — keep the live stream untouched.
            return START_NOT_STICKY
        }
        // New or different target: tear down any existing stream and start fresh for this one.
        streamJob?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        p2pConnection?.close(); p2pConnection = null
        p2pNetwork = null
        toRecycle?.recycle(); toRecycle = null
        currentKey = newKey
        ReceiverHolder.reset()
        startStreaming(ip, port, pin, ssid, pass)
        // Inputs live only in the intent extras, which a sticky restart drops — so don't pretend
        // we can auto-restart. The Activity restarts us via ReceiverService.isRunning if needed.
        return START_NOT_STICKY
    }

    private fun startStreaming(ip: String, port: Int, pin: String, ssid: String?, pass: String?) {
        streamJob = scope.launch {
            var localSocket: Socket? = null
            try {
                val s = openSocket(ip, port, ssid, pass)
                localSocket = s
                socket = s

                val out = s.getOutputStream()
                out.write(pin.toByteArray()); out.flush()

                val sender = ControlSender(DataOutputStream(out), scope)
                withContext(Dispatchers.Main) {
                    ReceiverHolder.controlSender.value = sender
                    ReceiverHolder.connectionState.value = ConnectionState.CONNECTED
                }

                val input = DataInputStream(s.getInputStream())
                AppLogger.i(LOGTAG, "[ReceiverService] Connected, waiting for frames")
                // Try audio port in a child coroutine; silently ignored if sender has no audio.
                launch { connectAudio(ip, port + 1, pin) }
                while (isActive) {
                    val size = input.readInt()
                    if (size <= 0 || size > MAX_FRAME_BYTES) {
                        throw IllegalStateException("Invalid frame size: $size")
                    }
                    val bytes = ByteArray(size)
                    input.readFully(bytes)
                    // Keep draining while backgrounded so the connection stays alive, but skip
                    // decode/publish/recycle: a paused PhotoView still holds the last bitmap and
                    // won't recompose, so recycling here would crash the next draw on resume.
                    if (!ReceiverHolder.rendererVisible) continue
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                    withContext(Dispatchers.Main) {
                        ReceiverHolder.currentBitmap.value = bmp
                        if (ReceiverHolder.streamAspect.value == null && bmp.height > 0)
                            ReceiverHolder.streamAspect.value = bmp.width.toFloat() / bmp.height
                        ReceiverHolder.frameCount.intValue++
                    }
                }
            } catch (e: Exception) {
                if (isActive) {
                    val kind = when (e) {
                        is kotlinx.coroutines.TimeoutCancellationException -> "timed out waiting to join Wi-Fi Direct"
                        is java.io.IOException -> "I/O error (connection dropped or refused)"
                        is IllegalStateException -> "protocol error"
                        else -> e.javaClass.simpleName
                    }
                    AppLogger.e(LOGTAG, "[ReceiverService] Stream failed: $kind — ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        ReceiverHolder.connectionState.value = ConnectionState.ERROR
                        ReceiverHolder.errorMessage.value = e.message ?: "Connection failed"
                    }
                }
            } finally {
                try { localSocket?.close() } catch (_: Exception) {}
                // Self-stop only on a genuine disconnect/error — not when cancelled to restart for
                // a new target or during onDestroy (those manage the lifecycle themselves).
                if (coroutineContext[Job]?.isCancelled == false) {
                    AppLogger.i(LOGTAG, "[ReceiverService] Stream ended, stopping service")
                    stopSelf()
                }
            }
        }
    }

    private suspend fun connectAudio(ip: String, audioPort: Int, pin: String) {
        try {
            val sock = withContext(Dispatchers.IO) {
                // Same network as the video socket, or the default one for same-Wi-Fi sessions.
                val net = p2pNetwork
                val s = net?.socketFactory?.createSocket() ?: Socket()
                s.apply {
                    connect(InetSocketAddress(ip, audioPort), 5000)
                    // The audio port authenticates with the same PIN as the video port.
                    getOutputStream().apply { write(pin.toByteArray()); flush() }
                }
            }
            val input = DataInputStream(sock.getInputStream())
            val minBuf = AudioTrack.getMinBufferSize(
                44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            track.play()
            AppLogger.i(LOGTAG, "[Audio] Receiving from $ip:$audioPort")
            try {
                while (currentCoroutineContext().isActive) {
                    val size = input.readInt()
                    if (size <= 0 || size > 512 * 1024) break
                    val buf = ByteArray(size)
                    input.readFully(buf)
                    track.write(buf, 0, buf.size)
                }
            } finally {
                try { track.stop() } catch (_: Exception) {}
                track.release()
                try { sock.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            AppLogger.i(LOGTAG, "[Audio] Not available: ${e.message}")
        }
    }

    /**
     * Opens the stream socket. When [ssid]/[pass] are present (QR / Wi-Fi Direct mode) the
     * socket is routed over the joined P2P network; otherwise a plain socket over the
     * already-shared Wi-Fi (NSD mode) is used. Both use an explicit connect timeout.
     */
    private suspend fun openSocket(ip: String, port: Int, ssid: String?, pass: String?): Socket {
        return if (!ssid.isNullOrEmpty() && !pass.isNullOrEmpty()
            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            AppLogger.i(LOGTAG, "[ReceiverService] Joining Wi-Fi Direct $ssid then $ip:$port")
            val conn = P2pConnection(this).also { p2pConnection = it }
            val network = conn.join(ssid, pass)
            p2pNetwork = network
            network.socketFactory.createSocket().apply {
                connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                soTimeout = STREAM_TIMEOUT_MS
            }
        } else {
            AppLogger.i(LOGTAG, "[ReceiverService] Connecting to $ip:$port")
            Socket().apply {
                connect(InetSocketAddress(ip, port), CONNECT_TIMEOUT_MS)
                soTimeout = STREAM_TIMEOUT_MS
            }
        }
    }

    private fun acquireLocks() {
        try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "ZeroLink:wifi").apply {
                setReferenceCounted(false); acquire()
            }
            val power = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZeroLink:receiver").apply {
                setReferenceCounted(false); acquire()
            }
        } catch (e: Exception) {
            AppLogger.e(LOGTAG, "[ReceiverService] Lock acquire failed ${e.message}")
        }
    }

    private fun releaseLocks() {
        try { if (wifiLock?.isHeld == true) wifiLock?.release() } catch (_: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(ip: String): Notification {
        val stopIntent = Intent(ACTION_STOP).apply { setPackage(packageName) }
        val stopPending = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Viewing Remote Screen")
            .setContentText("Connected to $ip")
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", stopPending)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CHANNEL_ID, "Remote Viewer", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(ch)
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppLogger.i(LOGTAG, "[ReceiverService] Stop action received")
            stopSelf()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.w(LOGTAG, "[ReceiverService] Destroying")
        isRunning = false
        currentKey = null
        ReceiverHolder.rendererVisible = false
        try { unregisterReceiver(stopReceiver) } catch (_: Exception) {}
        streamJob?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        p2pConnection?.close()
        p2pNetwork = null
        releaseLocks()
        scope.cancel()
        // Drop the singleton's references so a large frame + dead control channel don't linger
        // for the process lifetime. Recycle only the off-screen frame; null the visible one so
        // it's GC-eligible once the View releases it (recycling it could crash a live draw).
        toRecycle?.recycle(); toRecycle = null
        ReceiverHolder.currentBitmap.value = null
        ReceiverHolder.controlSender.value = null
    }
}
