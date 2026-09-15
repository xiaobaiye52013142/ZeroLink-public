package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.theme.LocalAppTheme
import com.helium4.localscreenshare.ui.theme.ThemeRegistry

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(theme.cornerRadiusLarge)

    var box = modifier

    // Each of these themes replaces elevation with a technique of its own, so they branch out
    // before the generic elevation path below.
    when (theme.name) {
        ThemeRegistry.NEUMORPHISM -> {
            val (light, dark) = ThemeRegistry.neumorphicShadows(theme.isDark)
            box = box
                .softShadow(dark, theme.cornerRadiusLarge, 10.dp, 6.dp, 6.dp)
                .softShadow(light, theme.cornerRadiusLarge, 10.dp, (-6).dp, (-6).dp)
                .clip(shape)
                .background(theme.cardBackgroundColor)
        }

        ThemeRegistry.BRUTALISM -> {
            box = box
                .hardShadow(theme.colorBorder, theme.cornerRadiusLarge, ThemeRegistry.brutalistShadowOffset())
                .clip(shape)
                .background(theme.cardBackgroundColor)
                .border(theme.borderWidth, theme.cardBorderColor, shape)
        }

        ThemeRegistry.GLASSMORPHISM, ThemeRegistry.AURORA_UI -> {
            // The surface token already carries its own alpha, so it frosts whatever is behind it.
            box = box
                .clip(shape)
                .background(theme.cardBackgroundColor)
                .border(theme.borderWidth, theme.cardBorderColor, shape)
        }

        else -> {
            if (theme.cardElevation > 0.dp) {
                box = box.softShadow(
                    Color.Black.copy(alpha = if (theme.isDark) 0.45f else 0.20f),
                    theme.cornerRadiusLarge,
                    theme.cardElevation * 2,
                    offsetY = theme.cardElevation,
                )
            }
            box = box.clip(shape).background(theme.cardBackgroundColor)
            if (theme.borderWidth > 0.dp && theme.cardBorderColor != Color.Transparent) {
                box = box.border(theme.borderWidth, theme.cardBorderColor, shape)
            }
        }
    }

    if (onClick != null) box = box.clickable(onClick = onClick)

    Column(
        modifier = box.padding(theme.spacingUnit * 2),
        content = content,
    )
}
