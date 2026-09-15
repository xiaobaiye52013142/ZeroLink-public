package com.helium4.localscreenshare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import com.helium4.localscreenshare.ui.components.AppButton
import com.helium4.localscreenshare.ui.components.AppCard
import com.helium4.localscreenshare.ui.components.AppScaffold
import com.helium4.localscreenshare.ui.components.AppTopAppBar
import com.helium4.localscreenshare.ui.theme.LocalScreenShareTheme
import com.helium4.localscreenshare.ui.theme.appColors
import com.helium4.localscreenshare.ui.theme.appTheme
import com.helium4.localscreenshare.ui.theme.shapeLarge
import com.helium4.localscreenshare.ui.theme.shapeMedium
import com.helium4.localscreenshare.ui.theme.shapeSmall
import com.journeyapps.barcodescanner.ScanContract
import com.helium4.localscreenshare.AppLogger
import com.journeyapps.barcodescanner.ScanOptions

/** Registration type, as passed to discoverServices. */
private const val SERVICE_TYPE = "_localscreenshare._tcp."
/** Trailing-dot-free form, for tolerant matching of what discovery reports back. */
private const val BARE_SERVICE_TYPE = "_localscreenshare._tcp"
/** How long a single resolveService gets before its slot is reclaimed. */
private const val RESOLVE_TIMEOUT_MS = 6_000L

