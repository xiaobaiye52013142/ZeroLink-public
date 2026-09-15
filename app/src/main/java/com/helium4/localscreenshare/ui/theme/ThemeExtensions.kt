package com.helium4.localscreenshare.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ── Shape shorthands ─────────────────────────────────────────────────────────

val AppTheme.shapeSmall: Shape get() = RoundedCornerShape(cornerRadiusSmall)
val AppTheme.shapeMedium: Shape get() = RoundedCornerShape(cornerRadiusMedium)
val AppTheme.shapeLarge: Shape get() = RoundedCornerShape(cornerRadiusLarge)
val AppTheme.shapeButton: Shape get() = RoundedCornerShape(buttonCornerRadius)

/** True for themes whose surfaces are deliberately translucent over a gradient. */
val AppTheme.isTranslucentSurface: Boolean
    get() = name == ThemeRegistry.GLASSMORPHISM || name == ThemeRegistry.AURORA_UI

val AppTheme.isNeumorphic: Boolean get() = name == ThemeRegistry.NEUMORPHISM
val AppTheme.isBrutalist: Boolean get() = name == ThemeRegistry.BRUTALISM

/** Background brush — a gradient when the theme defines one, otherwise a flat fill. */
val AppTheme.backgroundBrush: Brush
    get() = backgroundGradient
        ?.let { Brush.linearGradient(it) }
        ?: Brush.linearGradient(listOf(colorBackground, colorBackground))

@Composable
@ReadOnlyComposable
fun themeSpacing(multiplier: Float = 1f): Dp =
    (LocalAppTheme.current.spacingUnit.value * multiplier).dp

// ── Depth modifiers ──────────────────────────────────────────────────────────

/**
 * Soft drop shadow in an arbitrary colour. Compose's own `shadow()` can't be tinted, and
 * neumorphism/glow/brutalism all need coloured or offset shadows, so they route through here.
 *
 * setShadowLayer is used rather than a plain blurred fill because it keeps the shape's interior
 * transparent — the caller paints the real background on top.
 */
fun Modifier.softShadow(
    color: Color,
    cornerRadius: Dp,
    blurRadius: Dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
): Modifier = this.drawBehind {
    if (color.alpha == 0f) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(
                blurRadius.toPx().coerceAtLeast(0.1f),
                offsetX.toPx(),
                offsetY.toPx(),
                color.toArgb(),
            )
        }
        canvas.drawRoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height,
            radiusX = cornerRadius.toPx(), radiusY = cornerRadius.toPx(),
            paint = paint,
        )
    }
}

/** Hard, un-blurred offset shadow — the brutalist "printed" look. */
fun Modifier.hardShadow(
    color: Color,
    cornerRadius: Dp,
    offset: Dp,
): Modifier = this.drawBehind {
    drawIntoCanvas { canvas ->
        val paint = Paint().apply { this.color = color }
        canvas.drawRoundRect(
            left = offset.toPx(), top = offset.toPx(),
            right = size.width + offset.toPx(), bottom = size.height + offset.toPx(),
            radiusX = cornerRadius.toPx(), radiusY = cornerRadius.toPx(),
            paint = paint,
        )
    }
}

/** The neumorphic pair: highlight up-left, shade down-right. */
fun Modifier.neumorphicRaised(
    theme: AppTheme,
    cornerRadius: Dp,
    depth: Dp = 7.dp,
): Modifier {
    val (light, dark) = ThemeRegistry.neumorphicShadows(theme.isDark)
    return this
        .softShadow(dark, cornerRadius, depth, depth * 0.6f, depth * 0.6f)
        .softShadow(light, cornerRadius, depth, -depth * 0.6f, -depth * 0.6f)
}

/** Reversed shadows read as a pressed-in well — used for inputs and selected states. */
fun Modifier.neumorphicInset(
    theme: AppTheme,
    cornerRadius: Dp,
    depth: Dp = 5.dp,
): Modifier {
    val (light, dark) = ThemeRegistry.neumorphicShadows(theme.isDark)
    return this
        .softShadow(dark, cornerRadius, depth, -depth * 0.5f, -depth * 0.5f)
        .softShadow(light, cornerRadius, depth, depth * 0.5f, depth * 0.5f)
}

/** Coloured bloom behind a control — aurora and cyberpunk primary actions. */
fun Modifier.glow(color: Color?, cornerRadius: Dp, radius: Dp = 14.dp): Modifier =
    if (color == null) this else softShadow(color.copy(alpha = 0.55f), cornerRadius, radius)

/**
 * The single entry point every themed surface should use: applies whichever depth treatment
 * the active theme calls for, then fills and borders the shape.
 */
fun Modifier.themedSurface(
    theme: AppTheme,
    cornerRadius: Dp,
    fill: Color,
    borderColor: Color = Color.Transparent,
    elevation: Dp = theme.cardElevation,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    val withDepth = when {
        theme.isNeumorphic -> neumorphicRaised(theme, cornerRadius)
        theme.isBrutalist -> hardShadow(theme.colorBorder, cornerRadius, ThemeRegistry.brutalistShadowOffset())
        elevation > 0.dp -> softShadow(
            Color.Black.copy(alpha = if (theme.isDark) 0.55f else 0.22f),
            cornerRadius,
            elevation * 2,
            offsetY = elevation * 0.7f,
        )
        else -> this
    }
    return withDepth
        .background(fill, shape)
        .then(
            if (borderColor.alpha > 0f && theme.borderWidth > 0.dp) {
                Modifier.border(theme.borderWidth, borderColor, shape)
            } else Modifier
        )
}
