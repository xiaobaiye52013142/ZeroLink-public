package com.helium4.localscreenshare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * The colour surface the app's screens were originally written against.
 *
 * It is now a projection of the active [AppTheme] rather than a pair of hand-written palettes,
 * so every existing `MaterialTheme.appColors.xxx` call site follows the remote theme with no
 * change at the call site. New code should prefer [LocalAppTheme] directly, which exposes the
 * full token set instead of this reduced view.
 */
data class AppColors(
    val surface: Color,         // card / list-item background
    val surfaceElevated: Color, // dialog / bottom-sheet background
    val primary: Color,         // main accent
    val primaryVariant: Color,  // gradient end / secondary accent
    val onPrimary: Color,       // text & icons placed ON a primary-coloured surface
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val connected: Color,       // green  – wifi connected, live indicator
    val error: Color,           // red    – disconnected, stop, error
    val warning: Color,         // amber  – caution
)

/** Projects the full token set down onto the legacy [AppColors] view. */
fun AppTheme.toAppColors(): AppColors = AppColors(
    surface = cardBackgroundColor,
    surfaceElevated = dialogBackground,
    primary = colorPrimary,
    primaryVariant = colorSecondary,
    onPrimary = colorOnPrimary,
    textPrimary = colorOnBackground,
    textSecondary = colorOnBackground.copy(alpha = 0.70f),
    textMuted = colorOnBackground.copy(alpha = 0.45f),
    // "Connected" is semantic, not decorative — keep it readably green on any theme rather
    // than letting a magenta or gold palette claim it.
    connected = if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32),
    error = colorError,
    warning = if (isDark) Color(0xFFFFCA28) else Color(0xFFF57F17),
)

val MaterialTheme.appColors: AppColors
    @Composable @ReadOnlyComposable get() = LocalAppTheme.current.toAppColors()
