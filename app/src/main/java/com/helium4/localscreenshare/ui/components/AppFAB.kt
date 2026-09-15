package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/**
 * Collapses to a circular-ish FAB, or expands to a pill with [text] when [expanded] is true.
 * Themes that set a glowColor (aurora, cyberpunk) get a coloured spot shadow instead of the
 * default neutral elevation shadow.
 */
@Composable
fun AppFAB(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    text: String? = null,
) {
    val t = LocalAppTheme.current
    val shape = RoundedCornerShape(t.cornerRadiusLarge)
    val glowed = t.glowColor?.let { glow ->
        modifier.shadow(
            elevation = 12.dp,
            shape = shape,
            ambientColor = glow,
            spotColor = glow,
        )
    } ?: modifier

    if (expanded && text != null) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            modifier = glowed,
            shape = shape,
            containerColor = t.fabBackgroundColor,
            contentColor = t.fabIconColor,
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = t.fabIconColor,
                )
            },
            text = {
                Text(
                    text = text,
                    color = t.fabIconColor,
                    fontFamily = t.labelFontFamily,
                    fontSize = t.labelFontSize,
                )
            },
        )
    } else {
        FloatingActionButton(
            onClick = onClick,
            modifier = glowed,
            shape = shape,
            containerColor = t.fabBackgroundColor,
            contentColor = t.fabIconColor,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = t.fabIconColor,
            )
        }
    }
}
