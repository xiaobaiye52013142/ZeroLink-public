package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/** Long-press tooltip painted from theme tokens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTooltip(
    text: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t = LocalAppTheme.current
    TooltipBox(
        modifier = modifier,
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        state = rememberTooltipState(),
        tooltip = {
            PlainTooltip(
                shape = RoundedCornerShape(t.cornerRadiusSmall),
                containerColor = t.tooltipBackgroundColor,
                contentColor = t.tooltipTextColor,
            ) {
                Text(
                    text = text,
                    color = t.tooltipTextColor,
                    fontFamily = t.labelFontFamily,
                    fontSize = t.labelFontSize,
                )
            }
        },
        content = content,
    )
}
