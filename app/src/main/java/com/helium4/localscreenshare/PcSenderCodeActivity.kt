package com.helium4.localscreenshare

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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

class PcSenderCodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val script = assets.open("sender.py").bufferedReader().readText()
        setContent {
            LocalScreenShareTheme {
                PcSenderCodeScreen(script = script, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PcSenderCodeScreen(script: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = MaterialTheme.appColors

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = "Python Sender Script",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { copyToClipboard(context, script) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = c.primary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(4.dp))

            Surface(
                shape = appTheme.shapeMedium,
                color = c.primary.copy(alpha = 0.08f),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Terminal,
                        contentDescription = null,
                        tint = c.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Run on your PC terminal",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "1. pip install mss Pillow",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "2. python sender.py <PIN>",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "PIN is shown in the Mirror PC dialog.",
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            AppButton(
                text = "Copy Script",
                onClick = { copyToClipboard(context, script) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(52.dp),
                icon = Icons.Default.ContentCopy
            )

            Spacer(Modifier.height(12.dp))

            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
            ) {
                Box(
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    Text(
                        text = script,
                        fontFamily = FontFamily.Monospace,
                        fontSize = appTheme.labelFontSize,
                        color = c.textPrimary,
                        // Derived, not fixed: a theme with a larger label size would otherwise
                        // crowd these script lines together.
                        lineHeight = appTheme.labelFontSize * 1.5f
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun copyToClipboard(context: Context, script: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ZeroLink sender.py", script))
    Toast.makeText(context, "Script copied to clipboard", Toast.LENGTH_SHORT).show()
}