class DeviceListActivity : ComponentActivity() {

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { handleScan(it) }
    }

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchScanner()
        else Toast.makeText(this, "Camera permission is needed to scan", Toast.LENGTH_SHORT).show()
    }

    private var pendingPayload: ConnectionPayload? = null

    private val nearbyPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val payload = pendingPayload
        pendingPayload = null
        if (payload != null && grants.values.all { it }) {
            launchReceiver(payload)
        } else {
            Toast.makeText(
                this, "Nearby-devices permission is needed to join over Wi-Fi Direct",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        setContent {
            LocalScreenShareTheme {
                DeviceListScreen(
                    nsdManager = nsdManager,
                    onScanQr = { ensureCameraThenScan() },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun ensureCameraThenScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) launchScanner()
        else cameraPermLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchScanner() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scan the sender's ZeroLink QR")
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        scanLauncher.launch(options)
    }

    private fun handleScan(contents: String) {
        val payload = ConnectionPayload.fromJson(contents)
        if (payload == null) {
            Toast.makeText(this, "That QR isn't a ZeroLink code", Toast.LENGTH_SHORT).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            Toast.makeText(
                this, "Direct connect needs Android 10+. Use same-Wi-Fi pairing instead.",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val needed = nearbyPermissions().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            launchReceiver(payload)
        } else {
            pendingPayload = payload
            nearbyPermLauncher.launch(needed.toTypedArray())
        }
    }

    private fun nearbyPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            listOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun launchReceiver(payload: ConnectionPayload) {
        val intent = Intent(this, ReceiverActivity::class.java).apply {
            putExtra("DEVICE_IP", payload.ip)
            putExtra("DEVICE_PIN", payload.pin)
            putExtra("DEVICE_PORT", payload.port)
            putExtra("DEVICE_SSID", payload.ssid)
            putExtra("DEVICE_PASS", payload.pass)
        }
        startActivity(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(nsdManager: NsdManager, onScanQr: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.appColors
    var devices by remember { mutableStateOf(listOf<Pair<String, String>>()) }
    var isSearching by remember { mutableStateOf(true) }
    var discoveryFailed by remember { mutableStateOf(false) }
    var restartKey by remember { mutableStateOf(0) }
    var showPinDialog by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pinInput by remember { mutableStateOf("") }

    val lastName = remember { DeviceStore.lastName(context) }
    // Device name -> remembered PIN. Kept in state so a "Change PIN" edit re-renders the card.
    var savedPins by remember { mutableStateOf(DeviceStore.allPins(context)) }
    var autoPrompted by remember { mutableStateOf(false) }

    // Starts the session for a device and remembers its PIN, so next time the card connects
    // on a single tap instead of prompting.
    fun connect(name: String, ip: String, pin: String) {
        DeviceStore.remember(context, name, ip, pin)
        savedPins = DeviceStore.allPins(context)
        context.startActivity(Intent(context, ReceiverActivity::class.java).apply {
            putExtra("DEVICE_IP", ip)
            putExtra("DEVICE_PIN", pin)
        })
    }

    LaunchedEffect(devices) {
        // Only auto-prompt a device we have no PIN for. A remembered device is reached in one
        // tap instead, rather than auto-connecting the moment it appears — connecting is a
        // deliberate act, and silently attaching to a screen share shouldn't be automatic.
        if (!autoPrompted && !showPinDialog && lastName != null) {
            devices.firstOrNull { it.first == lastName && savedPins[it.first] == null }?.let { match ->
                selectedDevice = match
                showPinDialog = true
                autoPrompted = true
            }
        }
    }

    DisposableEffect(restartKey) {
        isSearching = true
        discoveryFailed = false
        devices = listOf()
        // NsdManager callbacks arrive on a binder thread. All Compose state writes and all
        // queue bookkeeping are marshalled onto the main thread: off-thread snapshot writes can
        // be dropped, which showed up as resolved devices never appearing in the list.
        val main = Handler(Looper.getMainLooper())
        // NsdManager resolves one service at a time — a concurrent resolveService fails with
        // FAILURE_ALREADY_ACTIVE — so found services are queued and resolved sequentially.
        val pending = ArrayDeque<NsdServiceInfo>()
        var resolving = false
        var disposed = false
        // Identifies the in-flight resolve. NsdManager can drop a ResolveListener entirely
        // (seen when Wi-Fi re-associates mid-resolve), which used to latch `resolving` true
        // forever and kill the list. Every resolve now gets a timeout, and the generation
        // counter makes sure a late callback can't release a newer resolve's slot.
        var resolveGen = 0

        lateinit var pump: () -> Unit
        // Runs on the main thread. Releases the slot only if `gen` is still current.
        fun finishResolve(gen: Int) {
            if (disposed || gen != resolveGen) return
            resolving = false
            pump()
        }
        pump = {
            if (!disposed && !resolving) {
                pending.removeFirstOrNull()?.let { info ->
                    resolving = true
                    resolveGen++
                    val gen = resolveGen
                    val listener = object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            val host = resolved.host?.hostAddress
                            main.post {
                                if (gen == resolveGen && host != null &&
                                    devices.none { it.first == resolved.serviceName }
                                ) {
                                    devices = devices + (resolved.serviceName to host)
                                }
                                finishResolve(gen)
                            }
                        }

                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                            AppLogger.w("ZeroLink", "[NSD] resolve failed for ${serviceInfo?.serviceName} code=$errorCode")
                            main.post { finishResolve(gen) }
                        }
                    }
                    try {
                        @Suppress("DEPRECATION")
                        nsdManager.resolveService(info, listener)
                        // Backstop for a listener that never fires. Re-queue the service so a
                        // transient framework hiccup doesn't lose the device permanently.
                        main.postDelayed({
                            if (!disposed && gen == resolveGen && resolving) {
                                AppLogger.w("ZeroLink", "[NSD] resolve timed out for ${info.serviceName}, re-queueing")
                                pending.addLast(info)
                                finishResolve(gen)
                            }
                        }, RESOLVE_TIMEOUT_MS)
                    } catch (e: Exception) {
                        AppLogger.w("ZeroLink", "[NSD] resolveService threw ${e.message}")
                        finishResolve(gen)
                    }
                }
            }
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) { main.post { isSearching = true } }
            override fun onDiscoveryStopped(serviceType: String?) { main.post { isSearching = false } }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // The discovered type comes back inconsistently across OS versions and OEMs —
                // "_localscreenshare._tcp", "_localscreenshare._tcp." and
                // "_localscreenshare._tcp.local." are all seen. Matching the bare type as a
                // substring accepts all of them; the old check required the trailing dot and so
                // silently rejected every service on the devices that report it without one.
                if (!serviceInfo.serviceType.orEmpty().lowercase().contains(BARE_SERVICE_TYPE)) return
                main.post {
                    // Already listed, or already waiting to be resolved — don't queue twice.
                    val known = devices.any { it.first == serviceInfo.serviceName } ||
                        pending.any { it.serviceName == serviceInfo.serviceName }
                    if (!known) pending.addLast(serviceInfo)
                    pump()
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                main.post {
                    devices = devices.filterNot { it.first == serviceInfo.serviceName }
                    pending.removeAll { it.serviceName == serviceInfo.serviceName }
                }
            }
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                AppLogger.e("ZeroLink", "[NSD] discovery failed to start code=$errorCode")
                main.post { isSearching = false; discoveryFailed = true }
            }
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {}
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        onDispose {
            disposed = true
            pending.clear()
            main.removeCallbacksAndMessages(null)
            try { nsdManager.stopServiceDiscovery(listener) } catch (_: Exception) {}
        }
    }

    if (showPinDialog && selectedDevice != null) {
        PinEntryDialog(
            deviceName = selectedDevice!!.first,
            pinInput = pinInput,
            onPinChanged = { if (it.length <= 6) pinInput = it },
            onConnect = {
                if (pinInput.length == 6) {
                    val (name, ip) = selectedDevice!!
                    connect(name, ip, pinInput)
                    showPinDialog = false
                    pinInput = ""
                } else {
                    Toast.makeText(context, "PIN must be 6 digits", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showPinDialog = false; pinInput = "" }
        )
    }

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = "Find Devices",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onScanQr) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR (direct)",
                            tint = c.primary
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(visible = isSearching) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = c.primary.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = c.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Scanning for devices on your network...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.primary
                        )
                    }
                }
            }

            Card(
                onClick = onScanQr,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = appTheme.shapeMedium,
                colors = CardDefaults.cardColors(containerColor = c.primary.copy(alpha = 0.10f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Scan QR (no router)",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Connect directly over Wi-Fi Direct, no shared network needed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = c.textMuted)
                }
            }

            if (discoveryFailed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.WifiOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = c.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Scan failed",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary
                        )
                        Text(
                            "Check that Wi-Fi is on and try again",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        AppButton(text = "Scan Again", onClick = { restartKey++ })
                    }
                }
            } else if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val infiniteTransition = rememberInfiniteTransition(label = "scan")
                        val scanAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = 0.8f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1500, easing = EaseInOut),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scanAlpha"
                        )

                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(c.primary.copy(alpha = scanAlpha * 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(c.primary.copy(alpha = scanAlpha * 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.WifiFind,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = c.primary.copy(alpha = scanAlpha)
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Looking for devices",
                            style = MaterialTheme.typography.titleMedium,
                            color = c.textPrimary
                        )
                        Text(
                            "Make sure the sender device is sharing\nand connected to the same network",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            "${devices.size} device${if (devices.size != 1) "s" else ""} found",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }
                    val ordered = devices.sortedByDescending { it.first == lastName }
                    items(ordered, key = { it.first }) { (name, ip) ->
                        val savedPin = savedPins[name]
                        DeviceCard(
                            name = name,
                            ip = ip,
                            isLastUsed = name == lastName,
                            hasSavedPin = savedPin != null,
                            onClick = {
                                if (savedPin != null) {
                                    connect(name, ip, savedPin)
                                } else {
                                    selectedDevice = name to ip
                                    showPinDialog = true
                                }
                            },
                            onChangePin = {
                                DeviceStore.forgetPin(context, name)
                                savedPins = DeviceStore.allPins(context)
                                selectedDevice = name to ip
                                pinInput = ""
                                showPinDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    name: String,
    ip: String,
    isLastUsed: Boolean = false,
    hasSavedPin: Boolean = false,
    onClick: () -> Unit,
    onChangePin: () -> Unit = {}
) {
    val c = MaterialTheme.appColors
    AppCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(appTheme.shapeMedium)
                    .background(c.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                    if (isLastUsed) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = appTheme.shapeSmall,
                            color = c.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "Last used",
                                style = MaterialTheme.typography.labelSmall,
                                color = c.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    ip,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (hasSavedPin) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Tap to connect",
                            style = MaterialTheme.typography.labelMedium,
                            color = c.connected,
                            fontWeight = FontWeight.SemiBold
                        )
                        // The remembered PIN is a hint, not a guarantee — the sender may have
                        // turned its fixed PIN off or changed it. Always leave a way to re-enter.
                        TextButton(
                            onClick = onChangePin,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text(
                                "Change PIN",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.primary
                            )
                        }
                    }
                }
            }
            Icon(
                if (hasSavedPin) Icons.Default.FlashOn else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = if (hasSavedPin) c.connected else c.textMuted
            )
        }
    }
}

