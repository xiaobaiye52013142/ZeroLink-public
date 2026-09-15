package com.helium4.localscreenshare.desktop.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.desktop.discovery.DiscoveredDevice

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(onBack: () -> Unit) {
    val vm = remember { ViewerViewModel() }
    val state by vm.state.collectAsState()
    val image by vm.currentImage.collectAsState()
    val devices by vm.discoveredDevices.collectAsState()
    val fps by vm.fps.collectAsState()
    val errorMessage by vm.errorMessage.collectAsState()
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingDevice by remember { mutableStateOf<DiscoveredDevice?>(null) }
    var manualIp by remember { mutableStateOf("") }
    var pinInput by remember { mutableStateOf("") }
    var usbMode by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { vm.dispose() } }

    if (showPinDialog) {
        PinDialog(
            ip = if (usbMode) "USB" else (pendingDevice?.ip ?: manualIp),
            pin = pinInput,
            onPinChanged = { pinInput = it },
            onConfirm = {
                showPinDialog = false
                if (usbMode) vm.connectUsb(pinInput)
                else vm.connectWifi(pendingDevice?.ip ?: manualIp, pin = pinInput)
                pinInput = ""
            },
            onDismiss = { showPinDialog = false; pinInput = "" }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("View Phone Screen") },
                navigationIcon = {
                    IconButton(onClick = { vm.disconnect(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state == ViewerConnectionState.CONNECTED) {
                        Text("$fps FPS", modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelLarge)
                        Button(onClick = { vm.disconnect() }, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Disconnect")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state == ViewerConnectionState.CONNECTED && image != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = image!!,
                    contentDescription = "Phone screen",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (state == ViewerConnectionState.CONNECTING) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state == ViewerConnectionState.ERROR) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Text(
                            errorMessage ?: "Connection failed. Check PIN and network.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Text("Discovered phones on Wi-Fi:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (devices.isEmpty()) {
                    Text("Searching… Start 'Send Screen' on your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices) { device ->
                        Card(
                            onClick = {
                                pendingDevice = device
                                usbMode = false
                                showPinDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Default.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text(device.name, fontWeight = FontWeight.Medium)
                                    Text(device.ip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text("Manual / USB connection:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = manualIp,
                        onValueChange = { manualIp = it },
                        label = { Text("Phone IP address") },
                        placeholder = { Text("e.g. 192.168.1.42") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = { pendingDevice = null; usbMode = false; showPinDialog = true },
                        modifier = Modifier.align(Alignment.CenterVertically),
                        enabled = manualIp.isNotBlank()
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Connect Wi-Fi")
                    }
                    Button(
                        onClick = { usbMode = true; showPinDialog = true },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("USB")
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDialog(
    ip: String,
    pin: String,
    onPinChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Connecting to: $ip")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) onPinChanged(it) },
                    label = { Text("6-digit PIN") },
                    placeholder = { Text("123456") },
                    singleLine = true
                )
                Text("Find the PIN on your phone under 'Send Screen'.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = pin.length == 6) { Text("Connect") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
