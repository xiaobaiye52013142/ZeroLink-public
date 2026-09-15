package com.helium4.localscreenshare

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import com.helium4.localscreenshare.AppLogger
import android.content.pm.ActivityInfo
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.github.chrisbanes.photoview.PhotoView
import com.helium4.localscreenshare.ui.components.AppButton
import com.helium4.localscreenshare.ui.components.AppCircularProgress
import com.helium4.localscreenshare.ui.components.AppSwitch
import com.helium4.localscreenshare.ui.theme.LocalAppTheme
import com.helium4.localscreenshare.ui.theme.LocalScreenShareTheme
import com.helium4.localscreenshare.ui.theme.appColors
import com.helium4.localscreenshare.ui.theme.appTheme
import com.helium4.localscreenshare.ui.theme.shapeMedium
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataOutputStream
import kotlin.math.hypot
import kotlin.math.min

private const val LOGTAG = "ZeroLink"
private const val PREFS = "zerolink_control"

/**
 * Serializes control commands onto the socket's output stream from the UI thread.
 * Writes run on IO under a mutex so concurrent gestures never interleave bytes.
 */
class ControlSender(private val out: DataOutputStream, private val scope: CoroutineScope) {
    private val mutex = Mutex()

    fun tap(nx: Float, ny: Float, longPress: Boolean) =
        send { ControlProtocol.writeTap(out, nx, ny, longPress) }

    fun swipe(nx1: Float, ny1: Float, nx2: Float, ny2: Float, durationMs: Int) =
        send { ControlProtocol.writeSwipe(out, nx1, ny1, nx2, ny2, durationMs) }

    fun global(action: Byte) = send { ControlProtocol.writeGlobal(out, action) }

    fun text(value: String) = send { ControlProtocol.writeText(out, value) }

    private fun send(block: () -> Unit) {
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                try {
                    block()
                } catch (e: Exception) {
                    AppLogger.e(LOGTAG, "[Control] send failed ${e.message}")
                }
            }
        }
    }
}

/**
 * Maps touches on the displayed (FIT_CENTER, non-zoomable) frame to normalized
 * sender coordinates and forwards them as tap/long-press/swipe commands.
 * Held stable across recompositions so a gesture isn't lost when a frame updates.
 */
private class TouchController : View.OnTouchListener {
    var sender: ControlSender? = null
    var enabled: Boolean = false

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var slop = 0
    private var longPressTimeout = 0L

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val s = sender
        if (!enabled || s == null) return false // let PhotoView pan/zoom

        if (slop == 0) {
            slop = ViewConfiguration.get(view.context).scaledTouchSlop
            longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; downTime = System.currentTimeMillis()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pv = view as? PhotoView ?: return true
                val d = pv.drawable ?: return true
                val bw = d.intrinsicWidth.toFloat()
                val bh = d.intrinsicHeight.toFloat()
                val vw = pv.width.toFloat()
                val vh = pv.height.toFloat()
                if (bw <= 0f || bh <= 0f || vw <= 0f || vh <= 0f) return true

                val scale = min(vw / bw, vh / bh)
                val dw = bw * scale
                val dh = bh * scale
                val left = (vw - dw) / 2f
                val top = (vh - dh) / 2f

                val sNx = (downX - left) / dw
                val sNy = (downY - top) / dh
                if (sNx !in 0f..1f || sNy !in 0f..1f) return true // started on letterbox

                val eNx = ((event.x - left) / dw).coerceIn(0f, 1f)
                val eNy = ((event.y - top) / dh).coerceIn(0f, 1f)
                val dt = System.currentTimeMillis() - downTime
                val dist = hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())

                if (dist <= slop) {
                    s.tap(sNx.coerceIn(0f, 1f), sNy.coerceIn(0f, 1f), longPress = dt >= longPressTimeout)
                } else {
                    s.swipe(sNx.coerceIn(0f, 1f), sNy.coerceIn(0f, 1f), eNx, eNy, dt.toInt())
                }
                return true
            }
            else -> return true
        }
    }
}

class ReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the viewer's screen awake while the activity is visible. If it locks anyway,
        // ReceiverService keeps the connection alive in the background.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // PC screens are always widescreen — lock to landscape so the stream fills the display.
        if (intent.getBooleanExtra("IS_PC_SOURCE", false)) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        val ip = intent.getStringExtra("DEVICE_IP") ?: run { finish(); return }
        val pin = intent.getStringExtra("DEVICE_PIN") ?: run { finish(); return }

        // Decide whether to (re)start the session service:
        //  - Fresh launch (savedInstanceState == null): always start it for THIS target. The
        //    service ignores a duplicate for the same target but restarts for a new device, so
        //    reconnecting to a different device works even during the previous teardown.
        //  - Recreation (config change / process death): only start if no session is live and we
        //    aren't currently showing an error — so rotating the "Connection Lost" screen doesn't
        //    silently kick off a fresh connection attempt.
        val freshLaunch = savedInstanceState == null
        val showingError = ReceiverHolder.connectionState.value == ConnectionState.ERROR
        if (freshLaunch || (!ReceiverService.isRunning && !showingError)) {
            val svc = Intent(this, ReceiverService::class.java).apply {
                putExtra(ReceiverService.EXTRA_IP, ip)
                putExtra(ReceiverService.EXTRA_PIN, pin)
                putExtra(ReceiverService.EXTRA_PORT, intent.getIntExtra("DEVICE_PORT", 5000))
                // Present only for QR / Wi-Fi Direct connections; null for same-Wi-Fi (NSD).
                putExtra(ReceiverService.EXTRA_SSID, intent.getStringExtra("DEVICE_SSID"))
                putExtra(ReceiverService.EXTRA_PASS, intent.getStringExtra("DEVICE_PASS"))
            }
            ContextCompat.startForegroundService(this, svc)
        }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        val touchEnabled = mutableStateOf(prefs.getBoolean("touch", true))
        // System buttons (back/home/recents) shown by default.
        val sysEnabled = mutableStateOf(prefs.getBoolean("sysButtons", true))
        val kbEnabled = mutableStateOf(prefs.getBoolean("keyboard", false))

        setContent {
            LocalScreenShareTheme {
                // Expanded width == tablet, or a phone in landscape. There the control panel gets
                // its own column instead of floating over the stream, which would waste the width.
                @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
                val isWide = calculateWindowSizeClass(this)
                    .widthSizeClass == WindowWidthSizeClass.Expanded
                ReceiverScreen(
                    isWideScreen = isWide,
                    streamAspect = ReceiverHolder.streamAspect.value,
                    deviceIp = ip,
                    connectionState = ReceiverHolder.connectionState.value,
                    errorMessage = ReceiverHolder.errorMessage.value,
                    frameCount = ReceiverHolder.frameCount.intValue,
                    currentBitmap = ReceiverHolder.currentBitmap.value,
                    controlSender = ReceiverHolder.controlSender.value,
                    touchEnabled = touchEnabled.value,
                    sysButtonsEnabled = sysEnabled.value,
                    keyboardEnabled = kbEnabled.value,
                    onToggleTouch = {
                        touchEnabled.value = it; prefs.edit().putBoolean("touch", it).apply()
                    },
                    onToggleSys = {
                        sysEnabled.value = it; prefs.edit().putBoolean("sysButtons", it).apply()
                    },
                    onToggleKeyboard = {
                        kbEnabled.value = it; prefs.edit().putBoolean("keyboard", it).apply()
                    },
                    onRetry = {
                        ReceiverHolder.reset()
                        ContextCompat.startForegroundService(this, Intent(this, ReceiverService::class.java).apply {
                            putExtra(ReceiverService.EXTRA_IP, ip)
                            putExtra(ReceiverService.EXTRA_PIN, pin)
                            putExtra(ReceiverService.EXTRA_PORT, intent.getIntExtra("DEVICE_PORT", 5000))
                            putExtra(ReceiverService.EXTRA_SSID, intent.getStringExtra("DEVICE_SSID"))
                            putExtra(ReceiverService.EXTRA_PASS, intent.getStringExtra("DEVICE_PASS"))
                        })
                    },
                    onBack = { stopSessionAndFinish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Only render (and let the service recycle frames) while we're actually visible.
        ReceiverHolder.rendererVisible = true
    }

    override fun onStop() {
        super.onStop()
        ReceiverHolder.rendererVisible = false
    }

    private fun stopSessionAndFinish() {
        stopService(Intent(this, ReceiverService::class.java))
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiverScreen(
    isWideScreen: Boolean,
    streamAspect: Float?,
    deviceIp: String,
    connectionState: ConnectionState,
    errorMessage: String,
    frameCount: Int,
    currentBitmap: Bitmap?,
    controlSender: ControlSender?,
    touchEnabled: Boolean,
    sysButtonsEnabled: Boolean,
    keyboardEnabled: Boolean,
    onToggleTouch: (Boolean) -> Unit,
    onToggleSys: (Boolean) -> Unit,
    onToggleKeyboard: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val c = MaterialTheme.appColors
    val t = LocalAppTheme.current
    val isConnecting = connectionState == ConnectionState.CONNECTING
    val isConnected = connectionState == ConnectionState.CONNECTED
    val isError = connectionState == ConnectionState.ERROR
    val hasFrames = currentBitmap != null
    // Keep the spinner up until the first frame arrives so the user never faces a bare black screen.
    val showWaiting = isConnecting || (isConnected && !hasFrames)

    var showPanel by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }
    val touchController = remember { TouchController() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val rcHintShown = remember {
        context.getSharedPreferences("zerolink_control", android.content.Context.MODE_PRIVATE)
            .getBoolean("rc_hint_shown", false)
    }
    var showRcHint by remember { mutableStateOf(!rcHintShown) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
    {
        // Frame display (full screen behind everything)
        if (hasFrames) {
            // FIT_CENTER already letterboxes correctly in every phone/tablet combination. On a wide
            // screen we additionally pin the view to the sender's own aspect ratio once it's known,
            // so the stream sits centred at true proportions instead of spanning the full width
            // with large dead margins the touch mapping still has to account for.
            val streamModifier =
                if (isWideScreen && streamAspect != null && streamAspect > 0f)
                    Modifier.fillMaxHeight().aspectRatio(streamAspect).align(Alignment.Center)
                else Modifier.fillMaxSize()
            AndroidView(
                factory = { context ->
                    PhotoView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                        setOnTouchListener(touchController)
                    }
                },
                update = { photoView ->
                    currentBitmap?.let { photoView.setImageBitmap(it) }
                    touchController.sender = controlSender
                    touchController.enabled = touchEnabled
                    // Local pinch-zoom only makes sense when not remote-controlling.
                    photoView.setZoomable(!touchEnabled)
                },
                modifier = streamModifier
            )
        }

        // Connecting / waiting-for-first-frame overlay
        AnimatedVisibility(
            visible = showWaiting,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ConnectingOverlay(deviceIp = deviceIp, isConnected = isConnected)
        }

        // Error overlay
        AnimatedVisibility(
            visible = isError,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ErrorOverlay(message = errorMessage, onRetry = onRetry, onBack = onBack)
        }

        // Top bar + control settings panel
        Column(modifier = Modifier.statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = c.textPrimary
                    )
                }

                if (isConnected && hasFrames) {
                    Surface(
                        shape = t.shapeMedium,
                        color = t.videoOverlayScrim
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(c.connected)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Live",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.connected
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "$frameCount frames",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.textSecondary
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showRcHint) {
                            Text(
                                "Remote control",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.primary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                        IconButton(onClick = {
                            showPanel = !showPanel
                            if (showRcHint) {
                                showRcHint = false
                                context.getSharedPreferences("zerolink_control", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("rc_hint_shown", true).apply()
                            }
                        }) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "Remote control settings",
                                tint = if (showPanel) c.primary else c.textPrimary
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showPanel && isConnected) {
                ControlPanel(
                    touchEnabled = touchEnabled,
                    sysButtonsEnabled = sysButtonsEnabled,
                    keyboardEnabled = keyboardEnabled,
                    onToggleTouch = onToggleTouch,
                    onToggleSys = onToggleSys,
                    onToggleKeyboard = onToggleKeyboard
                )
            }
        }

        // Bottom control bar (system buttons + text input)
        if (isConnected && hasFrames && (sysButtonsEnabled || keyboardEnabled)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (keyboardEnabled) {
                    Surface(
                        shape = t.shapeMedium,
                        color = t.videoOverlayScrim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = textInput,
                                onValueChange = { textInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Type on remote device") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = c.primary,
                                    unfocusedBorderColor = c.textMuted,
                                    cursorColor = c.primary
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (textInput.isNotEmpty()) {
                                        controlSender?.text(textInput)
                                        textInput = ""
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send text", tint = c.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (sysButtonsEnabled) {
                    Surface(
                        shape = t.shapeMedium,
                        color = t.videoOverlayScrim
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            IconButton(onClick = { controlSender?.global(ControlProtocol.GLOBAL_BACK) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.textPrimary)
                            }
                            IconButton(onClick = { controlSender?.global(ControlProtocol.GLOBAL_HOME) }) {
                                Icon(Icons.Default.Home, contentDescription = "Home", tint = c.textPrimary)
                            }
                            IconButton(onClick = { controlSender?.global(ControlProtocol.GLOBAL_RECENTS) }) {
                                Icon(Icons.Default.Apps, contentDescription = "Recents", tint = c.textPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    touchEnabled: Boolean,
    sysButtonsEnabled: Boolean,
    keyboardEnabled: Boolean,
    onToggleTouch: (Boolean) -> Unit,
    onToggleSys: (Boolean) -> Unit,
    onToggleKeyboard: (Boolean) -> Unit
) {
    val c = MaterialTheme.appColors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .widthIn(max = 600.dp),
        shape = appTheme.shapeMedium,
        color = c.surfaceElevated.copy(alpha = 0.97f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Remote control",
                style = MaterialTheme.typography.titleMedium,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Requires the sender to enable ZeroLink in Accessibility settings.",
                style = MaterialTheme.typography.labelMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            ToggleRow("Touch & swipe", touchEnabled, onToggleTouch)
            ToggleRow("System buttons", sysButtonsEnabled, onToggleSys)
            ToggleRow("Keyboard", keyboardEnabled, onToggleKeyboard)
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.appColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textPrimary,
            modifier = Modifier.weight(1f)
        )
        AppSwitch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

@Composable
private fun ConnectingOverlay(deviceIp: String, isConnected: Boolean) {
    val c = MaterialTheme.appColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppCircularProgress(modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                if (isConnected) "Waiting for screen..." else "Connecting...",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary
            )
            Text(
                deviceIp,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val c = MaterialTheme.appColors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(c.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = c.error,
                    modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Connection Lost",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(28.dp))
            AppButton(text = "Retry", onClick = onRetry)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) {
                Text("Go Back", color = c.textSecondary)
            }
        }
    }
}
