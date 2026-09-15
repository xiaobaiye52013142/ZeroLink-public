package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/**
 * Wraps [content] with a themed badge. A null [text] renders the small dot variant.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBadge(
    text: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalAppTheme.current
    BadgedBox(
        modifier = modifier,
        badge = {
            Badge(
                containerColor = t.badgeBackgroundColor,
                contentColor = t.badgeTextColor,
            ) {
                if (text != null) {
                    Text(
                        text = text,
                        color = t.badgeTextColor,
                        fontFamily = t.labelFontFamily,
                        fontSize = t.labelFontSize,
                    )
                }
            }
        },
        content = content,
    )
}
