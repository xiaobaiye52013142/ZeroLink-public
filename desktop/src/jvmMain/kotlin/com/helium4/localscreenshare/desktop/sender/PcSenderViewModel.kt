package com.helium4.localscreenshare.desktop.sender

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Toolkit
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.random.Random

class PcSenderViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _pin = MutableStateFlow(generatePin())
    val pin: StateFlow<String> = _pin

    private val _viewerCount = MutableStateFlow(0)
    val viewerCount: StateFlow<Int> = _viewerCount

    private val _isServing = MutableStateFlow(false)
    val isServing: StateFlow<Boolean> = _isServing

    private val _usbReady = MutableStateFlow(false)
    val usbReady: StateFlow<Boolean> = _usbReady

    val localIp: String = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { !it.isLoopbackAddress && it is java.net.Inet4Address }
            ?.hostAddress ?: "unknown"
    }.getOrDefault("unknown")

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    // Pre-initialized so the first client doesn't wait for Robot() + ImageIO to init
    private var robot: Robot? = null
    private var captureWriter: javax.imageio.ImageWriter? = null
    private var captureParam: ImageWriteParam? = null

    fun startServing(fps: Int = 24) {
        if (_isServing.value) return
        _isServing.value = true
        val currentPin = _pin.value
        serverJob = scope.launch {
            // Warm up Robot and JPEG writer before the first client connects so there
            // is no multi-second delay between PIN exchange and the first frame, which
            // would trip the Android receiver's socket read timeout.
            val r = runCatching { Robot() }.getOrNull()
            robot = r
            val writers = ImageIO.getImageWritersByFormatName("JPEG")
            if (writers.hasNext()) {
                val w = writers.next()
                w.defaultWriteParam.also { p ->
                    p.compressionMode = ImageWriteParam.MODE_EXPLICIT
                    p.compressionQuality = 0.6f
                    captureParam = p
                }
                captureWriter = w
            }

            val ss = ServerSocket(5000)
            serverSocket = ss
            try {
                while (isActive) {
                    val client = ss.accept()
                    launch { handleClient(client, currentPin, fps) }
                }
            } catch (_: Exception) {
            } finally {
                captureWriter?.dispose(); captureWriter = null
                robot = null
                try { ss.close() } catch (_: Exception) {}
                withContext(Dispatchers.Main.immediate) {
                    _isServing.value = false
                    _viewerCount.value = 0
                    _usbReady.value = false
                }
            }
        }
    }

    fun setupUsb() {
        scope.launch {
            _usbReady.value = runAdbReverse()
        }
    }

    private suspend fun handleClient(socket: Socket, pin: String, fps: Int) {
        try {
            val input = DataInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            socket.soTimeout = 10_000
            val buf = ByteArray(6)
            input.readFully(buf)
            val received = String(buf).trim()
            if (received != pin) { socket.close(); return }
            socket.soTimeout = 0
            withContext(Dispatchers.Main.immediate) { _viewerCount.value++ }
            val r = robot ?: Robot()
            val screen = Toolkit.getDefaultToolkit().screenSize
            val bounds = Rectangle(screen)
            val interval = 1000L / fps
            val writer = captureWriter ?: ImageIO.getImageWritersByFormatName("JPEG").next()
            val param = captureParam ?: writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.6f
            }
            try {
                while (currentCoroutineContext().isActive) {
                    val start = System.currentTimeMillis()
                    val img = r.createScreenCapture(bounds)
                    val baos = ByteArrayOutputStream()
                    val ios = ImageIO.createImageOutputStream(baos)
                    writer.output = ios
                    writer.write(null, IIOImage(img, null, null), param)
                    ios.close()
                    val bytes = baos.toByteArray()
                    val lenBytes = byteArrayOf(
                        (bytes.size shr 24).toByte(),
                        (bytes.size shr 16).toByte(),
                        (bytes.size shr 8).toByte(),
                        bytes.size.toByte()
                    )
                    output.write(lenBytes)
                    output.write(bytes)
                    output.flush()
                    val elapsed = System.currentTimeMillis() - start
                    val wait = interval - elapsed
                    if (wait > 0) delay(wait)
                }
            } finally {
                if (captureWriter == null) writer.dispose()
            }
        } catch (_: Exception) {
        } finally {
            withContext(Dispatchers.Main.immediate) { _viewerCount.value = (_viewerCount.value - 1).coerceAtLeast(0) }
            try { socket.close() } catch (_: Exception) {}
        }
    }

    fun stopServing() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        _isServing.value = false
        _viewerCount.value = 0
        _pin.value = generatePin()
    }

    fun dispose() {
        scope.cancel()
        stopServing()
    }
}

private fun generatePin(): String = (Random.nextInt(900_000) + 100_000).toString()

/**
 * Runs `adb reverse tcp:5000 tcp:5000` so the phone's localhost:5000 tunnels to this
 * PC's port 5000. Tries `adb` on PATH first, then the common Android SDK location.
 */
fun runAdbReverse(localPort: Int = 5000, remotePort: Int = 5000): Boolean {
    val candidates = buildList {
        add("adb")
        System.getProperty("user.home")?.let { home ->
            add("$home/AppData/Local/Android/Sdk/platform-tools/adb.exe")
            add("$home/Library/Android/sdk/platform-tools/adb")
            add("$home/Android/Sdk/platform-tools/adb")
        }
        System.getenv("ANDROID_HOME")?.let { add("$it/platform-tools/adb") }
        System.getenv("ANDROID_SDK_ROOT")?.let { add("$it/platform-tools/adb") }
    }
    for (adb in candidates) {
        try {
            val result = ProcessBuilder(adb, "reverse", "tcp:$remotePort", "tcp:$localPort")
                .redirectErrorStream(true)
                .start()
            if (result.waitFor() == 0) return true
        } catch (_: Exception) {}
    }
    return false
}
