package com.helium4.localscreenshare.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.desktop.sender.PcSenderScreen
import com.helium4.localscreenshare.desktop.viewer.ViewerScreen

enum class AppDestination { HOME, VIEWER, SENDER }

@Composable
fun MainScreen() {
    var destination by remember { mutableStateOf(AppDestination.HOME) }

    when (destination) {
        AppDestination.HOME -> HomeScreen(
            onViewPhone = { destination = AppDestination.VIEWER },
            onSharePc = { destination = AppDestination.SENDER }
        )
        AppDestination.VIEWER -> ViewerScreen(onBack = { destination = AppDestination.HOME })
        AppDestination.SENDER -> PcSenderScreen(onBack = { destination = AppDestination.HOME })
    }
}

@Composable
private fun HomeScreen(onViewPhone: () -> Unit, onSharePc: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "ZeroLink",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Screen sharing between phone and PC",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 48.dp)
        )

        Row(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Visibility,
                title = "View Phone Screen",
                description = "See your phone's display on this PC. Auto-discovers phones over Wi-Fi or connects via USB cable.",
                buttonText = "View Phone",
                onClick = onViewPhone
            )
            FeatureCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Computer,
                title = "Share PC Screen",
                description = "Stream this PC's desktop to your phone. The phone scans a QR code or types a PIN — no IP address needed.",
                buttonText = "Share PC Screen",
                onClick = onSharePc
            )
        }
    }
}

@Composable
private fun FeatureCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}