@Composable
private fun PinEntryDialog(
    deviceName: String,
    pinInput: String,
    onPinChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = MaterialTheme.appColors
    // Stays a raw AlertDialog: AppDialog takes plain strings, and this body needs a PIN field with
    // its own keyboard actions and letter spacing.
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        shape = appTheme.shapeLarge,
        title = {
            Text(
                "Connect to $deviceName",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary
            )
        },
        text = {
            Column {
                Text(
                    "Enter the 6-digit PIN shown on the sender's screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = onPinChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Pairing PIN") },
                    placeholder = { Text("000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    keyboardActions = KeyboardActions(onDone = { onConnect() }),
                    singleLine = true,
                    shape = appTheme.shapeMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.primary,
                        unfocusedBorderColor = c.textMuted,
                        cursorColor = c.primary,
                        focusedLabelColor = c.primary
                    ),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.Center,
                        // takeOrElse guards against a theme whose typography leaves letterSpacing
                        // Unspecified — TextUnit arithmetic on Unspecified throws at composition.
                        letterSpacing = MaterialTheme.typography.titleMedium.letterSpacing
                            .takeOrElse { 0.15.sp } * 3
                    )
                )
            }
        },
        confirmButton = {
            AppButton(
                text = "Connect",
                onClick = onConnect,
                enabled = pinInput.length == 6
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        }
    )
}
