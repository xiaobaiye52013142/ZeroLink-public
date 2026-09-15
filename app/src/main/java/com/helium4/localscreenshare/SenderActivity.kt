package com.helium4.localscreenshare

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.helium4.localscreenshare.AppLogger
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.helium4.localscreenshare.ui.components.AppButton
import com.helium4.localscreenshare.ui.components.AppButtonVariant
import com.helium4.localscreenshare.ui.components.AppCard
import com.helium4.localscreenshare.ui.components.AppScaffold
import com.helium4.localscreenshare.ui.components.AppTopAppBar
import com.helium4.localscreenshare.ui.theme.LocalScreenShareTheme
import com.helium4.localscreenshare.ui.theme.appColors
import com.helium4.localscreenshare.ui.theme.appTheme
import com.helium4.localscreenshare.ui.theme.shapeLarge
import com.helium4.localscreenshare.ui.theme.shapeMedium
import com.helium4.localscreenshare.ui.theme.shapeSmall
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LOGTAG = "ZeroLink"

// Remembered, learned-once verdict on whether starting Wi-Fi Direct tears down this phone's Wi-Fi.
private const val SENDER_PREFS = "zerolink_sender"
private const val KEY_DIRECT_DROPS_WIFI = "direct_drops_wifi"
private const val RADIO_UNKNOWN = 0
private const val RADIO_DUAL = 1
private const val RADIO_SINGLE = 2

class SenderActivity : ComponentActivity() {

    private val REQUEST_CODE_NOTIF = 200
    private val REQUEST_CODE_AUDIO = 201
    private lateinit var projectionManager: MediaProjectionManager
    private var selectedFps = mutableIntStateOf(24)
    private var isSharing = mutableStateOf(false)
    private var pairingPin = mutableStateOf("")
    private var showFpsSelector = mutableStateOf(true)
    private var controlServiceEnabled = mutableStateOf(false)
    private var shareAudio = mutableStateOf(false)
    // Fixed-PIN opt-in: same PIN every session so the receiver can one-tap reconnect.
    private var staticPinEnabled = mutableStateOf(false)
    private var staticPinValue = mutableStateOf("")

    // Wi-Fi Direct (no-router) state.
    private val wifiDirect by lazy { WifiDirectManager(this) }
    private val radioProbe by lazy { WifiRadioProbe(this) }
    private var directEnabled = mutableStateOf(false)
    private var directStarting = mutableStateOf(false)
    private var directStatus = mutableStateOf<String?>(null)
    private var qrPayload = mutableStateOf<String?>(null)
    // True while the same-Wi-Fi PIN is the primary path. Starts true: forming a P2P group drops
    // station-mode Wi-Fi on most phones, which would silently kill the same-Wi-Fi (NSD) path.
    private var pinFirst = mutableStateOf(true)
    // True once we know (from a previous session) that Direct costs this phone its Wi-Fi.
    private var knownSingleRadio = mutableStateOf(false)
    // True once we detect Direct dropped this phone's Wi-Fi this session (shows a banner).
    private var wifiDropBanner = mutableStateOf(false)
    // Wi-Fi-internet state captured just before starting Direct, for the learn step.
    private var wifiBeforeDirect = false
    // Set once Wi-Fi Direct has actually been started, so we only tear it down (and only touch the
    // lazy manager) when it was used.
    private var directUsed = false
    // The in-flight single-radio learn coroutine, cancelled before starting a new one.
    private var learnJob: Job? = null

