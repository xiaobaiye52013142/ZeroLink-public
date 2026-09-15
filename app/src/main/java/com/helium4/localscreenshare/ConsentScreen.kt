package com.helium4.localscreenshare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.components.AppBottomSheet
import com.helium4.localscreenshare.ui.components.AppButton
import com.helium4.localscreenshare.ui.components.AppCard
import com.helium4.localscreenshare.ui.components.AppScaffold
import com.helium4.localscreenshare.ui.theme.appColors

@Composable
fun ConsentScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    val c = MaterialTheme.appColors
    val context = LocalContext.current
    AppScaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(c.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = c.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Your privacy, in plain terms",
                style = MaterialTheme.typography.headlineSmall,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Please review how ZeroLink handles your data before you start.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(28.dp))

            ConsentSection(
                icon = Icons.AutoMirrored.Filled.ScreenShare,
                title = "Your screen stays between your devices",
                body = "Screen frames and remote taps are sent directly to the paired device over " +
                    "your local Wi-Fi or a phone-to-phone Wi-Fi Direct link. They are never uploaded, " +
                    "stored on a server, or routed through the internet."
            )
            ConsentSection(
                icon = Icons.Default.BugReport,
                title = "Crash reports — that's all we catch",
                body = "Firebase Crashlytics is here for one job only: if the app crashes, it quietly " +
                    "sends us a stack trace so we can fix it. That's it. No screen content, no taps, " +
                    "no keystrokes, no personal information — just \"the app broke here\" so we can " +
                    "stop it from happening again."
            )
            ConsentSection(
                icon = Icons.Default.Insights,
                title = "Anonymous usage trends (Firebase Analytics)",
                body = "We collect only aggregate, anonymous metrics — things like which screens are " +
                    "opened and how often. Nothing that identifies you or touches your screen content."
            )
            ConsentSection(
                icon = Icons.Default.TouchApp,
                title = "Remote control is opt-in and local",
                body = "If you enable remote control, the viewer can tap and type on the shared device " +
                    "during the session. This never reads or records your screen, and only works while " +
                    "you are actively sharing."
            )
            ConsentSection(
                icon = Icons.Default.PhotoCamera,
                title = "Camera is only for QR pairing",
                body = "The camera is used solely to scan a pairing QR code. Camera images are processed " +
                    "on your device and are never saved or transmitted."
            )
            ConsentSection(
                icon = Icons.Default.Lock,
                title = "No personal data collected — ever",
                body = "ZeroLink has no account system. We do not collect your name, email, contacts, " +
                    "location, photos, or any media. Your screen frames never leave your local network. " +
                    "We cannot see what you share."
            )

            Spacer(Modifier.height(8.dp))
            Text(
                "Questions or concerns? Open an issue on GitHub.",
                style = MaterialTheme.typography.labelMedium,
                color = c.textMuted,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/erTesla/ZeroLink-public/blob/main/PRIVACY.md")))
            }) {
                Text("View Privacy Policy", color = c.primary, style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(16.dp))

            AppButton(
                text = "Agree & Continue",
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(54.dp)
            )

            // Stays a TextButton: declining is deliberately the low-emphasis path, and every
            // AppButton variant renders as a filled or raised surface.
            TextButton(onClick = onDecline, modifier = Modifier.padding(top = 4.dp)) {
                Text("Decline and exit", color = c.textSecondary)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// Bump this integer any time the privacy policy materially changes.
// Existing users whose stored "privacy_version" is lower will see the sheet automatically.
const val PRIVACY_POLICY_VERSION = 2

@Composable
fun PrivacyChangelogSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.appColors
    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                "Privacy policy updated",
                style = MaterialTheme.typography.titleLarge,
                color = c.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Here's what changed",
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 20.dp)
            )
            ChangelogItem(
                title = "Crash reports — clarified what we collect",
                body = "Firebase Crashlytics is used only to catch app crashes. It receives a stack " +
                    "trace when the app breaks — nothing else. No screen content, no user data."
            )
            ChangelogItem(
                title = "No personal data — made explicit",
                body = "We added clearer language confirming that your screen frames never leave your " +
                    "local network and we cannot see what you share."
            )
            Spacer(Modifier.height(24.dp))
            AppButton(
                text = "Got it",
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(52.dp)
            )
        }
    }
}

@Composable
private fun ChangelogItem(title: String, body: String) {
    val c = MaterialTheme.appColors
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.textPrimary, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ConsentSection(icon: ImageVector, title: String, body: String) {
    val c = MaterialTheme.appColors
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
