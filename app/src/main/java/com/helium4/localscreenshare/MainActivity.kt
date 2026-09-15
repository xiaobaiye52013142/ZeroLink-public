package com.helium4.localscreenshare

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import com.helium4.localscreenshare.AppLogger
import java.net.Inet4Address
import java.net.NetworkInterface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.firebase.FirebaseApp
import com.helium4.localscreenshare.ui.components.AppBottomSheet
import com.helium4.localscreenshare.ui.components.AppButton
import com.helium4.localscreenshare.ui.components.AppScaffold
import com.helium4.localscreenshare.ui.components.AppTextField
import com.helium4.localscreenshare.ui.theme.LocalAppTheme
import com.helium4.localscreenshare.ui.theme.LocalScreenShareTheme
import com.helium4.localscreenshare.ui.theme.ThemeManager
import com.helium4.localscreenshare.ui.theme.appColors
import com.helium4.localscreenshare.ui.theme.isBrutalist
import com.helium4.localscreenshare.ui.theme.isNeumorphic
import com.helium4.localscreenshare.ui.theme.isTranslucentSurface
import com.helium4.localscreenshare.ui.theme.neumorphicInset
import com.helium4.localscreenshare.ui.theme.shapeLarge
import com.helium4.localscreenshare.ui.theme.shapeMedium
import com.helium4.localscreenshare.ui.theme.shapeSmall
import com.helium4.localscreenshare.ui.theme.themedSurface

