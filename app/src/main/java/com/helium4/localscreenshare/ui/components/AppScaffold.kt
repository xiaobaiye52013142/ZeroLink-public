package com.helium4.localscreenshare.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.helium4.localscreenshare.ui.theme.AnimationStyle
import com.helium4.localscreenshare.ui.theme.AppTheme
import com.helium4.localscreenshare.ui.theme.BackgroundPattern
import com.helium4.localscreenshare.ui.theme.LocalAppTheme
import com.helium4.localscreenshare.ui.theme.ThemeRegistry
import com.helium4.localscreenshare.ui.theme.isTranslucentSurface
import kotlin.random.Random

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val theme = LocalAppTheme.current

    // Aurora's gradient is specified as slowly moving. One very long transition drives it; every
    // other theme resolves to a constant so no animation is running for them.
    val animateGradient = theme.backgroundGradient != null &&
        theme.animationStyle != AnimationStyle.NONE &&
        theme.name == ThemeRegistry.AURORA_UI
    val shift by if (animateGradient) {
        rememberInfiniteTransition(label = "auroraGradient").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 22_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "auroraShift",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val backdrop = theme.backgroundGradient
        ?.let { Modifier.background(gradientBrush(it, shift)) }
        ?: Modifier.background(theme.colorBackground)

    Box(modifier.fillMaxSize().then(backdrop)) {
        // Frosted glass needs something out-of-focus behind it, and a smooth gradient stays smooth
        // when blurred. These soft colour bloms give the blur something to actually diffuse, so the
        // translucent surfaces above read as glass rather than as flat tinted panels.
        // Modifier.blur is a no-op below API 31, which leaves the plain translucency as the fallback.
        if (theme.isTranslucentSurface) {
            // Deliberately not animated. Blurring a full-screen layer is GPU work, and redoing it
            // every frame to drift the blooms is not worth it — the gradient underneath carries the
            // motion instead, and it animates without touching this layer.
            Box(
                Modifier
                    .fillMaxSize()
                    .blur(56.dp)
                    .drawBehind { drawGlassBlooms(theme) }
            )
        }

        Box(Modifier.fillMaxSize().drawBehind { drawPattern(theme) }) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = theme.colorOnBackground,
                topBar = topBar,
                bottomBar = bottomBar,
                floatingActionButton = floatingActionButton,
                content = content,
            )
        }
    }
}

/** Slides the gradient's endpoints so the colours drift without the fill ever leaving the screen. */
private fun gradientBrush(colors: List<Color>, shift: Float): Brush {
    if (shift == 0f) return Brush.linearGradient(colors)
    val d = (shift - 0.5f) * 600f
    return Brush.linearGradient(
        colors = colors,
        start = Offset(d, 0f),
        end = Offset(1000f + d, 1400f),
    )
}

/** Large, heavily overlapping accent circles — the light sources the glass blur diffuses. */
private fun DrawScope.drawGlassBlooms(theme: AppTheme) {
    val unit = maxOf(size.width, size.height)
    val blooms = listOf(
        Triple(0.20f, 0.18f, theme.colorAccent),
        Triple(0.85f, 0.35f, theme.colorPrimary),
        Triple(0.45f, 0.85f, theme.colorSecondary),
    )
    blooms.forEachIndexed { i, (x, y, color) ->
        drawCircle(
            color = color.copy(alpha = 0.45f),
            radius = unit * (0.30f + 0.06f * i),
            center = Offset(x * size.width, y * size.height),
        )
    }
}

// Positions are normalised 0..1 and generated once from a fixed seed, so the pattern is identical
// on every recomposition and every device rather than shimmering as the tree redraws.
private data class Blob(val x: Float, val y: Float, val r: Float, val kind: Int, val rot: Float)

private val geometricShapes: List<Blob> = Random(42).let { r ->
    List(22) { Blob(r.nextFloat(), r.nextFloat(), 0.03f + r.nextFloat() * 0.07f, r.nextInt(3), r.nextFloat() * 360f) }
}

private val organicBlobs: List<Blob> = Random(7).let { r ->
    List(6) { Blob(r.nextFloat(), r.nextFloat(), 0.18f + r.nextFloat() * 0.22f, 0, 0f) }
}

private val noiseDots: List<Blob> = Random(1312).let { r ->
    List(420) { Blob(r.nextFloat(), r.nextFloat(), 0.0015f + r.nextFloat() * 0.002f, 0, 0f) }
}

private fun DrawScope.drawPattern(theme: AppTheme) {
    val tint = theme.colorOnBackground
    when (theme.backgroundPattern) {
        BackgroundPattern.NONE -> Unit

        BackgroundPattern.GRID -> {
            val step = 24f * density
            val c = tint.copy(alpha = 0.06f)
            var x = 0f
            while (x <= size.width) {
                drawLine(c, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        BackgroundPattern.SCANLINE -> {
            val step = 4f * density
            val c = tint.copy(alpha = 0.08f)
            var y = 0f
            while (y <= size.height) {
                drawLine(c, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        BackgroundPattern.GEOMETRIC -> {
            val c = tint.copy(alpha = 0.08f)
            val unit = minOf(size.width, size.height)
            geometricShapes.forEach { s ->
                val cx = s.x * size.width
                val cy = s.y * size.height
                val r = s.r * unit
                when (s.kind) {
                    0 -> drawCircle(c, radius = r, center = Offset(cx, cy))
                    1 -> rotate(s.rot, Offset(cx, cy)) {
                        drawRect(c, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2))
                    }
                    else -> rotate(s.rot, Offset(cx, cy)) {
                        val p = Path().apply {
                            moveTo(cx, cy - r)
                            lineTo(cx + r, cy + r)
                            lineTo(cx - r, cy + r)
                            close()
                        }
                        drawPath(p, c)
                    }
                }
            }
        }

        BackgroundPattern.ORGANIC -> {
            val c = tint.copy(alpha = 0.06f)
            val unit = minOf(size.width, size.height)
            organicBlobs.forEach { b ->
                // Overlapping offset circles read as a soft irregular blob without needing a blur.
                val cx = b.x * size.width
                val cy = b.y * size.height
                val r = b.r * unit
                drawCircle(c, radius = r, center = Offset(cx, cy))
                drawCircle(c, radius = r * 0.75f, center = Offset(cx + r * 0.5f, cy + r * 0.3f))
                drawCircle(c, radius = r * 0.65f, center = Offset(cx - r * 0.4f, cy + r * 0.45f))
            }
        }

        BackgroundPattern.NOISE -> {
            val c = tint.copy(alpha = 0.05f)
            val unit = minOf(size.width, size.height)
            noiseDots.forEach { d ->
                drawCircle(c, radius = d.r * unit, center = Offset(d.x * size.width, d.y * size.height))
            }
        }
    }
}

