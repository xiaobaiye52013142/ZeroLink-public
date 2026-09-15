package com.helium4.localscreenshare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Root of every screen. Resolves the active [AppTheme] from [ThemeManager], installs it on
 * [LocalAppTheme], mirrors it into [MaterialTheme] so stock Material components inherit it too,
 * and paints the seasonal overlay above the content.
 */
@Composable
fun AppThemeWrapper(content: @Composable () -> Unit) {
    val themeId by ThemeManager.themeId.collectAsStateWithLifecycle()
    val forceDark by ThemeManager.forceDark.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val dark = forceDark ?: systemDark
    val theme = remember(themeId, dark) { ThemeRegistry.resolve(themeId, dark) }

    // The Material theme is the one place we defer to the platform: its whole point is to follow
    // the Material 3 spec, which on API 31+ means the user's wallpaper-derived palette.
    val context = LocalContext.current
    val colorScheme = remember(theme, dark, context) {
        if (theme.name == ThemeRegistry.MATERIAL && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            theme.toColorScheme()
        }
    }
    val typography = remember(theme) { theme.toTypography() }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = theme.statusBarColor.toArgb()
            window.navigationBarColor = theme.navigationBarColor.toArgb()
            // Icon brightness must contrast with the bar it sits on, which is not always the
            // same as the theme's overall darkness (e.g. a dark app bar on a light theme).
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !theme.statusBarColor.isDarkish()
            controller.isAppearanceLightNavigationBars = !theme.navigationBarColor.isDarkish()
        }
    }

    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(colorScheme = colorScheme, typography = typography) {
            Box(Modifier.fillMaxSize()) {
                content()
                // Drawn last so it sits above content; the Canvas takes no pointer input,
                // so taps fall through to whatever is underneath.
                DecorativeOverlayLayer(
                    effect = theme.decorativeOverlay,
                    tint = theme.colorOnBackground,
                    accent = theme.colorAccent,
                )
               // DebugThemeSwitcher()
            }
        }
    }
}

/** Relative luminance test — decides whether system-bar icons should be light or dark. */
private fun androidx.compose.ui.graphics.Color.isDarkish(): Boolean =
    (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

private fun AppTheme.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colorPrimary,
        onPrimary = colorOnPrimary,
        primaryContainer = colorPrimary.copy(alpha = 0.14f),
        onPrimaryContainer = colorPrimary,
        secondary = colorSecondary,
        onSecondary = colorOnPrimary,
        tertiary = colorAccent,
        onTertiary = colorOnPrimary,
        background = colorBackground,
        onBackground = colorOnBackground,
        surface = colorSurface,
        onSurface = colorOnSurface,
        surfaceVariant = colorSurfaceVariant,
        onSurfaceVariant = colorOnSurface.copy(alpha = 0.75f),
        outline = colorOutline,
        outlineVariant = colorBorder,
        error = colorError,
        onError = colorOnPrimary,
        scrim = colorOnBackground.copy(alpha = 0.32f),
    )
}

private fun AppTheme.toTypography(): Typography {
    // letterSpacing must be set explicitly on every base style. A TextStyle that leaves it
    // Unspecified propagates through .copy() into MaterialTheme.typography, and any call site
    // doing arithmetic on it (e.g. scaling it up for a PIN field) throws
    // IllegalArgumentException("Cannot perform operation for Unspecified type") at composition.
    val heading = TextStyle(
        fontFamily = headingFontFamily,
        fontWeight = headingFontWeight,
        letterSpacing = 0.sp,
    )
    val body = TextStyle(
        fontFamily = bodyFontFamily,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp,
    )
    val label = TextStyle(
        fontFamily = labelFontFamily,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    )
    // Heading sizes step down proportionally from the theme's headingFontSize so a theme like
    // TYPOGRAPHIC (48sp) scales its whole hierarchy without needing per-style overrides.
    val h = headingFontSize.value
    return Typography(
        displayLarge = heading.copy(fontSize = (h * 2.0f).sp),
        displayMedium = heading.copy(fontSize = (h * 1.7f).sp),
        displaySmall = heading.copy(fontSize = (h * 1.4f).sp),
        headlineLarge = heading.copy(fontSize = (h * 1.3f).sp),
        headlineMedium = heading.copy(fontSize = (h * 1.15f).sp),
        headlineSmall = heading.copy(fontSize = h.sp),
        titleLarge = heading.copy(fontSize = (h * 0.85f).sp),
        titleMedium = heading.copy(fontSize = (h * 0.7f).sp),
        titleSmall = heading.copy(fontSize = (h * 0.62f).sp),
        bodyLarge = body.copy(fontSize = (bodyFontSize.value * 1.15f).sp),
        bodyMedium = body.copy(fontSize = bodyFontSize),
        bodySmall = body.copy(fontSize = (bodyFontSize.value * 0.88f).sp),
        labelLarge = label.copy(fontSize = (labelFontSize.value * 1.15f).sp),
        labelMedium = label.copy(fontSize = labelFontSize),
        labelSmall = label.copy(fontSize = (labelFontSize.value * 0.85f).sp),
    )
}
