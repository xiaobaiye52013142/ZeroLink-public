package com.helium4.localscreenshare.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Seasonal decoration drawn on top of all content.
 *
 * The whole layer is a bare [Canvas] with no pointer-input modifier, so it never consumes
 * touch events — taps pass straight through to the UI underneath. Particle positions come
 * from a fixed seed so they stay put across recompositions instead of teleporting.
 */
@Composable
fun DecorativeOverlayLayer(effect: OverlayEffect, tint: Color, accent: Color) {
    if (effect == OverlayEffect.NONE) return

    val particles = remember(effect) { generateParticles(effect) }
    val transition = rememberInfiniteTransition(label = "overlay")

    // One slow master clock; each particle reads it at its own phase offset.
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress",
    )

    val twinkle by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "twinkle",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        particles.forEach { p ->
            when (effect) {
                OverlayEffect.SNOWFLAKES -> drawSnowflake(p, progress, tint)
                OverlayEffect.EGGS -> drawEgg(p, progress, tint, accent)
                OverlayEffect.CRESCENTS -> drawCrescent(p, progress, tint, accent)
                OverlayEffect.STARS -> drawStar(p, twinkle, tint)
                OverlayEffect.FLOWERS -> drawPetal(p, progress, tint, accent)
                OverlayEffect.NONE -> Unit
            }
        }
    }
}

/** A single decoration. Coordinates are fractions of the canvas so they scale to any screen. */
private data class Particle(
    val xFraction: Float,
    val yFraction: Float,
    val size: Float,
    val phase: Float,
    val drift: Float,
    val spin: Float,
    val alpha: Float,
)

private fun generateParticles(effect: OverlayEffect): List<Particle> {
    // Fixed seed: the layout must not reshuffle every recomposition.
    val rng = Random(effect.ordinal * 7919)
    val count = when (effect) {
        OverlayEffect.SNOWFLAKES -> 26
        OverlayEffect.STARS -> 30
        OverlayEffect.EGGS -> 12
        OverlayEffect.CRESCENTS -> 10
        OverlayEffect.FLOWERS -> 16
        OverlayEffect.NONE -> 0
    }
    return List(count) {
        Particle(
            xFraction = rng.nextFloat(),
            yFraction = rng.nextFloat(),
            size = rng.nextFloat() * 0.5f + 0.5f,
            phase = rng.nextFloat(),
            drift = (rng.nextFloat() - 0.5f) * 0.06f,
            spin = rng.nextFloat() * 360f,
            // Kept low deliberately: decoration must never compete with content for attention.
            alpha = rng.nextFloat() * 0.18f + 0.10f,
        )
    }
}

/** Falling particles wrap around vertically using the shared clock plus a per-particle phase. */
private fun fallingOffset(p: Particle, progress: Float, size: Size): Offset {
    val t = (progress + p.phase) % 1f
    return Offset(
        x = (p.xFraction + p.drift * sin(t * 2f * Math.PI.toFloat())) * size.width,
        y = t * size.height,
    )
}

/** Gentle bobbing for particles that float rather than fall. */
private fun floatingOffset(p: Particle, progress: Float, size: Size): Offset {
    val t = (progress + p.phase) % 1f
    return Offset(
        x = p.xFraction * size.width + sin(t * 2f * Math.PI.toFloat()) * 12f,
        y = p.yFraction * size.height + cos(t * 2f * Math.PI.toFloat()) * 16f,
    )
}

private fun DrawScope.drawSnowflake(p: Particle, progress: Float, tint: Color) {
    val center = fallingOffset(p, progress, size)
    val radius = 5f * p.size
    val color = tint.copy(alpha = p.alpha)
    rotate(degrees = p.spin + progress * 180f, pivot = center) {
        // Six spokes with short cross-bars — reads as a snowflake at small sizes.
        repeat(6) { i ->
            val angle = (i * 60f) * (Math.PI / 180f).toFloat()
            val dx = cos(angle) * radius
            val dy = sin(angle) * radius
            drawLine(color, center, Offset(center.x + dx, center.y + dy), strokeWidth = 1.4f)
            drawLine(
                color,
                Offset(center.x + dx * 0.6f, center.y + dy * 0.6f),
                Offset(center.x + dx * 0.6f - dy * 0.25f, center.y + dy * 0.6f + dx * 0.25f),
                strokeWidth = 1f,
            )
        }
    }
}

private fun DrawScope.drawEgg(p: Particle, progress: Float, tint: Color, accent: Color) {
    val center = floatingOffset(p, progress, size)
    val w = 11f * p.size
    val h = 15f * p.size
    drawOval(
        color = (if (p.phase > 0.5f) accent else tint).copy(alpha = p.alpha),
        topLeft = Offset(center.x - w, center.y - h),
        size = Size(w * 2, h * 2),
    )
    // Two horizontal bands, the classic painted-egg motif.
    repeat(2) { i ->
        val y = center.y - h * 0.3f + i * h * 0.55f
        drawLine(
            color = tint.copy(alpha = p.alpha * 0.75f),
            start = Offset(center.x - w * 0.82f, y),
            end = Offset(center.x + w * 0.82f, y),
            strokeWidth = 1.6f,
        )
    }
}

private fun DrawScope.drawCrescent(p: Particle, progress: Float, tint: Color, accent: Color) {
    val center = floatingOffset(p, progress, size)
    val r = 10f * p.size
    val color = tint.copy(alpha = p.alpha)
    // A filled disc with a second disc punched out of it by overdrawing in the background —
    // approximated here with a stroked arc, which reads correctly at this scale.
    val path = Path().apply {
        addArc(
            androidx.compose.ui.geometry.Rect(
                center.x - r, center.y - r, center.x + r, center.y + r
            ),
            startAngleDegrees = 40f,
            sweepAngleDegrees = 280f,
        )
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
    // Companion star, offset toward the crescent's opening.
    val sx = center.x + r * 1.25f
    val sy = center.y - r * 0.5f
    drawStarShape(Offset(sx, sy), r * 0.34f, accent.copy(alpha = p.alpha))
}

private fun DrawScope.drawStar(p: Particle, twinkle: Float, tint: Color) {
    val center = Offset(p.xFraction * size.width, p.yFraction * size.height)
    // Phase-shift the shared twinkle so stars don't all pulse in lockstep.
    val a = p.alpha * (if (p.phase > 0.5f) twinkle else 1.35f - twinkle)
    drawStarShape(center, 4.5f * p.size, tint.copy(alpha = a.coerceIn(0f, 1f)))
}

private fun DrawScope.drawStarShape(center: Offset, radius: Float, color: Color) {
    // Four-point sparkle: two crossed tapers.
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawPetal(p: Particle, progress: Float, tint: Color, accent: Color) {
    val center = fallingOffset(p, progress, size)
    val r = 7f * p.size
    val color = (if (p.phase > 0.6f) accent else tint).copy(alpha = p.alpha)
    rotate(degrees = p.spin + progress * 90f, pivot = center) {
        // Five overlapping ovals arranged radially read as a soft blossom.
        repeat(5) { i ->
            val angle = (i * 72f) * (Math.PI / 180f).toFloat()
            val cx = center.x + cos(angle) * r * 0.55f
            val cy = center.y + sin(angle) * r * 0.55f
            drawOval(
                color = color,
                topLeft = Offset(cx - r * 0.42f, cy - r * 0.62f),
                size = Size(r * 0.84f, r * 1.24f),
            )
        }
    }
}
