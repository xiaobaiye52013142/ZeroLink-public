package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/**
 * Themed confirmation dialog. Pass [dismissText] to get a second, secondary-styled button.
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String? = null,
    onDismissAction: (() -> Unit)? = null,
) {
    val t = LocalAppTheme.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(t.cornerRadiusLarge),
        containerColor = t.dialogBackground,
        titleContentColor = t.colorOnSurface,
        textContentColor = t.colorOnSurface,
        title = {
            Text(
                text = title,
                color = t.colorOnSurface,
                fontFamily = t.headingFontFamily,
                fontSize = t.headingFontSize,
                fontWeight = t.headingFontWeight,
            )
        },
        text = {
            Text(
                text = text,
                color = t.colorOnSurface,
                fontFamily = t.bodyFontFamily,
                fontSize = t.bodyFontSize,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmText,
                    color = t.colorPrimary,
                    fontFamily = t.labelFontFamily,
                    fontSize = t.labelFontSize,
                )
            }
        },
        dismissButton = dismissText?.let { label ->
            {
                TextButton(onClick = { (onDismissAction ?: onDismiss)() }) {
                    Text(
                        text = label,
                        color = t.colorSecondary,
                        fontFamily = t.labelFontFamily,
                        fontSize = t.labelFontSize,
                    )
                }
            }
        },
    )
}