private const val LOGTAG = "ZeroLink"
private const val KEY_CHANGELOG_SEEN = "changelog_seen_version"
private const val KEY_SOFT_UPDATE_DISMISSED = "soft_update_dismissed_version"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)
        AppLogger.init(this)
        RemoteConfigManager.init()
        // Must run before setContent so the first composition already has the cached theme.
        ThemeManager.init(this)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        setContent {
            LocalScreenShareTheme {
                var accepted by remember { mutableStateOf(prefs.getBoolean(KEY_CONSENT, false)) }
                if (accepted) {
                    MainScreen(prefs = prefs)
                } else {
                    ConsentScreen(
                        onAccept = {
                            prefs.edit().putBoolean(KEY_CONSENT, true).apply()
                            accepted = true
                        },
                        onDecline = { finish() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check on every foreground so a theme published while the app was backgrounded
        // takes effect without needing a cold start.
        ThemeManager.refresh()
    }

    companion object {
        private const val PREFS = "zerolink"
        private const val KEY_CONSENT = "consent_v1"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    val c = MaterialTheme.appColors
    val t = LocalAppTheme.current
    var showPcDialog by remember { mutableStateOf(false) }
    var pcIp by remember { mutableStateOf("") }
    var debugTapCount by remember { mutableStateOf(0) }
    val showDebug = debugTapCount >= 5
    var pin by remember { mutableStateOf("") }  // typed by user from PC app display

    val currentVersion = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
    }
    var forceUpdateMessage by remember { mutableStateOf<String?>(null) }
    var softUpdateMessage by remember { mutableStateOf<String?>(null) }
    var softUpdateDismissed by remember {
        mutableStateOf(prefs.getInt(KEY_SOFT_UPDATE_DISMISSED, 0) >= currentVersion)
    }
    var showChangelog by remember { mutableStateOf(false) }
    var showPrivacyChangelog by remember {
        mutableStateOf(prefs.getInt("privacy_version", 0) < PRIVACY_POLICY_VERSION)
    }

    LaunchedEffect(Unit) {
        val forceV = RemoteConfigManager.forceUpdateVersion()
        val softV = RemoteConfigManager.softUpdateVersion()
        val msg = RemoteConfigManager.updateMessage()
        when {
            forceV > currentVersion ->
                forceUpdateMessage = msg.ifBlank { "A required update is available. Please update to continue." }
            softV > currentVersion ->
                softUpdateMessage = msg.ifBlank { "A new version is available." }
        }
        val changelog = RemoteConfigManager.changelog()
        if (changelog.isNotBlank() && softV > prefs.getInt(KEY_CHANGELOG_SEEN, 0)) {
            showChangelog = true
        }
    }

    if (forceUpdateMessage != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Update Required") },
            text = { Text(forceUpdateMessage!!) },
            confirmButton = {
                Button(onClick = {
                    val url = RemoteConfigManager.updateUrl()
                    if (url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }) { Text("Update Now") }
            },
            dismissButton = null
        )
    }

    if (showPrivacyChangelog) {
        PrivacyChangelogSheet(onDismiss = {
            prefs.edit().putInt("privacy_version", PRIVACY_POLICY_VERSION).apply()
            showPrivacyChangelog = false
        })
    }

    if (showChangelog) {
        AppBottomSheet(onDismiss = {
            prefs.edit().putInt(KEY_CHANGELOG_SEEN, RemoteConfigManager.softUpdateVersion()).apply()
            showChangelog = false
        }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "What's New",
                    style = MaterialTheme.typography.titleLarge,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    RemoteConfigManager.changelog(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(20.dp))
                AppButton(
                    text = "Got it",
                    onClick = {
                        prefs.edit().putInt(KEY_CHANGELOG_SEEN, RemoteConfigManager.softUpdateVersion()).apply()
                        showChangelog = false
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                )
            }
        }
    }

    if (showPcDialog) {
        PcMirrorDialog(
            pin = pin,
            pcIp = pcIp,
            onPcIpChanged = { pcIp = it },
            onDismiss = { showPcDialog = false },
            onStart = { ip, pinValue ->
                val intent = Intent(context, ReceiverActivity::class.java).apply {
                    putExtra("DEVICE_IP", ip)
                    putExtra("DEVICE_PIN", pinValue)
                    putExtra("IS_PC_SOURCE", true)
                }
                context.startActivity(intent)
                showPcDialog = false
            }
        )
    }

    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (softUpdateMessage != null && !softUpdateDismissed) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    shape = t.shapeMedium,
                    color = c.primary.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = c.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            softUpdateMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        val updateUrl = RemoteConfigManager.updateUrl()
                        if (updateUrl.isNotBlank()) {
                            TextButton(
                                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Update", style = MaterialTheme.typography.labelSmall, color = c.primary)
                            }
                        }
                        IconButton(
                            onClick = {
                                softUpdateDismissed = true
                                prefs.edit().putInt(KEY_SOFT_UPDATE_DISMISSED, currentVersion).apply()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = c.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
            }

            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .size(88.dp)
                    .padding(4.dp)
                    .themedSurface(
                        theme = t,
                        cornerRadius = 40.dp,
                        fill = if (t.isTranslucentSurface) t.cardBackgroundColor else t.colorPrimary,
                        borderColor = t.cardBorderColor,
                        elevation = t.cardElevation,
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (t.isTranslucentSurface) t.colorPrimary else t.colorOnPrimary
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "ZeroLink",
                style = MaterialTheme.typography.headlineMedium,
                color = c.textPrimary,
                modifier = Modifier.clickable { debugTapCount++ }
            )

            Text(
                text = "Share your screen over local Wi-Fi",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )

            val currentIp = remember { getLocalIpAddress(context) }
            val isConnected = currentIp != "0.0.0.0"

            Row(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .then(
                        // Neumorphism signals state with a pressed-in well; every other theme
                        // uses a tinted chip.
                        if (t.isNeumorphic) Modifier.neumorphicInset(t, t.cornerRadiusLarge)
                        else Modifier.background(
                            (if (isConnected) c.connected else c.error).copy(alpha = 0.12f),
                            t.shapeLarge
                        )
                    )
                    .then(
                        if (!isConnected) Modifier.clickable {
                            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        } else Modifier
                    )
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isConnected) c.connected else c.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (isConnected) currentIp else "Not connected",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isConnected) c.connected else c.error
                )
            }

            Spacer(Modifier.height(40.dp))

            FeatureCard(
                icon = Icons.AutoMirrored.Filled.Send,
                title = "Send Screen",
                description = "Share this phone's screen with another device on your network",
                buttonText = "Start Sharing",
                onClick = { context.startActivity(Intent(context, SenderActivity::class.java)) }
            )

            Spacer(Modifier.height(16.dp))

            FeatureCard(
                icon = Icons.Default.Visibility,
                title = "View Screen",
                description = "View another phone's screen being shared on your network",
                buttonText = "Find Devices",
                onClick = { context.startActivity(Intent(context, DeviceListActivity::class.java)) }
            )

            Spacer(Modifier.height(16.dp))

            FeatureCard(
                icon = Icons.Default.Computer,
                title = "Mirror PC",
                description = "View your PC's screen on this phone using the ZeroLink desktop app",
                buttonText = "Connect to PC",
                onClick = {
                    pcIp = ""
                    pin = ""
                    showPcDialog = true
                }
            )

            Spacer(Modifier.height(24.dp))

            TextButton(onClick = {
                context.startActivity(Intent(context, GuideActivity::class.java))
            }) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("How it works", color = c.primary)
            }

            if (showDebug) {
                TextButton(onClick = {
                    context.startActivity(Intent(context, LogViewerActivity::class.java))
                }) {
                    Icon(
                        Icons.Default.BugReport,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("View Logs", color = c.primary)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    isBeta: Boolean = false,
    onClick: () -> Unit
) {
    val c = MaterialTheme.appColors
    val t = LocalAppTheme.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .animateContentSize()
            // Themes that draw their own offset or double shadows need room for it to spill.
            .padding(if (t.isNeumorphic || t.isBrutalist) 6.dp else 0.dp)
            .themedSurface(
                theme = t,
                cornerRadius = t.cornerRadiusLarge,
                fill = t.cardBackgroundColor,
                borderColor = t.cardBorderColor,
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .themedSurface(
                            theme = t,
                            cornerRadius = t.cornerRadiusSmall.coerceAtLeast(8.dp),
                            fill = t.colorPrimary.copy(alpha = if (t.isNeumorphic) 0f else 0.12f),
                            // Inner icon tile stays flat on every theme — the enclosing card
                            // already carries the depth, and stacking both reads as a bug.
                            elevation = 0.dp,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = t.headingFontWeight
                )
                if (isBeta) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "BETA",
                        style = MaterialTheme.typography.labelSmall,
                        color = t.chipTextColor,
                        modifier = Modifier
                            .background(t.chipBackgroundColor, RoundedCornerShape(t.cornerRadiusSmall))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )

            AppButton(
                text = buttonText,
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )
        }
    }
}

@Composable
private fun PcMirrorDialog(
    pin: String,
    pcIp: String,
    onPcIpChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onStart: (ip: String, pin: String) -> Unit
) {
    val context = LocalContext.current
    val c = MaterialTheme.appColors
    val t = LocalAppTheme.current
    var pinLocal by remember { mutableStateOf(pin) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surfaceElevated,
        shape = t.shapeLarge,
        title = {
            Text(
                "Mirror PC Screen",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary
            )
        },
        text = {
            Column {
                // Step 1
                Text(
                    "Step 1 — Open ZeroLink on your PC",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Click \"Share PC Screen\" then \"Start Sharing\". The app shows your PC's IP address and a PIN.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/erTesla/ZeroLink-public/releases"))
                        )
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = c.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Don't have the PC app? Download it", color = c.primary, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))

                // Step 2
                Text(
                    "Step 2 — Enter your PC's IP and PIN",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = pcIp,
                    onValueChange = { onPcIpChanged(it.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    label = "PC IP Address",
                    placeholder = "shown in ZeroLink on PC",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Spacer(Modifier.height(8.dp))
                AppTextField(
                    value = pinLocal,
                    onValueChange = { if (it.length <= 6) pinLocal = it.filter { c -> c.isDigit() } },
                    modifier = Modifier.fillMaxWidth(),
                    label = "PIN",
                    placeholder = "6-digit PIN shown on PC",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(8.dp))
                // USB shortcut — pre-fills 127.0.0.1 so the user doesn't need to
                // type an IP address when connected by USB cable with ADB reverse active.
                TextButton(
                    onClick = { onPcIpChanged("127.0.0.1") },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Text("Using USB cable? Click \"Set Up USB\" on PC app, then tap here", color = c.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            AppButton(
                text = "Connect",
                onClick = {
                    onStart(pcIp, pinLocal)
                },
                enabled = pcIp.isNotBlank() && pinLocal.length == 6
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = c.textSecondary)
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    val c = MaterialTheme.appColors
    val t = LocalAppTheme.current
    Surface(
        shape = t.shapeSmall,
        color = c.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = c.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun getLocalIpAddress(@Suppress("UNUSED_PARAMETER") context: Context): String {
    return try {
        NetworkInterface.getNetworkInterfaces().asSequence()
            .flatMap { it.inetAddresses.asSequence() }
            .firstOrNull { addr ->
                !addr.isLoopbackAddress && addr is Inet4Address &&
                    addr.hostAddress?.let { !it.startsWith("169.254") && !it.startsWith("0.") } == true
            }?.hostAddress ?: "0.0.0.0"
    } catch (e: Exception) {
        AppLogger.e(LOGTAG, "Failed to get IP", e)
        "0.0.0.0"
    }
}
