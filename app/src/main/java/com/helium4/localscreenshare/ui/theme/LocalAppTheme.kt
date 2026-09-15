package com.helium4.localscreenshare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Provides the active [AppTheme] to every composable in the tree. Installed by AppThemeWrapper.
 *
 * staticCompositionLocalOf (rather than compositionLocalOf) is deliberate: a theme change should
 * recompose the whole tree, which is exactly what we want when the remote flag flips.
 */
val LocalAppTheme = staticCompositionLocalOf<AppTheme> {
    error("LocalAppTheme not provided — wrap the screen in AppThemeWrapper { }")
}

/** Shorthand: `appTheme.colorPrimary` instead of `LocalAppTheme.current.colorPrimary`. */
val appTheme: AppTheme
    @Composable @ReadOnlyComposable get() = LocalAppTheme.current
