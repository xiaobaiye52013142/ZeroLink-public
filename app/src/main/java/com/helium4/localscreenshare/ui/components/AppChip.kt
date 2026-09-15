package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/**
 * Built from Surface rather than Material's FilterChip so every colour, radius and border
 * comes from a token — the Material chip bakes in its own container/border defaults.
 * When [selected] is true the chip inverts to the primary colour to read as active.
 */
@Composable
fun AppChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
) {
    val t = LocalAppTheme.current
    val container = if (selected) t.colorPrimary else t.chipBackgroundColor
    val content = if (selected) t.colorOnPrimary else t.chipTextColor

    Surface(
        modifier = modifier,
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = RoundedCornerShape(t.cornerRadiusSmall),
        color = container,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = t.spacingUnit * 1.5f, vertical = t.spacingUnit),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(t.spacingUnit / 2),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = label,
                color = content,
                fontFamily = t.labelFontFamily,
                fontSize = t.labelFontSize,
            )
        }
    }
}
