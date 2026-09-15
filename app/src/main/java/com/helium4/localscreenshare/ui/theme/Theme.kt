package com.helium4.localscreenshare.ui.theme

import androidx.compose.runtime.Composable

/**
 * Historical entry point kept so every existing `setContent { LocalScreenShareTheme { … } }`
 * call site keeps working. All it does now is delegate to [AppThemeWrapper], which resolves the
 * active theme from Remote Config, installs [LocalAppTheme] and paints the seasonal overlay.
 *
 * The [darkTheme] parameter is ignored: light/dark is decided by [ThemeManager]'s `force_dark`
 * override falling back to the system setting, so a caller can't accidentally pin one screen
 * to a different mode than the rest of the app.
 */
@Composable
fun LocalScreenShareTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    AppThemeWrapper(content = content)
}