    private val directPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) startDirect()
        else {
            directEnabled.value = false
            directStarting.value = false
            directStatus.value = "Allow the nearby-devices permission to use Wi-Fi Direct"
        }
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            pairingPin.value = PinStore.pinForSession(this)
            ProjectionHolder.resultCode = result.resultCode
            ProjectionHolder.data = result.data
            val svc = Intent(this, ScreenShareService::class.java).apply {
                putExtra("TARGET_FPS", selectedFps.intValue)
                putExtra("PAIR_PIN", pairingPin.value)
                putExtra("SHARE_AUDIO", shareAudio.value)
            }
            ContextCompat.startForegroundService(this, svc)
            isSharing.value = true
            showFpsSelector.value = false
            // Keep the shared device awake so it keeps rendering/mirroring.
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Wi-Fi Direct is opt-in, never automatic: on a single-radio phone (the majority)
            // creating a P2P group tears down station-mode Wi-Fi, which takes the sender off the
            // router's LAN and makes the NSD/same-Wi-Fi path unreachable. Lead with the PIN and
            // let the user turn Direct on deliberately when there's no shared network.
            pinFirst.value = true
            knownSingleRadio.value = directDropsWifi() == RADIO_SINGLE
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLogger.i(LOGTAG, "[SenderActivity] onCreate()")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIF
            )
        }

        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        staticPinEnabled.value = PinStore.isStaticEnabled(this)
        staticPinValue.value = if (staticPinEnabled.value) PinStore.staticPin(this) else ""

        setContent {
            LocalScreenShareTheme {
                val sessionError = SenderHolder.sessionError.value
                // The service can stop itself (capture revoked, port taken). The share is over,
                // so drop the "sharing" state and let the screen sleep again; SenderScreen keeps
                // showing the reason until the user retries or leaves.
                LaunchedEffect(sessionError) {
                    if (sessionError != null) {
                        isSharing.value = false
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                SenderScreen(
                    sessionError = sessionError,
                    onRetry = {
                        // Clear first, so a stale error can't bleed into the new session.
                        SenderHolder.reset()
                        startProjection()
                    },
                    selectedFps = selectedFps.intValue,
                    onFpsChanged = { selectedFps.intValue = it },
                    shareAudio = shareAudio.value,
                    onShareAudioChanged = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (ContextCompat.checkSelfPermission(
                                    this@SenderActivity, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                shareAudio.value = true
                            } else {
                                ActivityCompat.requestPermissions(
                                    this@SenderActivity,
                                    arrayOf(Manifest.permission.RECORD_AUDIO),
                                    REQUEST_CODE_AUDIO
                                )
                            }
                        } else {
                            shareAudio.value = false
                        }
                    },
                    isSharing = isSharing.value,
                    showFpsSelector = showFpsSelector.value,
                    staticPinEnabled = staticPinEnabled.value,
                    staticPin = staticPinValue.value,
                    onToggleStaticPin = { enabled ->
                        PinStore.setStaticEnabled(this@SenderActivity, enabled)
                        staticPinEnabled.value = enabled
                        staticPinValue.value =
                            if (enabled) PinStore.staticPin(this@SenderActivity) else ""
                    },
                    onStaticPinChanged = { entered ->
                        staticPinValue.value = entered
                        // Persist only a complete PIN; a partial edit stays in the field.
                        if (PinStore.isValid(entered)) PinStore.setStaticPin(this@SenderActivity, entered)
                    },
                    pairingPin = pairingPin.value,
                    viewerCount = SenderHolder.viewerCount.intValue,
                    premiumRequired = SenderHolder.premiumRequired.value,
                    controlEnabled = controlServiceEnabled.value,
                    directEnabled = directEnabled.value,
                    directStarting = directStarting.value,
                    directStatus = directStatus.value,
                    qrPayload = qrPayload.value,
                    directSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q,
                    qrPrimary = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        (directEnabled.value || !pinFirst.value),
                    singleRadio = knownSingleRadio.value,
                    wifiDropBanner = wifiDropBanner.value,
                    onDismissBanner = { wifiDropBanner.value = false },
                    onEnableControl = {
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onToggleDirect = { enabled -> onToggleDirect(enabled) },
                    onStartSharing = { startProjection() },
                    onStopSharing = { stopSharing() },
                    onBack = {
                        if (isSharing.value) stopSharing() else finish()
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        controlServiceEnabled.value = isControlServiceEnabled()
    }

    private fun isControlServiceEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val si = it.resolveInfo.serviceInfo
                si.packageName == packageName &&
                    si.name == ControlAccessibilityService::class.java.name
            }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_AUDIO) {
            shareAudio.value = grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startProjection() {
        captureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun onToggleDirect(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                directStatus.value = "Wi-Fi Direct needs Android 10+; use same-Wi-Fi pairing instead"
                return
            }
            // Ask the hardware before asking the user: prompting for nearby-devices permission
            // on a tablet with no P2P radio only to fail afterwards is the worst of both.
            if (!WifiDirectManager.isSupported(this)) {
                directStatus.value =
                    "This device doesn't support Wi-Fi Direct - use same-Wi-Fi pairing instead"
                directEnabled.value = false
                directStarting.value = false
                return
            }
            directEnabled.value = true
            directStarting.value = true
            directStatus.value = null
            qrPayload.value = null
            wifiDropBanner.value = false
            val needed = directPermissions().filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isEmpty()) startDirect()
            else directPermLauncher.launch(needed.toTypedArray())
        } else {
            wifiDirect.stop()
            directEnabled.value = false
            directStarting.value = false
            directStatus.value = null
            qrPayload.value = null
            wifiDropBanner.value = false
        }
    }

    private fun directDropsWifi(): Int =
        getSharedPreferences(SENDER_PREFS, MODE_PRIVATE).getInt(KEY_DIRECT_DROPS_WIFI, RADIO_UNKNOWN)

    private fun setDirectDropsWifi(value: Int) =
        getSharedPreferences(SENDER_PREFS, MODE_PRIVATE).edit().putInt(KEY_DIRECT_DROPS_WIFI, value).apply()

    /**
     * After the Direct group forms, check whether the phone's real Wi-Fi/internet survived.
     * Remembers the verdict so future shares can skip auto-starting Direct on single-radio phones.
     */
    private fun learnRadioBehaviour() {
        if (!wifiBeforeDirect) return // wasn't on Wi-Fi → inconclusive, leave the remembered value
        // Cancel any prior learner so overlapping toggles can't race to persist conflicting verdicts.
        learnJob?.cancel()
        learnJob = lifecycleScope.launch {
            delay(4000)
            // Bail if the session ended or Direct was turned off before we could sample.
            if (!isSharing.value || !directEnabled.value) return@launch
            if (radioProbe.hasValidatedWifiInternet()) {
                setDirectDropsWifi(RADIO_DUAL); return@launch
            }
            // Wi-Fi looks gone — but a validated STA link often blips briefly right after a P2P
            // group forms. Re-check after a longer settle before condemning the device as single-radio.
            delay(4000)
            if (!isSharing.value || !directEnabled.value) return@launch
            if (radioProbe.hasValidatedWifiInternet()) {
                setDirectDropsWifi(RADIO_DUAL)
            } else {
                setDirectDropsWifi(RADIO_SINGLE)
                knownSingleRadio.value = true // Direct costs this phone its Wi-Fi — warn in the card
                wifiDropBanner.value = true
            }
        }
    }

    private fun directPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun startDirect() {
        directUsed = true
        directStarting.value = true
        directStatus.value = null
        // Snapshot the Wi-Fi uplink now so we can tell if forming the group tears it down.
        wifiBeforeDirect = radioProbe.hasValidatedWifiInternet()
        wifiDirect.start(
            onReady = { info ->
                val payload = ConnectionPayload(
                    ssid = info.ssid,
                    pass = info.passphrase,
                    ip = info.ownerIp,
                    port = 5000,
                    pin = pairingPin.value
                )
                runOnUiThread {
                    qrPayload.value = payload.toJson()
                    directEnabled.value = true
                    directStarting.value = false
                    directStatus.value = null
                    learnRadioBehaviour()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    directEnabled.value = false
                    directStarting.value = false
                    qrPayload.value = null
                    directStatus.value = msg
                }
            }
        )
    }

    private fun stopSharing() {
        stopService(Intent(this, ScreenShareService::class.java))
        if (directUsed) wifiDirect.stop()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        isSharing.value = false
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Don't tear down the Direct group on a config-change recreation (the share is still live);
        // only when Direct was actually used and this is a real teardown.
        if (directUsed && !isChangingConfigurations) wifiDirect.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderScreen(
    selectedFps: Int,
    onFpsChanged: (Int) -> Unit,
    shareAudio: Boolean = false,
    onShareAudioChanged: (Boolean) -> Unit = {},
    isSharing: Boolean,
    sessionError: String? = null,
    onRetry: () -> Unit = {},
    showFpsSelector: Boolean,
    staticPinEnabled: Boolean = false,
    staticPin: String = "",
    onToggleStaticPin: (Boolean) -> Unit = {},
    onStaticPinChanged: (String) -> Unit = {},
    pairingPin: String,
    viewerCount: Int = 0,
    premiumRequired: Boolean = false,
    controlEnabled: Boolean,
    directEnabled: Boolean,
    directStarting: Boolean,
    directStatus: String?,
    qrPayload: String?,
    directSupported: Boolean,
    qrPrimary: Boolean,
    singleRadio: Boolean,
    wifiDropBanner: Boolean,
    onDismissBanner: () -> Unit,
    onEnableControl: () -> Unit,
    onToggleDirect: (Boolean) -> Unit,
    onStartSharing: () -> Unit,
    onStopSharing: () -> Unit,
    onBack: () -> Unit
) {
    val c = MaterialTheme.appColors
    var showStopDialog by remember { mutableStateOf(false) }

    // Intercept the system back button/gesture while sharing so the user can't drop the
    // stream by accident — confirm first.
    BackHandler(enabled = isSharing) { showStopDialog = true }

    if (showStopDialog) {
        // Stays raw: AppDialog styles its confirm as a primary text button, which would drop the
        // filled-red destructive cue on an action that ends the stream.
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            containerColor = c.surfaceElevated,
            shape = appTheme.shapeLarge,
            title = { Text("Stop sharing?", color = c.textPrimary) },
            text = { Text("Going back will end screen sharing and disconnect the viewer.", color = c.textSecondary) },
            confirmButton = {
                Button(
                    onClick = { showStopDialog = false; onStopSharing() },
                    colors = ButtonDefaults.buttonColors(containerColor = c.error)
                ) { Text("Stop Sharing") }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("Cancel", color = c.textSecondary)
                }
            }
        )
    }

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = "Send Screen",
                onBack = { if (isSharing) showStopDialog = true else onBack() }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = showFpsSelector && !isSharing && sessionError == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FpsSelector(
                    selectedFps = selectedFps,
                    onFpsChanged = onFpsChanged,
                    shareAudio = shareAudio,
                    onShareAudioChanged = onShareAudioChanged,
                    staticPinEnabled = staticPinEnabled,
                    staticPin = staticPin,
                    onToggleStaticPin = onToggleStaticPin,
                    onStaticPinChanged = onStaticPinChanged,
                    onStart = onStartSharing
                )
            }

            AnimatedVisibility(
                visible = isSharing || sessionError != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                SharingStatus(
                    sessionError = sessionError,
                    onRetry = onRetry,
                    pin = pairingPin,
                    fps = selectedFps,
                    viewerCount = viewerCount,
                    premiumRequired = premiumRequired,
                    controlEnabled = controlEnabled,
                    directEnabled = directEnabled,
                    directStarting = directStarting,
                    directStatus = directStatus,
                    qrPayload = qrPayload,
                    directSupported = directSupported,
                    qrPrimary = qrPrimary,
                    singleRadio = singleRadio,
                    wifiDropBanner = wifiDropBanner,
                    onDismissBanner = onDismissBanner,
                    onEnableControl = onEnableControl,
                    onToggleDirect = onToggleDirect,
                    onStop = { showStopDialog = true }
                )
            }
        }
    }
}

