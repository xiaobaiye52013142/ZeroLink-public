package com.helium4.localscreenshare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.components.AppButton
import com.helium4.localscreenshare.ui.components.AppCard
import com.helium4.localscreenshare.ui.components.AppScaffold
import com.helium4.localscreenshare.ui.components.AppTopAppBar
import com.helium4.localscreenshare.ui.theme.LocalScreenShareTheme
import com.helium4.localscreenshare.ui.theme.appColors
import com.helium4.localscreenshare.ui.theme.appTheme
import com.helium4.localscreenshare.ui.theme.shapeMedium

class GuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LocalScreenShareTheme { GuideScreen(onBack = { finish() }) } }
    }
}

private data class GuideStep(val text: String)

@Composable
private fun GuideScreen(onBack: () -> Unit) {
    val c = MaterialTheme.appColors
    AppScaffold(
        topBar = { AppTopAppBar(title = "How it works", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Share a screen across your local Wi-Fi, then optionally control the other device remotely.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .padding(bottom = 16.dp)
            )

            GuideCard(
                icon = Icons.AutoMirrored.Filled.ScreenShare,
                title = "1 · Share your screen",
                steps = listOf(
                    GuideStep("On the home screen tap \"Start Sharing\" and allow screen capture."),
                    GuideStep("A 6-digit PIN appears — give it to the person who will view your screen."),
                    GuideStep("Keep this screen open; your device stays awake while sharing.")
                )
            )

            GuideCard(
                icon = Icons.Default.Visibility,
                title = "2 · View another screen",
                steps = listOf(
                    GuideStep("Tap \"Find Devices\", choose the sender, and enter its PIN."),
                    GuideStep("While remote control is off, pinch to zoom and pan the live view.")
                )
            )

            GuideCard(
                icon = Icons.Default.Accessibility,
                title = "3 · Allow remote control (on the device being controlled)",
                steps = listOf(
                    GuideStep("On the sender's sharing screen, find the \"Remote control off\" card and tap Enable."),
                    GuideStep("Turn on ZeroLink in Accessibility settings — a one-time step."),
                    GuideStep("This lets the viewer tap, swipe and type on this device. It only acts during an authorized session.")
                )
            )

            GuideCard(
                icon = Icons.Default.TouchApp,
                title = "4 · Control the remote device (on the viewer)",
                steps = listOf(
                    GuideStep("While viewing, tap the sliders icon to open the control panel."),
                    GuideStep("Turn on Touch & swipe, System buttons and Keyboard — your choices are remembered."),
                    GuideStep("Touch the live screen to tap, long-press or swipe. Use Back / Home / Recents, and type with the text bar.")
                )
            )

            GuideCard(
                icon = Icons.Default.Bolt,
                title = "Good to know",
                steps = listOf(
                    GuideStep("Both devices must be on the same Wi-Fi network or hotspot."),
                    GuideStep("Rotating either screen no longer drops the connection."),
                    GuideStep("Neither screen sleeps while a session is active.")
                )
            )

            Spacer(Modifier.height(24.dp))
            AppButton(
                text = "Got it",
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideCard(icon: ImageVector, title: String, steps: List<GuideStep>) {
    val c = MaterialTheme.appColors
    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .padding(bottom = 16.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(appTheme.shapeMedium)
                        .background(c.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(12.dp))

            steps.forEach { step ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(c.primary)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = step.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                }
            }
        }
    }
}
