package com.helium4.localscreenshare.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ButtonStyle { FILLED, OUTLINED, GHOST, BEVELED }
enum class IconStyle { OUTLINE, FILLED, ROUNDED, SHARP }
enum class SpacingDensity { COMPACT, NORMAL, COMFORTABLE }
enum class BackgroundPattern { NONE, GEOMETRIC, ORGANIC, NOISE, GRID, SCANLINE }
enum class AnimationStyle { NONE, SUBTLE, PLAYFUL, DRAMATIC }
enum class OverlayEffect { NONE, SNOWFLAKES, EGGS, CRESCENTS, STARS, FLOWERS }

/**
 * Every visual decision in the app resolves to a token on this class. Nothing in a composable
 * may hardcode a colour, corner radius or text style — read it from [LocalAppTheme] instead.
 *
 * Only the handful of parameters at the top are genuinely distinctive per theme; everything
 * below derives from them by default, so a theme definition stays readable. Kotlin evaluates
 * default expressions against earlier parameters, which is what makes that derivation possible.
 */
data class AppTheme(
    val name: String,
    val isDark: Boolean,

    // ── Core palette — every theme sets these explicitly ─────────────────────
    val colorPrimary: Color,
    val colorSecondary: Color,
    val colorBackground: Color,
    val colorSurface: Color,
    val colorOnPrimary: Color,
    val colorOnBackground: Color,
    val colorAccent: Color,

    // ── Derived colours — override only when a theme needs something special ─
    val colorSurfaceVariant: Color = colorSurface,
    val colorOnSurface: Color = colorOnBackground,
    val colorBorder: Color = colorOnBackground.copy(alpha = 0.12f),
    val colorError: Color = if (isDark) Color(0xFFEF5350) else Color(0xFFC62828),
    val colorOutline: Color = colorOnBackground.copy(alpha = 0.30f),

    // ── Typography ───────────────────────────────────────────────────────────
    val headingFontFamily: FontFamily = FontFamily.Default,
    val bodyFontFamily: FontFamily = FontFamily.Default,
    val labelFontFamily: FontFamily = bodyFontFamily,
    val headingFontSize: TextUnit = 24.sp,
    val bodyFontSize: TextUnit = 14.sp,
    val labelFontSize: TextUnit = 12.sp,
    val headingFontWeight: FontWeight = FontWeight.SemiBold,

    // ── Shape ────────────────────────────────────────────────────────────────
    val cornerRadiusSmall: Dp = 4.dp,
    val cornerRadiusMedium: Dp = 12.dp,
    val cornerRadiusLarge: Dp = 20.dp,
    val buttonCornerRadius: Dp = cornerRadiusMedium,

    // ── Depth ────────────────────────────────────────────────────────────────
    val cardElevation: Dp = 1.dp,
    val buttonElevation: Dp = 0.dp,
    val borderWidth: Dp = 1.dp,

    // ── Behaviour ────────────────────────────────────────────────────────────
    val buttonStyle: ButtonStyle = ButtonStyle.FILLED,
    val iconStyle: IconStyle = IconStyle.OUTLINE,
    val spacingDensity: SpacingDensity = SpacingDensity.NORMAL,
    val backgroundPattern: BackgroundPattern = BackgroundPattern.NONE,
    val animationStyle: AnimationStyle = AnimationStyle.SUBTLE,
    val decorativeOverlay: OverlayEffect = OverlayEffect.NONE,

    /** Non-null turns the background into a gradient (glassmorphism, aurora, y2k). */
    val backgroundGradient: List<Color>? = null,
    /** Coloured glow behind primary buttons — aurora and cyberpunk use this. */
    val glowColor: Color? = null,

    // ── System bars ──────────────────────────────────────────────────────────
    val statusBarColor: Color = colorBackground,
    val navigationBarColor: Color = colorBackground,

    // ── Per-component surfaces ───────────────────────────────────────────────
    val topAppBarColor: Color = colorBackground,
    val topAppBarContentColor: Color = colorOnBackground,
    val bottomNavBackgroundColor: Color = colorSurface,
    val bottomNavSelectedColor: Color = colorPrimary,
    val bottomNavUnselectedColor: Color = colorOnSurface.copy(alpha = 0.60f),

    val inputFieldBackground: Color = colorSurface,
    val inputFieldBorderColor: Color = colorOutline,
    val inputFieldTextColor: Color = colorOnSurface,

    val cardBackgroundColor: Color = colorSurface,
    val cardBorderColor: Color = colorBorder,
    val dividerColor: Color = colorBorder,
    val dividerThickness: Dp = 1.dp,

    val snackbarBackground: Color = if (isDark) colorSurfaceVariant else colorOnBackground,
    val snackbarTextColor: Color = if (isDark) colorOnSurface else colorBackground,
    val dialogBackground: Color = colorSurface,

    val shimmerBaseColor: Color = colorOnBackground.copy(alpha = 0.08f),
    val shimmerHighlightColor: Color = colorOnBackground.copy(alpha = 0.16f),

    val fabBackgroundColor: Color = colorPrimary,
    val fabIconColor: Color = colorOnPrimary,

    val chipBackgroundColor: Color = colorPrimary.copy(alpha = 0.12f),
    val chipTextColor: Color = colorPrimary,

    val switchThumbColor: Color = colorPrimary,
    val switchTrackColor: Color = colorPrimary.copy(alpha = 0.50f),
    val checkboxColor: Color = colorPrimary,
    val radioButtonColor: Color = colorPrimary,
    val sliderActiveColor: Color = colorPrimary,
    val sliderInactiveColor: Color = colorPrimary.copy(alpha = 0.24f),
    val progressIndicatorColor: Color = colorPrimary,

    val tooltipBackgroundColor: Color = if (isDark) colorSurfaceVariant else colorOnBackground,
    val tooltipTextColor: Color = if (isDark) colorOnSurface else colorBackground,
    val badgeBackgroundColor: Color = colorError,
    val badgeTextColor: Color = Color.White,

    /**
     * Scrim for HUD chips that float over the received video stream. Stays dark on every theme
     * because it sits over unpredictable frame content, not over [colorBackground] — a light
     * scrim would make the overlay text unreadable against a bright frame. Themes may still
     * tune the opacity.
     */
    val videoOverlayScrim: Color = Color.Black.copy(alpha = 0.60f),
    val videoOverlayContent: Color = Color.White,
) {
    /** Content padding step used by AppScaffold and the App* components. */
    val spacingUnit: Dp
        get() = when (spacingDensity) {
            SpacingDensity.COMPACT -> 4.dp
            SpacingDensity.NORMAL -> 8.dp
            SpacingDensity.COMFORTABLE -> 12.dp
        }

    /** Animation durations scale with the theme's personality. */
    val motionDurationMs: Int
        get() = when (animationStyle) {
            AnimationStyle.NONE -> 0
            AnimationStyle.SUBTLE -> 150
            AnimationStyle.PLAYFUL -> 400
            AnimationStyle.DRAMATIC -> 700
        }
}
