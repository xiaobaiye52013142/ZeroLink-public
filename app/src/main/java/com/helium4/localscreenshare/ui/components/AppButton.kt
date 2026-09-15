package com.helium4.localscreenshare.ui.components

import android.graphics.BlurMaskFilter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.theme.ButtonStyle
import com.helium4.localscreenshare.ui.theme.LocalAppTheme
import com.helium4.localscreenshare.ui.theme.ThemeRegistry

enum class AppButtonVariant { PRIMARY, SECONDARY, GHOST, DESTRUCTIVE }

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.PRIMARY,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val theme = LocalAppTheme.current
    val shape = RoundedCornerShape(theme.buttonCornerRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // The accent colour the variant asks for; the buttonStyle decides how it gets used.
    val accent = when (variant) {
        AppButtonVariant.PRIMARY -> theme.colorPrimary
        AppButtonVariant.SECONDARY -> theme.colorSecondary
        AppButtonVariant.GHOST -> theme.colorPrimary
        AppButtonVariant.DESTRUCTIVE -> theme.colorError
    }

    // Brutalism inverts on press instead of animating; everything else scales slightly.
    val inverts = theme.animationStyle == com.helium4.localscreenshare.ui.theme.AnimationStyle.NONE
    val scale by animateFloatAsState(
        targetValue = if (pressed && !inverts) 0.97f else 1f,
        animationSpec = tween(theme.motionDurationMs),
        label = "buttonScale",
    )

    val effectiveStyle = if (variant == AppButtonVariant.GHOST) ButtonStyle.GHOST else theme.buttonStyle
    val invertedNow = inverts && pressed

    var surface = modifier
        .defaultMinSize(minHeight = 48.dp)
        .scale(scale)
        .alpha(if (enabled) 1f else 0.45f)

    // A themed glow sits underneath everything else so it reads as light spill, not a border.
    theme.glowColor?.let { glow ->
        if (variant == AppButtonVariant.PRIMARY) {
            surface = surface.softShadow(glow.copy(alpha = 0.55f), theme.buttonCornerRadius, 14.dp)
        }
    }

    val contentColor: Color
    when (effectiveStyle) {
        ButtonStyle.FILLED -> {
            val fill = if (invertedNow) theme.colorOnPrimary else accent
            contentColor = if (invertedNow) accent else theme.colorOnPrimary
            if (theme.buttonElevation > 0.dp) {
                surface = surface.softShadow(
                    Color.Black.copy(alpha = 0.28f), theme.buttonCornerRadius,
                    theme.buttonElevation * 2, offsetY = theme.buttonElevation,
                )
            }
            surface = surface.clip(shape).background(fill)
        }

        ButtonStyle.OUTLINED -> {
            contentColor = if (invertedNow) theme.colorBackground else accent
            val fill = if (invertedNow) accent else Color.Transparent
            surface = surface
                .clip(shape)
                .background(fill)
                .border(theme.borderWidth, accent, shape)
        }

        ButtonStyle.GHOST -> {
            // Neumorphic raised surface: light from the top-left, shadow to the bottom-right.
            val (light, dark) = ThemeRegistry.neumorphicShadows(theme.isDark)
            contentColor = accent
            surface = if (pressed) {
                // Pressing pushes the surface in — swap the shadow directions.
                surface
                    .softShadow(dark, theme.buttonCornerRadius, 6.dp, (-3).dp, (-3).dp)
                    .softShadow(light, theme.buttonCornerRadius, 6.dp, 3.dp, 3.dp)
                    .clip(shape)
                    .background(theme.colorSurface)
            } else {
                surface
                    .softShadow(dark, theme.buttonCornerRadius, 8.dp, 5.dp, 5.dp)
                    .softShadow(light, theme.buttonCornerRadius, 8.dp, (-5).dp, (-5).dp)
                    .clip(shape)
                    .background(theme.colorSurface)
            }
        }

        ButtonStyle.BEVELED -> {
            contentColor = theme.colorOnPrimary
            if (theme.buttonElevation > 0.dp) {
                surface = surface.softShadow(
                    Color.Black.copy(alpha = 0.35f), theme.buttonCornerRadius,
                    theme.buttonElevation * 2, offsetY = theme.buttonElevation,
                )
            }
            // Lighter at the top, darker at the bottom reads as a lit, extruded face.
            val top = lerp(accent, Color.White, if (pressed) 0.05f else 0.30f)
            val bottom = lerp(accent, Color.Black, if (pressed) 0.30f else 0.18f)
            surface = surface
                .clip(shape)
                .background(Brush.verticalGradient(listOf(top, bottom)))
        }
    }

    Box(
        modifier = surface.clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) {
        // Beveled buttons get a thin highlight along the top edge to finish the 3D read.
        if (effectiveStyle == ButtonStyle.BEVELED && !pressed) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(Color.White.copy(alpha = 0.45f))
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = theme.spacingUnit * 2, vertical = theme.spacingUnit),
            horizontalArrangement = Arrangement.spacedBy(theme.spacingUnit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontFamily = theme.labelFontFamily,
                fontSize = theme.labelFontSize,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Blurred drop shadow in an arbitrary colour. Compose's own `shadow` modifier can't be tinted,
 * so this drops to the framework paint's shadow layer, which can.
 */
internal fun Modifier.softShadow(
    color: Color,
    cornerRadius: Dp,
    blurRadius: Dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp,
): Modifier = this.drawBehind {
    if (blurRadius.toPx() <= 0f) return@drawBehind
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            isAntiAlias = true
            // The rect itself must be invisible — only its shadow layer should paint.
            this.color = android.graphics.Color.TRANSPARENT
            setShadowLayer(blurRadius.toPx(), offsetX.toPx(), offsetY.toPx(), color.toArgb())
        }
        canvas.drawRoundRect(
            left = 0f, top = 0f, right = size.width, bottom = size.height,
            radiusX = cornerRadius.toPx(), radiusY = cornerRadius.toPx(), paint = paint,
        )
    }
}

/** Un-blurred offset block, the hard shadow brutalism uses in place of elevation. */
internal fun Modifier.hardShadow(
    color: Color,
    cornerRadius: Dp,
    offset: Dp,
): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(offset.toPx(), offset.toPx()),
        size = size,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
    )
}

/** Blur mask used by the organic pattern; kept here so the shadow helpers stay together. */
internal fun blurMask(radiusPx: Float) = BlurMaskFilter(radiusPx, BlurMaskFilter.Blur.NORMAL)