@Composable
private fun FpsSelector(
    selectedFps: Int,
    onFpsChanged: (Int) -> Unit,
    shareAudio: Boolean = false,
    onShareAudioChanged: (Boolean) -> Unit = {},
    staticPinEnabled: Boolean = false,
    staticPin: String = "",
    onToggleStaticPin: (Boolean) -> Unit = {},
    onStaticPinChanged: (String) -> Unit = {},
    onStart: () -> Unit
) {
    val c = MaterialTheme.appColors
    val fpsOptions = listOf(10, 15, 24, 30, 45, 60)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = c.primary
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Choose Frame Rate",
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary
        )
        Text(
            "Higher FPS = smoother but uses more battery",
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(28.dp))

        // FPS grid
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            fpsOptions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { fps ->
                        val isSelected = fps == selectedFps
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(appTheme.shapeMedium)
                                .then(
                                    if (isSelected) Modifier.border(
                                        appTheme.borderWidth, c.primary, appTheme.shapeMedium
                                    ) else Modifier
                                )
                                .clickable { onFpsChanged(fps) },
                            shape = appTheme.shapeMedium,
                            color = if (isSelected) c.primary.copy(alpha = 0.12f) else c.surface
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "$fps",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (isSelected) c.primary else c.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "FPS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) c.primary.copy(alpha = 0.7f) else c.textMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Quality hint
        val qualityHint = when {
            selectedFps <= 15 -> "Battery saver"
            selectedFps <= 24 -> "Balanced"
            selectedFps <= 30 -> "Smooth"
            selectedFps <= 45 -> "High quality"
            else -> "Maximum smoothness"
        }
        Surface(
            shape = appTheme.shapeSmall,
            color = c.primary.copy(alpha = 0.08f)
        ) {
            Text(
                text = qualityHint,
                style = MaterialTheme.typography.labelMedium,
                color = c.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Spacer(Modifier.height(16.dp))

            Surface(
                shape = appTheme.shapeMedium,
                color = c.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(appTheme.borderWidth, appTheme.cardBorderColor, appTheme.shapeMedium)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onShareAudioChanged(!shareAudio) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (shareAudio) c.primary else c.textMuted,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Share System Sound",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Stream audio playing on this device",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.textSecondary
                        )
                    }
                    Switch(
                        checked = shareAudio,
                        onCheckedChange = onShareAudioChanged,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = appTheme.switchThumbColor,
                            checkedTrackColor = appTheme.switchTrackColor,
                            uncheckedThumbColor = c.textMuted,
                            uncheckedTrackColor = c.surface
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        StaticPinCard(
            enabled = staticPinEnabled,
            pin = staticPin,
            onToggle = onToggleStaticPin,
            onPinChanged = onStaticPinChanged
        )

        Spacer(Modifier.height(32.dp))

        AppButton(
            text = "Start Screen Sharing",
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            icon = Icons.AutoMirrored.Filled.ScreenShare
        )

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Lets the user pin the pairing code to a fixed value instead of a fresh one per session, so a
 * receiver that has connected before can reconnect in one tap. Off by default — a rotating PIN
 * is the safer choice, and this trades that away for convenience.
 */
@Composable
private fun StaticPinCard(
    enabled: Boolean,
    pin: String,
    onToggle: (Boolean) -> Unit,
    onPinChanged: (String) -> Unit
) {
    val c = MaterialTheme.appColors
    Surface(
        shape = appTheme.shapeMedium,
        color = c.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(appTheme.borderWidth, appTheme.cardBorderColor, appTheme.shapeMedium)
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(!enabled) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Pin,
                    contentDescription = null,
                    tint = if (enabled) c.primary else c.textMuted,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Use a Fixed PIN",
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (enabled) "Viewers who connected before can reconnect in one tap"
                        else "A new random PIN is used for every session",
                        style = MaterialTheme.typography.labelSmall,
                        color = c.textSecondary
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = appTheme.switchThumbColor,
                        checkedTrackColor = appTheme.switchTrackColor,
                        uncheckedThumbColor = c.textMuted,
                        uncheckedTrackColor = c.surface
                    )
                )
            }

            AnimatedVisibility(visible = enabled, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier.padding(start = 50.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    var hasFocusLeft by remember { mutableStateOf(false) }
                    val showPinError = hasFocusLeft && pin.length != PinStore.PIN_LENGTH
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { entered ->
                            // Digits only, capped at the protocol's fixed 6-byte PIN width.
                            val cleaned = entered.filter { it.isDigit() }.take(PinStore.PIN_LENGTH)
                            onPinChanged(cleaned)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) hasFocusLeft = true },
                        label = { Text("Fixed PIN") },
                        placeholder = { Text("000000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = showPinError,
                        shape = appTheme.shapeMedium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = c.primary,
                            unfocusedBorderColor = c.textMuted,
                            cursorColor = c.primary,
                            focusedLabelColor = c.primary
                        )
                    )
                    if (showPinError) {
                        Text(
                            "Enter 6 digits — the last saved PIN is still in use until you do",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.warning,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharingStatus(
    sessionError: String? = null,
    onRetry: () -> Unit = {},
    pin: String,
    fps: Int,
    viewerCount: Int = 0,
    premiumRequired: Boolean = false,
    controlEnabled: Boolean,
    directEnabled: Boolean,
    directStarting: Boolean,
    directStatus: String?,
    qrPayload: String?,
    directSupported: Boolean,
    qrPrimary: Boolean,
    singleRadio: Boolean,
    wifiDropBanner: Boolean,
    onDismissBanner: () -> Unit,
    onEnableControl: () -> Unit,
    onToggleDirect: (Boolean) -> Unit,
    onStop: () -> Unit
) {
    val c = MaterialTheme.appColors

    if (sessionError != null) {
        // The session is gone. Showing the PIN and QR here would invite the user to pair
        // with something that is no longer listening.
        SessionEndedState(message = sessionError, onRetry = onRetry)
        return
    }

    var showDisclosureDialog by remember { mutableStateOf(false) }

    if (showDisclosureDialog) {
        AccessibilityDisclosureDialog(
            onAllow = { showDisclosureDialog = false; onEnableControl() },
            onDeny = { showDisclosureDialog = false }
        )
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // Pulsing indicator — green/primary signals "live", not error
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(c.primary.copy(alpha = pulseAlpha * 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(c.primary.copy(alpha = pulseAlpha * 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(c.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                        contentDescription = null,
                        tint = c.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Screen is Being Shared",
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary
        )

        Text(
            if (viewerCount == 0) "Waiting for viewers to connect…"
            else if (viewerCount == 1) "1 viewer connected"
            else "$viewerCount viewers connected",
            style = MaterialTheme.typography.bodyMedium,
            color = if (viewerCount > 0) c.primary else c.textSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (premiumRequired) {
            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                shape = appTheme.shapeMedium,
                color = c.warning.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = c.warning,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Multi-Device is Premium",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "A second device tried to connect but was blocked. Upgrade to share to multiple screens at once.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        if (wifiDropBanner) {
            WifiDropBanner(onTurnOff = { onToggleDirect(false) }, onDismiss = onDismissBanner)
            Spacer(Modifier.height(20.dp))
        }

        // Order the two pairing methods: QR/Direct is primary on capable phones; on a phone we've
        // learned drops its Wi-Fi for Direct, the same-Wi-Fi PIN becomes primary instead. On
        // Android < 10 Direct is unsupported, so only the PIN is shown.
        if (!directSupported) {
            PinCard(pin = pin, fps = fps)
        } else if (qrPrimary) {
            DirectConnectCard(
                directEnabled = directEnabled,
                directStarting = directStarting,
                directStatus = directStatus,
                qrPayload = qrPayload,
                warning = singleRadio,
                onToggleDirect = onToggleDirect
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Or connect on the same Wi-Fi",
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted
            )
            Spacer(Modifier.height(8.dp))
            PinCard(pin = pin, fps = fps)
        } else {
            PinCard(pin = pin, fps = fps)
            Spacer(Modifier.height(20.dp))
            Text(
                if (singleRadio) "No shared Wi-Fi? Connect by QR — this turns off your Wi-Fi"
                else "No shared Wi-Fi? Connect directly by QR",
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted
            )
            Spacer(Modifier.height(8.dp))
            DirectConnectCard(
                directEnabled = directEnabled,
                directStarting = directStarting,
                directStatus = directStatus,
                qrPayload = qrPayload,
                warning = singleRadio,
                onToggleDirect = onToggleDirect
            )
        }

        Spacer(Modifier.height(20.dp))

        // Remote-control capability status
        Surface(
            shape = appTheme.shapeMedium,
            color = if (controlEnabled) c.connected.copy(alpha = 0.10f) else c.warning.copy(alpha = 0.12f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (controlEnabled) Icons.Default.TouchApp else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (controlEnabled) c.connected else c.warning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (controlEnabled) "Remote control enabled" else "Remote control off",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (controlEnabled) c.connected else c.warning,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (controlEnabled) "The viewer can tap, swipe and type on this device."
                        else "Enable ZeroLink in Accessibility to let the viewer control this device.",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary
                    )
                }
                if (!controlEnabled) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showDisclosureDialog = true }) {
                        Text("Enable", color = c.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        AppButton(
            text = "Stop Sharing",
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            variant = AppButtonVariant.DESTRUCTIVE,
            icon = Icons.Default.Stop
        )

        Spacer(Modifier.height(24.dp))
    }
}

/** Shown in place of the live-session UI once the service has stopped on its own. */
@Composable
private fun SessionEndedState(message: String, onRetry: () -> Unit) {
    val c = MaterialTheme.appColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(c.warning.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = c.warning,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "Sharing stopped",
            style = MaterialTheme.typography.titleLarge,
            color = c.textPrimary
        )

        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(Modifier.height(32.dp))

        AppButton(
            text = "Start Sharing Again",
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            icon = Icons.Default.Refresh
        )

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PinCard(pin: String, fps: Int) {
    val c = MaterialTheme.appColors
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Pairing PIN", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                pin.forEach { digit ->
                    Surface(shape = appTheme.shapeSmall, color = c.primary.copy(alpha = 0.12f)) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontSize = appTheme.headingFontSize),
                            color = c.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = c.textMuted.copy(alpha = 0.3f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Frame Rate", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text("$fps FPS", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Status", style = MaterialTheme.typography.labelMedium, color = c.textSecondary)
                    Text("Live", style = MaterialTheme.typography.titleMedium, color = c.connected)
                }
            }
        }
    }
}

@Composable
private fun WifiDropBanner(onTurnOff: () -> Unit, onDismiss: () -> Unit) {
    val c = MaterialTheme.appColors
    Surface(
        shape = appTheme.shapeMedium,
        color = c.warning.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = c.warning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "This phone left Wi-Fi for Direct",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.warning,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Internet resumes when you stop sharing. If the other phone is on the same " +
                            "Wi-Fi, turn Direct off and use the PIN below instead.",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.textSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = c.textSecondary)
                }
            }
            TextButton(onClick = onTurnOff, modifier = Modifier.align(Alignment.End)) {
                Text("Turn Direct off", color = c.primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DirectConnectCard(
    directEnabled: Boolean,
    directStarting: Boolean,
    directStatus: String?,
    qrPayload: String?,
    warning: Boolean,
    onToggleDirect: (Boolean) -> Unit
) {
    val c = MaterialTheme.appColors
    Surface(
        shape = appTheme.shapeMedium,
        color = if (warning) c.surface else c.primary.copy(alpha = 0.08f),
        border = if (warning) null else androidx.compose.foundation.BorderStroke(1.dp, c.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Direct connect (no router)",
                        style = MaterialTheme.typography.labelLarge,
                        color = c.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (warning)
                            "Best when there's no shared Wi-Fi. On this phone, turning it on " +
                                "disconnects Wi-Fi/internet until you stop."
                        else
                            "Turns this phone into a Wi-Fi Direct hotspot so the other phone can join " +
                                "by QR with no shared network. On many phones this drops your Wi-Fi " +
                                "until you stop sharing.",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (warning) c.warning else c.textSecondary
                    )
                }
                Switch(
                    checked = directEnabled,
                    enabled = !directStarting,
                    onCheckedChange = onToggleDirect,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = c.onPrimary,
                        checkedTrackColor = c.primary
                    )
                )
            }

            when {
                // Setting up the group.
                directStarting -> {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = c.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Setting up Wi-Fi Direct…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary
                        )
                    }
                }

                // Failed to start — show why and let the user retry.
                directStatus != null -> {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        directStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.warning
                    )
                    Spacer(Modifier.height(8.dp))
                    AppButton(
                        text = "Try again",
                        onClick = { onToggleDirect(true) },
                        icon = Icons.Default.Refresh
                    )
                }

                // Group is up and the QR is ready.
                directEnabled && qrPayload != null -> {
                    Spacer(Modifier.height(16.dp))
                    val qrBitmap = remember(qrPayload) { QrCodeUtil.encode(qrPayload, 512)?.asImageBitmap() }
                    if (qrBitmap != null) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            // Cap the QR so it never overflows a narrow screen.
                            val qrSize = 232.dp.coerceAtMost(maxWidth)
                            // Deliberately NOT themed: a QR code needs a white quiet zone and high
                            // contrast to stay scannable — tinting it would break pairing on dark themes.
                            Surface(shape = appTheme.shapeMedium, color = Color.White) {
                                Image(
                                    bitmap = qrBitmap,
                                    contentDescription = "Pairing QR code",
                                    modifier = Modifier
                                        .size(qrSize)
                                        .padding(12.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "On the other phone:",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        DirectStep("1", "Open ZeroLink → Receive Screen → Find Devices")
                        DirectStep("2", "Tap “Scan QR (no router)” and scan the code above")
                        DirectStep("3", "Approve the Wi-Fi Direct prompt when it appears")
                    } else {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Couldn't render the QR code — use the same-Wi-Fi PIN below instead.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.warning
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectStep(number: String, text: String) {
    val c = MaterialTheme.appColors
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(c.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = c.primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
    }
}

@Composable
private fun AccessibilityDisclosureDialog(
    onAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val c = MaterialTheme.appColors
    // Stays raw: this consent dialog needs an icon, non-dismissible properties and a multi-paragraph
    // body, none of which AppDialog's single-string API can express.
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        containerColor = c.surfaceElevated,
        shape = appTheme.shapeLarge,
        icon = {
            Icon(
                imageVector = Icons.Default.Accessibility,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                "Allow Remote Control?",
                color = c.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "ZeroLink uses Android's Accessibility API to let a connected viewer device simulate taps, swipes, and gestures on this device during an active screen sharing session.",
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "This permission is required because Android does not allow apps to inject touch input without it.",
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "ZeroLink does not use this permission to read, collect, or store your screen content, keystrokes, or any personal data. The feature is only active during a user-initiated sharing session over the local network.",
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                // Android 13+ greys accessibility out for anything not installed through a
                // store session, behind a menu item most users never find on their own.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Text(
                        "If the switch is greyed out as a restricted setting, open Settings > Apps > ZeroLink, tap the three-dot menu, and choose \"Allow restricted settings\" first.",
                        color = c.textMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            AppButton(text = "Allow", onClick = onAllow)
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text("Don't Allow", color = c.textSecondary)
            }
        }
    )
}
