package com.helium4.localscreenshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.components.AppButton
import com.helium4.localscreenshare.ui.components.AppButtonVariant
import com.helium4.localscreenshare.ui.components.AppCard
import com.helium4.localscreenshare.ui.components.AppScaffold
import com.helium4.localscreenshare.ui.components.AppSwitch
import com.helium4.localscreenshare.ui.components.AppTopAppBar
import com.helium4.localscreenshare.ui.theme.LocalScreenShareTheme
import com.helium4.localscreenshare.ui.theme.appColors
import com.helium4.localscreenshare.ui.theme.appTheme

class LogViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalScreenShareTheme {
                LogViewerScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun LogViewerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.appColors
    var loggingEnabled by remember { mutableStateOf(AppLogger.isEnabled()) }
    var logs by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        logs = AppLogger.readLogs()
    }

    LaunchedEffect(loggingEnabled) {
        logs = AppLogger.readLogs()
    }

    AppScaffold(
        topBar = { AppTopAppBar(title = "Diagnostic Logs", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            AppCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Logging",
                            style = MaterialTheme.typography.titleSmall,
                            color = c.textPrimary
                        )
                        Text(
                            text = "Capture app events to diagnose issues",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                    AppSwitch(
                        checked = loggingEnabled,
                        onCheckedChange = { enabled ->
                            AppLogger.setEnabled(context, enabled)
                            loggingEnabled = enabled
                            if (enabled) {
                                AppLogger.i("LogViewer", "Logging enabled by user")
                                logs = AppLogger.readLogs()
                            }
                        }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppButton(
                    text = "Copy Logs",
                    onClick = {
                        val content = AppLogger.readLogs()
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("ZeroLink Logs", content))
                        Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                    },
                    variant = AppButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                AppButton(
                    text = "Clear Logs",
                    onClick = {
                        AppLogger.clearLogs()
                        logs = ""
                    },
                    variant = AppButtonVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
            }

            // TEST ONLY — remove before release
            AppButton(
                text = "Test Crash",
                onClick = { throw RuntimeException("ZeroLink crash test — verifying AppLogger + Crashlytics pipeline") },
                variant = AppButtonVariant.DESTRUCTIVE,
                modifier = Modifier.fillMaxWidth()
            )

            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (logs.isBlank()) {
                        Text(
                            text = "No logs captured yet.",
                            color = c.textSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    } else {
                        val scrollState = rememberScrollState()
                        Text(
                            text = logs,
                            fontFamily = FontFamily.Monospace,
                            fontSize = appTheme.labelFontSize,
                            color = c.textPrimary,
                            modifier = Modifier.verticalScroll(scrollState)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
