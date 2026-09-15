package com.helium4.localscreenshare.desktop.sender

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcSenderScreen(onBack: () -> Unit) {
    val vm = remember { PcSenderViewModel() }
    val isServing by vm.isServing.collectAsState()
    val pin by vm.pin.collectAsState()
    val viewerCount by vm.viewerCount.collectAsState()
    val usbReady by vm.usbReady.collectAsState()
    var selectedFps by remember { mutableStateOf(24) }

    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share PC Screen") },
                navigationIcon = {
                    IconButton(onClick = { vm.stopServing(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (isServing) {
                Card(
                    modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("On your phone: tap Mirror PC → Connect to PC", style = MaterialTheme.typography.bodyMedium)
                        HorizontalDivider()
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("PC IP Address", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(vm.localIp, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        // USB option
                        HorizontalDivider()
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Or connect via USB cable", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (usbReady) {
                                Text(
                                    "USB ready — on your phone enter IP: 127.0.0.1",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { vm.setupUsb() },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Set Up USB (ADB Reverse)")
                                }
                            }
                        }
                        HorizontalDivider()
                        Text("PIN:", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pin.forEach { digit ->
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(56.dp, 72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            digit.toString(),
                                            fontSize = 36.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            if (viewerCount == 0) "Waiting for phone to connect…"
                            else "$viewerCount phone(s) connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewerCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { vm.stopServing() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Stop Sharing")
                        }
                    }
                }
            } else {
                Card(modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Start sharing your PC screen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "The phone connects to this PC using a PIN. Both devices must be on the same Wi-Fi network.",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("FPS:", style = MaterialTheme.typography.bodyMedium)
                            listOf(15, 24, 30).forEach { f ->
                                FilterChip(
                                    selected = selectedFps == f,
                                    onClick = { selectedFps = f },
                                    label = { Text("$f") }
                                )
                            }
                        }

                        Button(onClick = { vm.startServing(selectedFps) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Start Sharing")
                        }
                    }
                }
            }
        }
    }
}
