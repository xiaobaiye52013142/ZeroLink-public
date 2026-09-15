package com.helium4.localscreenshare.desktop.viewer

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.helium4.localscreenshare.desktop.discovery.DiscoveredDevice
import com.helium4.localscreenshare.desktop.discovery.MdnsDiscovery
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Image
import java.io.DataInputStream
import java.net.Socket

enum class ViewerConnectionState { IDLE, CONNECTING, CONNECTED, ERROR }

class ViewerViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(ViewerConnectionState.IDLE)
    val state: StateFlow<ViewerConnectionState> = _state

    private val _currentImage = MutableStateFlow<ImageBitmap?>(null)
    val currentImage: StateFlow<ImageBitmap?> = _currentImage

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private var socket: Socket? = null
    private var streamJob: Job? = null

    init {
        MdnsDiscovery.startListening(
            onFound = { device ->
                val current = _discoveredDevices.value.toMutableList()
                current.removeAll { it.name == device.name }
                current.add(device)
                _discoveredDevices.value = current
            },
            onLost = { name ->
                _discoveredDevices.value = _discoveredDevices.value.filter { it.name != name }
            }
        )
    }

    fun connectWifi(ip: String, port: Int = 5000, pin: String) {
        _errorMessage.value = null
        connect(ip, port, pin)
    }

    fun connectUsb(pin: String) {
        _errorMessage.value = null
        _state.value = ViewerConnectionState.CONNECTING
        streamJob?.cancel()
        streamJob = scope.launch {
            val adbResult = adbForward()
            if (!adbResult) {
                _state.value = ViewerConnectionState.ERROR
                _errorMessage.value = "ADB forwarding failed. Make sure:\n" +
                    "• USB debugging is enabled on the phone\n" +
                    "• The phone is authorized (check phone screen)\n" +
                    "• ADB is installed and in PATH (or install platform-tools)"
                return@launch
            }
            // Small delay to ensure forwarding is established
            delay(300)
            connectInternal("localhost", 5000, pin)
        }
    }

    private fun connect(host: String, port: Int, pin: String) {
        streamJob?.cancel()
        _state.value = ViewerConnectionState.CONNECTING
        streamJob = scope.launch {
            connectInternal(host, port, pin)
        }
    }

    private suspend fun connectInternal(host: String, port: Int, pin: String) {
        try {
            val s = Socket(host, port)
            socket = s
            val input = DataInputStream(s.getInputStream())
            val output = s.getOutputStream()
            // PIN must be exactly 6 bytes — pad with leading spaces, phone trims them
            val pinBytes = pin.padStart(6).toByteArray(Charsets.UTF_8)
            output.write(pinBytes)
            output.flush()
            _state.value = ViewerConnectionState.CONNECTED
            var frameCount = 0
            var lastFpsTime = System.currentTimeMillis()
            while (currentCoroutineContext().isActive) {
                val size = input.readInt()
                if (size <= 0 || size > 10_000_000) break
                val bytes = ByteArray(size)
                input.readFully(bytes)
                val bitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
                _currentImage.value = bitmap
                frameCount++
                val now = System.currentTimeMillis()
                if (now - lastFpsTime >= 1000) {
                    _fps.value = frameCount
                    frameCount = 0
                    lastFpsTime = now
                }
            }
        } catch (e: Exception) {
            if (currentCoroutineContext().isActive) {
                _state.value = ViewerConnectionState.ERROR
                _errorMessage.value = when {
                    e.message?.contains("Connection refused") == true ->
                        "Connection refused — make sure 'Send Screen' is running on the phone."
                    e.message?.contains("timeout") == true ->
                        "Connection timed out — check the IP address and PIN."
                    else -> "Connection failed: ${e.message}"
                }
            }
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            socket = null
            if (_state.value == ViewerConnectionState.CONNECTED)
                _state.value = ViewerConnectionState.IDLE
        }
    }

    fun disconnect() {
        streamJob?.cancel()
        try { socket?.close() } catch (_: Exception) {}
        _state.value = ViewerConnectionState.IDLE
        _currentImage.value = null
        _errorMessage.value = null
    }

    fun dispose() {
        MdnsDiscovery.stopListening()
        scope.cancel()
        disconnect()
    }
}

fun adbForward(localPort: Int = 5000, remotePort: Int = 5000): Boolean {
    return try {
        val result = ProcessBuilder("adb", "forward", "tcp:$localPort", "tcp:$remotePort")
            .redirectErrorStream(true)
            .start()
        result.waitFor() == 0
    } catch (_: Exception) { false }
}
