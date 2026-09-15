package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

@Composable
fun AppListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val t = LocalAppTheme.current
    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        colors = ListItemDefaults.colors(
            containerColor = t.colorSurface,
            headlineColor = t.colorOnSurface,
            supportingColor = t.colorOnSurface.copy(alpha = 0.70f),
            leadingIconColor = t.colorPrimary,
            trailingIconColor = t.colorOnSurface.copy(alpha = 0.70f),
        ),
        headlineContent = {
            Text(
                text = headline,
                color = t.colorOnSurface,
                fontFamily = t.bodyFontFamily,
                fontSize = t.bodyFontSize,
            )
        },
        supportingContent = supporting?.let { s ->
            {
                Text(
                    text = s,
                    color = t.colorOnSurface.copy(alpha = 0.70f),
                    fontFamily = t.bodyFontFamily,
                    fontSize = t.labelFontSize,
                )
            }
        },
        leadingContent = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = t.colorPrimary,
                )
            }
        },
        trailingContent = trailing,
    )
}
