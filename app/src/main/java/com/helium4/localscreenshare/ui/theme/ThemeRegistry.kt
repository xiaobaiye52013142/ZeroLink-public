package com.helium4.localscreenshare.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every theme the app can wear, keyed by the string published in the `active_theme`
 * Remote Config parameter. Each id resolves to a light and a dark variant.
 */
object ThemeRegistry {

    const val DEFAULT = "default"
    const val FLAT = "flat"
    const val MATERIAL = "material"
    const val NEUMORPHISM = "neumorphism"
    const val GLASSMORPHISM = "glassmorphism"
    const val MINIMALISM = "minimalism"
    const val BRUTALISM = "brutalism"
    const val CLAYMORPHISM = "claymorphism"
    const val DARK_UI = "dark_ui"
    const val RETRO_Y2K = "retro_y2k"
    const val AURORA_UI = "aurora_ui"
    const val BENTO_GRID = "bento_grid"
    const val SWISS_STYLE = "swiss_style"
    const val MEMPHIS = "memphis"
    const val CYBERPUNK = "cyberpunk"
    const val ORGANIC = "organic"
    const val MAXIMALISM = "maximalism"
    const val TYPOGRAPHIC = "typographic"
    const val SKEUOMORPHISM = "skeuomorphism"
    const val CHRISTMAS = "christmas"
    const val EASTER = "easter"
    const val EID = "eid"

    /** Every id, in the order the debug switcher cycles them. */
    val allThemeIds = listOf(
        DEFAULT, FLAT, MATERIAL, NEUMORPHISM, GLASSMORPHISM, MINIMALISM, BRUTALISM,
        CLAYMORPHISM, DARK_UI, RETRO_Y2K, AURORA_UI, BENTO_GRID, SWISS_STYLE,
        MEMPHIS, CYBERPUNK, ORGANIC, MAXIMALISM, TYPOGRAPHIC, SKEUOMORPHISM,
        CHRISTMAS, EASTER, EID,
    )

    /** Unknown ids fall back to [DEFAULT] so a typo in Remote Config can't break the app. */
    fun resolve(id: String, dark: Boolean): AppTheme = when (id.lowercase().trim()) {
        FLAT -> if (dark) flatDark else flatLight
        MATERIAL -> if (dark) materialDark else materialLight
        NEUMORPHISM -> if (dark) neumorphismDark else neumorphismLight
        GLASSMORPHISM -> if (dark) glassmorphismDark else glassmorphismLight
        MINIMALISM -> if (dark) minimalismDark else minimalismLight
        BRUTALISM -> if (dark) brutalismDark else brutalismLight
        CLAYMORPHISM -> if (dark) claymorphismDark else claymorphismLight
        DARK_UI -> if (dark) darkUiDark else darkUiLight
        RETRO_Y2K -> if (dark) retroY2kDark else retroY2kLight
        AURORA_UI -> if (dark) auroraDark else auroraLight
        BENTO_GRID -> if (dark) bentoDark else bentoLight
        SWISS_STYLE -> if (dark) swissDark else swissLight
        MEMPHIS -> if (dark) memphisDark else memphisLight
        CYBERPUNK -> if (dark) cyberpunkDark else cyberpunkLight
        ORGANIC -> if (dark) organicDark else organicLight
        MAXIMALISM -> if (dark) maximalismDark else maximalismLight
        TYPOGRAPHIC -> if (dark) typographicDark else typographicLight
        SKEUOMORPHISM -> if (dark) skeuomorphismDark else skeuomorphismLight
        CHRISTMAS -> if (dark) christmasDark else christmasLight
        EASTER -> if (dark) easterDark else easterLight
        EID -> if (dark) eidDark else eidLight
        else -> if (dark) defaultDark else defaultLight
    }

    // ─────────────────────────────────────────────────────────── DEFAULT ────
    val defaultLight = AppTheme(
        name = DEFAULT, isDark = false,
        colorPrimary = Color(0xFF6750A4), colorSecondary = Color(0xFF625B71),
        colorBackground = Color(0xFFFFFBFE), colorSurface = Color(0xFFFFFBFE),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF1C1B1F),
        colorAccent = Color(0xFF7D5260),
        colorSurfaceVariant = Color(0xFFE7E0EC),
        cornerRadiusSmall = 4.dp, cornerRadiusMedium = 12.dp, cornerRadiusLarge = 28.dp,
        cardElevation = 1.dp,
    )
    val defaultDark = defaultLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFD0BCFF), colorSecondary = Color(0xFFCCC2DC),
        colorBackground = Color(0xFF1C1B1F), colorSurface = Color(0xFF1C1B1F),
        colorOnPrimary = Color(0xFF381E72), colorOnBackground = Color(0xFFE6E1E5),
        colorAccent = Color(0xFFEFB8C8),
        colorSurfaceVariant = Color(0xFF49454F),
        colorOnSurface = Color(0xFFE6E1E5),
        colorBorder = Color(0xFFE6E1E5).copy(alpha = 0.12f),
        cardBackgroundColor = Color(0xFF1C1B1F),
        dialogBackground = Color(0xFF1C1B1F),
        inputFieldBackground = Color(0xFF1C1B1F),
        snackbarBackground = Color(0xFF49454F),
        snackbarTextColor = Color(0xFFE6E1E5),
        statusBarColor = Color(0xFF1C1B1F), navigationBarColor = Color(0xFF1C1B1F),
        topAppBarColor = Color(0xFF1C1B1F), topAppBarContentColor = Color(0xFFE6E1E5),
    )

    // ────────────────────────────────────────────────────────────── FLAT ────
    val flatLight = AppTheme(
        name = FLAT, isDark = false,
        colorPrimary = Color(0xFF2196F3), colorSecondary = Color(0xFFFF5722),
        colorBackground = Color(0xFFFAFAFA), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF212121),
        colorAccent = Color(0xFFFF9800),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Bold,
        cornerRadiusSmall = 0.dp, cornerRadiusMedium = 2.dp, cornerRadiusLarge = 4.dp,
        cardElevation = 0.dp, buttonElevation = 0.dp,   // flat design: no shadows anywhere
        animationStyle = AnimationStyle.SUBTLE,
        topAppBarColor = Color(0xFF2196F3), topAppBarContentColor = Color(0xFFFFFFFF),
        statusBarColor = Color(0xFF1976D2),
    )
    val flatDark = flatLight.copy(
        isDark = true,
        colorBackground = Color(0xFF212121), colorSurface = Color(0xFF2C2C2C),
        colorOnBackground = Color(0xFFFAFAFA), colorOnSurface = Color(0xFFFAFAFA),
        colorBorder = Color(0xFFFAFAFA).copy(alpha = 0.12f),
        cardBackgroundColor = Color(0xFF2C2C2C),
        dialogBackground = Color(0xFF2C2C2C),
        inputFieldBackground = Color(0xFF2C2C2C),
        snackbarBackground = Color(0xFF3A3A3A),
        snackbarTextColor = Color(0xFFFAFAFA),
        statusBarColor = Color(0xFF1565C0),
    )

    // ────────────────────────────────────────────────────────── MATERIAL ────
    val materialLight = defaultLight.copy(
        name = MATERIAL,
        cardElevation = 2.dp, buttonElevation = 1.dp,
        cornerRadiusLarge = 28.dp,
        animationStyle = AnimationStyle.PLAYFUL,
        iconStyle = IconStyle.ROUNDED,
    )
    val materialDark = defaultDark.copy(
        name = MATERIAL,
        cardElevation = 2.dp, buttonElevation = 1.dp,
        cornerRadiusLarge = 28.dp,
        animationStyle = AnimationStyle.PLAYFUL,
        iconStyle = IconStyle.ROUNDED,
    )

    // ─────────────────────────────────────────────────────── NEUMORPHISM ────
    val neumorphismLight = AppTheme(
        name = NEUMORPHISM, isDark = false,
        colorPrimary = Color(0xFF6E8EAD), colorSecondary = Color(0xFF8FA5BC),
        colorBackground = Color(0xFFE0E5EC), colorSurface = Color(0xFFE0E5EC),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF5C6B7A),
        colorAccent = Color(0xFF6E8EAD),
        colorBorder = Color.Transparent,                // depth comes from shadows, never borders
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Light,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 12.dp, cornerRadiusLarge = 16.dp,
        cardElevation = 0.dp, buttonElevation = 0.dp,   // shadow replaces elevation entirely
        borderWidth = 0.dp,
        buttonStyle = ButtonStyle.GHOST,
        spacingDensity = SpacingDensity.COMFORTABLE,
        inputFieldBackground = Color(0xFFE0E5EC),
        cardBackgroundColor = Color(0xFFE0E5EC),
        cardBorderColor = Color.Transparent,
        dividerColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
    )
    val neumorphismDark = neumorphismLight.copy(
        isDark = true,
        colorPrimary = Color(0xFF7A9CC6), colorSecondary = Color(0xFF5A7A9A),
        colorBackground = Color(0xFF2D3748), colorSurface = Color(0xFF2D3748),
        colorOnBackground = Color(0xFFCBD5E0), colorOnSurface = Color(0xFFCBD5E0),
        inputFieldBackground = Color(0xFF2D3748),
        cardBackgroundColor = Color(0xFF2D3748),
        dividerColor = Color(0xFF1A202C).copy(alpha = 0.6f),
        statusBarColor = Color(0xFF2D3748), navigationBarColor = Color(0xFF2D3748),
        topAppBarColor = Color(0xFF2D3748), topAppBarContentColor = Color(0xFFCBD5E0),
    )

    /** Shadow pair for the neumorphic double-shadow effect, light then dark. */
    fun neumorphicShadows(dark: Boolean): Pair<Color, Color> =
        if (dark) Color(0xFF4A5568) to Color(0xFF1A202C)
        else Color.White.copy(alpha = 0.70f) to Color(0xFFA3B1C6).copy(alpha = 0.50f)

    // ────────────────────────────────────────────────────── GLASSMORPHISM ───
    val glassmorphismLight = AppTheme(
        name = GLASSMORPHISM, isDark = true,   // content is light-on-glass even in "light" mode
        colorPrimary = Color(0xFFFFFFFF), colorSecondary = Color(0xFFE0E7FF),
        colorBackground = Color(0xFF667EEA), colorSurface = Color(0x26FFFFFF),
        colorOnPrimary = Color(0xFF667EEA), colorOnBackground = Color(0xFFFFFFFF),
        colorAccent = Color(0xFFA5B4FC),
        colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0x4DFFFFFF),                 // white @ 30%
        backgroundGradient = listOf(Color(0xFF667EEA), Color(0xFF764BA2)),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Light,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 16.dp, cornerRadiusLarge = 24.dp,
        cardElevation = 0.dp, borderWidth = 1.dp,
        buttonStyle = ButtonStyle.OUTLINED,
        animationStyle = AnimationStyle.SUBTLE,
        cardBackgroundColor = Color(0x26FFFFFF), cardBorderColor = Color(0x4DFFFFFF),
        inputFieldBackground = Color(0x1AFFFFFF), inputFieldBorderColor = Color(0x4DFFFFFF),
        inputFieldTextColor = Color(0xFFFFFFFF),
        statusBarColor = Color(0xFF667EEA), navigationBarColor = Color(0xFF764BA2),
        topAppBarColor = Color.Transparent, topAppBarContentColor = Color(0xFFFFFFFF),
        dividerColor = Color(0x33FFFFFF),
    )
    val glassmorphismDark = glassmorphismLight.copy(
        colorBackground = Color(0xFF1A1A2E),
        backgroundGradient = listOf(Color(0xFF1A1A2E), Color(0xFF16213E)),
        statusBarColor = Color(0xFF1A1A2E), navigationBarColor = Color(0xFF16213E),
    )

    // ───────────────────────────────────────────────────────── MINIMALISM ───
    val minimalismLight = AppTheme(
        name = MINIMALISM, isDark = false,
        colorPrimary = Color(0xFF000000), colorSecondary = Color(0xFF767676),
        colorBackground = Color(0xFFFFFFFF), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF000000),
        colorAccent = Color(0xFF0066FF),                 // the one permitted accent, CTAs only
        colorBorder = Color(0xFFE5E5E5),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Thin, headingFontSize = 28.sp,
        cornerRadiusSmall = 0.dp, cornerRadiusMedium = 0.dp, cornerRadiusLarge = 0.dp,
        cardElevation = 0.dp, buttonElevation = 0.dp,
        buttonStyle = ButtonStyle.OUTLINED,
        spacingDensity = SpacingDensity.COMFORTABLE,     // maximum whitespace
        animationStyle = AnimationStyle.SUBTLE,
        dividerThickness = 0.5.dp, dividerColor = Color(0xFFE5E5E5),
        cardBorderColor = Color(0xFFE5E5E5),
    )
    val minimalismDark = minimalismLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFFFFFFF), colorSecondary = Color(0xFF999999),
        colorBackground = Color(0xFF000000), colorSurface = Color(0xFF111111),
        colorOnPrimary = Color(0xFF000000), colorOnBackground = Color(0xFFFFFFFF),
        colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0xFF2A2A2A), cardBorderColor = Color(0xFF2A2A2A),
        dividerColor = Color(0xFF2A2A2A),
        cardBackgroundColor = Color(0xFF111111),
        dialogBackground = Color(0xFF111111),
        inputFieldBackground = Color(0xFF111111),
        snackbarBackground = Color(0xFF1A1A1A),
        snackbarTextColor = Color(0xFFFFFFFF),
        statusBarColor = Color(0xFF000000), navigationBarColor = Color(0xFF000000),
        topAppBarColor = Color(0xFF000000), topAppBarContentColor = Color(0xFFFFFFFF),
    )

    // ────────────────────────────────────────────────────────── BRUTALISM ───
    val brutalismLight = AppTheme(
        name = BRUTALISM, isDark = false,
        colorPrimary = Color(0xFF000000), colorSecondary = Color(0xFFFFFFFF),
        colorBackground = Color(0xFFFFFFFF), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF000000),
        colorAccent = Color(0xFFFFD700),
        colorBorder = Color(0xFF000000),
        headingFontFamily = FontFamily.Monospace, bodyFontFamily = FontFamily.Monospace,
        headingFontWeight = FontWeight.Black,
        cornerRadiusSmall = 0.dp, cornerRadiusMedium = 0.dp, cornerRadiusLarge = 0.dp,
        cardElevation = 0.dp, buttonElevation = 0.dp,
        borderWidth = 2.dp,                              // every border is a hard 2dp rule
        buttonStyle = ButtonStyle.OUTLINED,
        animationStyle = AnimationStyle.NONE,            // brutalism does not animate
        spacingDensity = SpacingDensity.COMPACT,
        cardBorderColor = Color(0xFF000000), dividerColor = Color(0xFF000000),
        dividerThickness = 2.dp,
        inputFieldBorderColor = Color(0xFF000000),
        topAppBarColor = Color(0xFFFFFFFF), topAppBarContentColor = Color(0xFF000000),
    )
    val brutalismDark = brutalismLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFFFFFFF), colorSecondary = Color(0xFF000000),
        colorBackground = Color(0xFF000000), colorSurface = Color(0xFF000000),
        colorOnPrimary = Color(0xFF000000), colorOnBackground = Color(0xFFFFFFFF),
        colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0xFFFFFFFF), cardBorderColor = Color(0xFFFFFFFF),
        dividerColor = Color(0xFFFFFFFF), inputFieldBorderColor = Color(0xFFFFFFFF),
        cardBackgroundColor = Color(0xFF000000),
        dialogBackground = Color(0xFF000000),
        inputFieldBackground = Color(0xFF000000),
        statusBarColor = Color(0xFF000000), navigationBarColor = Color(0xFF000000),
        topAppBarColor = Color(0xFF000000), topAppBarContentColor = Color(0xFFFFFFFF),
    )

    /** Brutalism draws a hard, un-blurred offset shadow instead of a soft elevation. */
    fun brutalistShadowOffset() = 4.dp

    // ──────────────────────────────────────────────────────── CLAYMORPHISM ──
    val claymorphismLight = AppTheme(
        name = CLAYMORPHISM, isDark = false,
        colorPrimary = Color(0xFFFF6B9D), colorSecondary = Color(0xFFC77DFF),
        colorBackground = Color(0xFFF0F4FF), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF4A4458),
        colorAccent = Color(0xFF9BB1FF),
        colorBorder = Color.Transparent,
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Bold,
        cornerRadiusSmall = 12.dp, cornerRadiusMedium = 18.dp, cornerRadiusLarge = 24.dp,
        buttonCornerRadius = 50.dp,                      // pill buttons
        cardElevation = 0.dp,                            // the coloured shadow does the work
        borderWidth = 0.dp,
        spacingDensity = SpacingDensity.COMFORTABLE,
        animationStyle = AnimationStyle.PLAYFUL,
        iconStyle = IconStyle.ROUNDED,
        cardBorderColor = Color.Transparent,
        inputFieldBackground = Color(0xFFF7F9FF),
        dividerColor = Color(0xFFE4E9F7),
    )
    val claymorphismDark = claymorphismLight.copy(
        isDark = true,
        colorBackground = Color(0xFF1A1A2E), colorSurface = Color(0xFF2D2D44),
        colorOnBackground = Color(0xFFE8E4F0), colorOnSurface = Color(0xFFE8E4F0),
        inputFieldBackground = Color(0xFF35354F),
        dividerColor = Color(0xFF3A3A55),
        cardBackgroundColor = Color(0xFF2D2D44),
        dialogBackground = Color(0xFF2D2D44),
        snackbarBackground = Color(0xFF3A3A55),
        snackbarTextColor = Color(0xFFE8E4F0),
        statusBarColor = Color(0xFF1A1A2E), navigationBarColor = Color(0xFF1A1A2E),
        topAppBarColor = Color(0xFF1A1A2E), topAppBarContentColor = Color(0xFFE8E4F0),
    )

    // ─────────────────────────────────────────────────────────── DARK_UI ────
    val darkUiDark = AppTheme(
        name = DARK_UI, isDark = true,
        colorPrimary = Color(0xFFBB86FC), colorSecondary = Color(0xFF03DAC6),
        colorBackground = Color(0xFF0D0D0D), colorSurface = Color(0xFF1A1A1A),
        colorOnPrimary = Color(0xFF000000), colorOnBackground = Color(0xFFFFFFFF),
        colorAccent = Color(0xFFCF6679),
        colorSurfaceVariant = Color(0xFF242424),
        colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0xFF2A2A2A),
        cardElevation = 0.dp,                            // depth via surface colour, not shadow
        cardBackgroundColor = Color(0xFF1A1A1A), cardBorderColor = Color(0xFF2A2A2A),
        inputFieldBackground = Color(0xFF242424), inputFieldBorderColor = Color(0xFF2A2A2A),
        dividerColor = Color(0xFF2A2A2A),
        statusBarColor = Color(0xFF0D0D0D), navigationBarColor = Color(0xFF0D0D0D),
        topAppBarColor = Color(0xFF0D0D0D), topAppBarContentColor = Color(0xFFFFFFFF),
    )
    val darkUiLight = darkUiDark.copy(
        isDark = false,
        colorPrimary = Color(0xFF6200EE), colorSecondary = Color(0xFF018786),
        colorBackground = Color(0xFFF5F5F5), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF1A1A1A),
        colorSurfaceVariant = Color(0xFFEEEEEE), colorOnSurface = Color(0xFF1A1A1A),
        colorBorder = Color(0xFFE0E0E0),
        cardBackgroundColor = Color(0xFFFFFFFF), cardBorderColor = Color(0xFFE0E0E0),
        inputFieldBackground = Color(0xFFF5F5F5), inputFieldBorderColor = Color(0xFFE0E0E0),
        dividerColor = Color(0xFFE0E0E0),
        statusBarColor = Color(0xFFF5F5F5), navigationBarColor = Color(0xFFF5F5F5),
        topAppBarColor = Color(0xFFF5F5F5), topAppBarContentColor = Color(0xFF1A1A1A),
    )

    // ───────────────────────────────────────────────────────── RETRO_Y2K ────
    val retroY2kLight = AppTheme(
        name = RETRO_Y2K, isDark = false,
        colorPrimary = Color(0xFFFF1493), colorSecondary = Color(0xFF00CED1),
        colorBackground = Color(0xFFFF00FF), colorSurface = Color(0xFFFFE4FF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF2D0033),
        colorAccent = Color(0xFFFFD700),
        colorOnSurface = Color(0xFF2D0033),
        backgroundGradient = listOf(Color(0xFFFF00FF), Color(0xFF00FFFF)),
        headingFontFamily = FontFamily.Serif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Black,
        cornerRadiusSmall = 0.dp, cornerRadiusMedium = 4.dp, cornerRadiusLarge = 4.dp,
        cardElevation = 4.dp, buttonElevation = 4.dp,
        buttonStyle = ButtonStyle.BEVELED,               // 3D bevel: highlight top, shadow bottom
        backgroundPattern = BackgroundPattern.GEOMETRIC,
        animationStyle = AnimationStyle.PLAYFUL,
        decorativeOverlay = OverlayEffect.STARS,
        iconStyle = IconStyle.SHARP,
        statusBarColor = Color(0xFFFF00FF), navigationBarColor = Color(0xFF00FFFF),
        topAppBarColor = Color(0xFFFF1493), topAppBarContentColor = Color(0xFFFFFFFF),
    )
    val retroY2kDark = retroY2kLight.copy(
        isDark = true,
        colorBackground = Color(0xFF4A004A), colorSurface = Color(0xFF3D0040),
        colorOnBackground = Color(0xFFFFE4FF), colorOnSurface = Color(0xFFFFE4FF),
        backgroundGradient = listOf(Color(0xFF4A004A), Color(0xFF004A4A)),
        colorBorder = Color(0xFFFFE4FF).copy(alpha = 0.12f),
        cardBackgroundColor = Color(0xFF3D0040),
        dialogBackground = Color(0xFF3D0040),
        inputFieldBackground = Color(0xFF3D0040),
        snackbarBackground = Color(0xFF5A005A),
        snackbarTextColor = Color(0xFFFFE4FF),
        statusBarColor = Color(0xFF4A004A), navigationBarColor = Color(0xFF004A4A),
    )

    // ───────────────────────────────────────────────────────── AURORA_UI ────
    val auroraDark = AppTheme(
        name = AURORA_UI, isDark = true,
        colorPrimary = Color(0xFFA78BFA), colorSecondary = Color(0xFF3B82F6),
        colorBackground = Color(0xFF0F0B2E), colorSurface = Color(0xCC1E1B4B),
        colorOnPrimary = Color(0xFF1E1B4B), colorOnBackground = Color(0xFFFFFFFF),
        colorAccent = Color(0xFF34D399),
        colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0xFFA78BFA).copy(alpha = 0.35f),
        backgroundGradient = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6), Color(0xFF06B6D4)),
        glowColor = Color(0xFF34D399),                   // primary buttons glow accent-green
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Light,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 16.dp, cornerRadiusLarge = 24.dp,
        cardElevation = 0.dp, borderWidth = 1.dp,
        animationStyle = AnimationStyle.DRAMATIC,
        decorativeOverlay = OverlayEffect.STARS,
        cardBackgroundColor = Color(0xCC1E1B4B),
        cardBorderColor = Color(0xFFA78BFA).copy(alpha = 0.35f),
        inputFieldBackground = Color(0x991E1B4B),
        statusBarColor = Color(0xFF8B5CF6), navigationBarColor = Color(0xFF06B6D4),
        topAppBarColor = Color.Transparent, topAppBarContentColor = Color(0xFFFFFFFF),
    )
    val auroraLight = auroraDark.copy(
        isDark = false,
        colorPrimary = Color(0xFF7C3AED), colorSecondary = Color(0xFF2563EB),
        colorBackground = Color(0xFFEDE9FE), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF1E1B4B),
        colorOnSurface = Color(0xFF1E1B4B),
        backgroundGradient = listOf(Color(0xFFEDE9FE), Color(0xFFDBEAFE)),
        cardBackgroundColor = Color(0xFFFFFFFF),
        inputFieldBackground = Color(0xFFF5F3FF),
        statusBarColor = Color(0xFFEDE9FE), navigationBarColor = Color(0xFFDBEAFE),
        topAppBarContentColor = Color(0xFF1E1B4B),
    )

    // ──────────────────────────────────────────────────────── BENTO_GRID ────
    val bentoLight = AppTheme(
        name = BENTO_GRID, isDark = false,
        colorPrimary = Color(0xFF000000), colorSecondary = Color(0xFF666666),
        colorBackground = Color(0xFFF5F5F5), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF000000),
        colorAccent = Color(0xFF0071E3),                 // exactly one bold accent
        colorBorder = Color(0xFFE0E0E0),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Medium,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 12.dp, cornerRadiusLarge = 12.dp,
        cardElevation = 0.dp, borderWidth = 1.dp,
        spacingDensity = SpacingDensity.COMFORTABLE,     // generous padding inside cards
        animationStyle = AnimationStyle.SUBTLE,
        cardBorderColor = Color(0xFFE0E0E0), dividerColor = Color(0xFFE0E0E0),
        topAppBarColor = Color(0xFFF5F5F5), topAppBarContentColor = Color(0xFF000000),
    )
    val bentoDark = bentoLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFFFFFFF), colorSecondary = Color(0xFF999999),
        colorBackground = Color(0xFF000000), colorSurface = Color(0xFF1C1C1E),
        colorOnPrimary = Color(0xFF000000), colorOnBackground = Color(0xFFFFFFFF),
        colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0xFF2C2C2E), cardBorderColor = Color(0xFF2C2C2E),
        dividerColor = Color(0xFF2C2C2E),
        cardBackgroundColor = Color(0xFF1C1C1E),
        dialogBackground = Color(0xFF1C1C1E),
        inputFieldBackground = Color(0xFF1C1C1E),
        snackbarBackground = Color(0xFF2C2C2E),
        snackbarTextColor = Color(0xFFFFFFFF),
        statusBarColor = Color(0xFF000000), navigationBarColor = Color(0xFF000000),
        topAppBarColor = Color(0xFF000000), topAppBarContentColor = Color(0xFFFFFFFF),
    )

    // ─────────────────────────────────────────────────────── SWISS_STYLE ────
    val swissLight = AppTheme(
        name = SWISS_STYLE, isDark = false,
        colorPrimary = Color(0xFFFF0000), colorSecondary = Color(0xFF000000),
        colorBackground = Color(0xFFFFFFFF), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF000000),
        colorAccent = Color(0xFFFF0000),
        colorBorder = Color(0xFF000000),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Black, headingFontSize = 34.sp,
        cornerRadiusSmall = 0.dp, cornerRadiusMedium = 0.dp, cornerRadiusLarge = 0.dp,
        cardElevation = 0.dp, buttonElevation = 0.dp,
        animationStyle = AnimationStyle.NONE,
        dividerThickness = 1.dp, dividerColor = Color(0xFF000000),
        cardBorderColor = Color(0xFF000000),
        topAppBarColor = Color(0xFFFFFFFF), topAppBarContentColor = Color(0xFF000000),
    )
    val swissDark = swissLight.copy(
        isDark = true,
        colorSecondary = Color(0xFFFFFFFF),
        colorBackground = Color(0xFF000000), colorSurface = Color(0xFF000000),
        colorOnBackground = Color(0xFFFFFFFF), colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0xFFFFFFFF), cardBorderColor = Color(0xFFFFFFFF),
        dividerColor = Color(0xFFFFFFFF),
        cardBackgroundColor = Color(0xFF000000),
        dialogBackground = Color(0xFF000000),
        inputFieldBackground = Color(0xFF000000),
        statusBarColor = Color(0xFF000000), navigationBarColor = Color(0xFF000000),
        topAppBarColor = Color(0xFF000000), topAppBarContentColor = Color(0xFFFFFFFF),
    )

    // ─────────────────────────────────────────────────────────── MEMPHIS ────
    val memphisLight = AppTheme(
        name = MEMPHIS, isDark = false,
        colorPrimary = Color(0xFFFF6B6B), colorSecondary = Color(0xFF4ECDC4),
        colorBackground = Color(0xFFFFF9E6), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF2D2D2D),
        colorAccent = Color(0xFFFFE66D),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Black,
        cornerRadiusSmall = 4.dp, cornerRadiusMedium = 8.dp, cornerRadiusLarge = 12.dp,
        cardElevation = 0.dp,                            // contrasting hard shadows instead
        backgroundPattern = BackgroundPattern.GEOMETRIC,
        animationStyle = AnimationStyle.PLAYFUL,
        iconStyle = IconStyle.SHARP,
        cardBorderColor = Color(0xFF2D2D2D), borderWidth = 2.dp,
        topAppBarColor = Color(0xFFFF6B6B), topAppBarContentColor = Color(0xFFFFFFFF),
        statusBarColor = Color(0xFFFF6B6B),
    )
    val memphisDark = memphisLight.copy(
        isDark = true,
        colorBackground = Color(0xFF2D2D2D), colorSurface = Color(0xFF3A3A3A),
        colorOnBackground = Color(0xFFFFF9E6), colorOnSurface = Color(0xFFFFF9E6),
        colorBorder = Color(0xFFFFF9E6).copy(alpha = 0.20f),
        cardBorderColor = Color(0xFFFFF9E6),
        cardBackgroundColor = Color(0xFF3A3A3A),
        dialogBackground = Color(0xFF3A3A3A),
        inputFieldBackground = Color(0xFF3A3A3A),
        snackbarBackground = Color(0xFF4A4A4A),
        snackbarTextColor = Color(0xFFFFF9E6),
        statusBarColor = Color(0xFF2D2D2D), navigationBarColor = Color(0xFF2D2D2D),
    )

    // ───────────────────────────────────────────────────────── CYBERPUNK ────
    val cyberpunkDark = AppTheme(
        name = CYBERPUNK, isDark = true,
        colorPrimary = Color(0xFF00FFFF), colorSecondary = Color(0xFFFF00FF),
        colorBackground = Color(0xFF0A0A0F), colorSurface = Color(0xFF12121A),
        colorOnPrimary = Color(0xFF0A0A0F), colorOnBackground = Color(0xFF00FFFF),
        colorAccent = Color(0xFF39FF14),
        colorOnSurface = Color(0xFF00FFFF),
        colorBorder = Color(0xFF00FFFF),
        glowColor = Color(0xFF00FFFF),
        headingFontFamily = FontFamily.Monospace, bodyFontFamily = FontFamily.Monospace,
        labelFontFamily = FontFamily.Monospace,
        headingFontWeight = FontWeight.Bold,
        cornerRadiusSmall = 0.dp, cornerRadiusMedium = 2.dp, cornerRadiusLarge = 2.dp,
        cardElevation = 0.dp, borderWidth = 1.dp,
        buttonStyle = ButtonStyle.OUTLINED,
        backgroundPattern = BackgroundPattern.SCANLINE,
        animationStyle = AnimationStyle.DRAMATIC,
        iconStyle = IconStyle.SHARP,
        cardBackgroundColor = Color(0xFF12121A), cardBorderColor = Color(0xFF00FFFF),
        inputFieldBackground = Color(0xFF12121A), inputFieldBorderColor = Color(0xFF00FFFF),
        inputFieldTextColor = Color(0xFF00FFFF),
        dividerColor = Color(0xFF00FFFF).copy(alpha = 0.4f),
        statusBarColor = Color(0xFF0A0A0F), navigationBarColor = Color(0xFF0A0A0F),
        topAppBarColor = Color(0xFF0A0A0F), topAppBarContentColor = Color(0xFF00FFFF),
    )
    val cyberpunkLight = cyberpunkDark.copy(
        isDark = false,
        colorPrimary = Color(0xFF0057B8), colorSecondary = Color(0xFFB800B8),
        colorBackground = Color(0xFFF0F8FF), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF0057B8),
        colorOnSurface = Color(0xFF0057B8), colorAccent = Color(0xFF00994D),
        colorBorder = Color(0xFF0057B8), glowColor = Color(0xFF0057B8),
        cardBackgroundColor = Color(0xFFFFFFFF), cardBorderColor = Color(0xFF0057B8),
        inputFieldBackground = Color(0xFFFFFFFF), inputFieldBorderColor = Color(0xFF0057B8),
        inputFieldTextColor = Color(0xFF0057B8),
        dividerColor = Color(0xFF0057B8).copy(alpha = 0.4f),
        statusBarColor = Color(0xFFF0F8FF), navigationBarColor = Color(0xFFF0F8FF),
        topAppBarColor = Color(0xFFF0F8FF), topAppBarContentColor = Color(0xFF0057B8),
    )

    // ─────────────────────────────────────────────────────────── ORGANIC ────
    val organicLight = AppTheme(
        name = ORGANIC, isDark = false,
        colorPrimary = Color(0xFF5C7A4E), colorSecondary = Color(0xFF8B6F47),
        colorBackground = Color(0xFFF5F0E8), colorSurface = Color(0xFFFAF7F2),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF3D3A32),
        colorAccent = Color(0xFFD4956A),
        colorBorder = Color(0xFFE0D8C8),
        headingFontFamily = FontFamily.Serif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Normal,
        cornerRadiusSmall = 10.dp, cornerRadiusMedium = 16.dp, cornerRadiusLarge = 20.dp,
        cardElevation = 2.dp,
        backgroundPattern = BackgroundPattern.ORGANIC,
        animationStyle = AnimationStyle.SUBTLE,
        iconStyle = IconStyle.ROUNDED,
        spacingDensity = SpacingDensity.COMFORTABLE,
        decorativeOverlay = OverlayEffect.FLOWERS,
        cardBorderColor = Color(0xFFE0D8C8), dividerColor = Color(0xFFE0D8C8),
        topAppBarColor = Color(0xFFF5F0E8), topAppBarContentColor = Color(0xFF3D3A32),
    )
    val organicDark = organicLight.copy(
        isDark = true,
        colorPrimary = Color(0xFF7EA86D), colorSecondary = Color(0xFFA98C64),
        colorBackground = Color(0xFF1C1A16), colorSurface = Color(0xFF252219),
        colorOnBackground = Color(0xFFE8E2D5), colorOnSurface = Color(0xFFE8E2D5),
        colorBorder = Color(0xFF3A362C), cardBorderColor = Color(0xFF3A362C),
        dividerColor = Color(0xFF3A362C),
        cardBackgroundColor = Color(0xFF252219),
        dialogBackground = Color(0xFF252219),
        inputFieldBackground = Color(0xFF2A2720),
        snackbarBackground = Color(0xFF3A362C),
        snackbarTextColor = Color(0xFFE8E2D5),
        statusBarColor = Color(0xFF1C1A16), navigationBarColor = Color(0xFF1C1A16),
        topAppBarColor = Color(0xFF1C1A16), topAppBarContentColor = Color(0xFFE8E2D5),
    )

    // ──────────────────────────────────────────────────────── MAXIMALISM ────
    val maximalismDark = AppTheme(
        name = MAXIMALISM, isDark = true,
        colorPrimary = Color(0xFFFFD700), colorSecondary = Color(0xFFFF1493),
        colorBackground = Color(0xFF1B1464), colorSurface = Color(0xFF2A1F7A),
        colorOnPrimary = Color(0xFF1B1464), colorOnBackground = Color(0xFFFFF0D0),
        colorAccent = Color(0xFF00E5A0),
        colorOnSurface = Color(0xFFFFF0D0),
        colorBorder = Color(0xFFFFD700),
        headingFontFamily = FontFamily.Serif, bodyFontFamily = FontFamily.Serif,
        headingFontWeight = FontWeight.Black,
        cornerRadiusSmall = 6.dp, cornerRadiusMedium = 12.dp, cornerRadiusLarge = 18.dp,
        cardElevation = 8.dp, borderWidth = 2.dp,
        backgroundPattern = BackgroundPattern.GEOMETRIC,
        animationStyle = AnimationStyle.DRAMATIC,
        spacingDensity = SpacingDensity.COMPACT,         // dense, everything decorated
        decorativeOverlay = OverlayEffect.STARS,
        cardBackgroundColor = Color(0xFF2A1F7A), cardBorderColor = Color(0xFFFFD700),
        dividerColor = Color(0xFFFFD700),
        statusBarColor = Color(0xFF1B1464), navigationBarColor = Color(0xFF1B1464),
        topAppBarColor = Color(0xFF800020), topAppBarContentColor = Color(0xFFFFD700),
    )
    val maximalismLight = maximalismDark.copy(
        isDark = false,
        colorBackground = Color(0xFF800020), colorSurface = Color(0xFF9A0F2E),
        colorOnBackground = Color(0xFFFFF0D0), colorOnSurface = Color(0xFFFFF0D0),
        cardBackgroundColor = Color(0xFF9A0F2E),
        statusBarColor = Color(0xFF800020), navigationBarColor = Color(0xFF800020),
        topAppBarColor = Color(0xFF1B1464),
    )

    // ─────────────────────────────────────────────────────── TYPOGRAPHIC ────
    val typographicLight = AppTheme(
        name = TYPOGRAPHIC, isDark = false,
        colorPrimary = Color(0xFF000000), colorSecondary = Color(0xFF555555),
        colorBackground = Color(0xFFFFFFFF), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF000000),
        colorAccent = Color(0xFFFF0000),                 // a single red highlight, used sparingly
        colorBorder = Color(0xFF000000),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Black,
        headingFontSize = 48.sp, bodyFontSize = 14.sp, labelFontSize = 11.sp,
        cornerRadiusSmall = 0.dp, cornerRadiusMedium = 0.dp, cornerRadiusLarge = 0.dp,
        cardElevation = 0.dp, buttonElevation = 0.dp,
        buttonStyle = ButtonStyle.GHOST,
        animationStyle = AnimationStyle.NONE,
        spacingDensity = SpacingDensity.COMFORTABLE,
        dividerThickness = 1.dp, dividerColor = Color(0xFF000000),
        cardBorderColor = Color(0xFF000000),
        topAppBarColor = Color(0xFFFFFFFF), topAppBarContentColor = Color(0xFF000000),
    )
    val typographicDark = typographicLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFFFFFFF), colorSecondary = Color(0xFFAAAAAA),
        colorBackground = Color(0xFF000000), colorSurface = Color(0xFF000000),
        colorOnPrimary = Color(0xFF000000), colorOnBackground = Color(0xFFFFFFFF),
        colorOnSurface = Color(0xFFFFFFFF),
        colorBorder = Color(0xFFFFFFFF), cardBorderColor = Color(0xFFFFFFFF),
        dividerColor = Color(0xFFFFFFFF),
        cardBackgroundColor = Color(0xFF000000),
        dialogBackground = Color(0xFF000000),
        inputFieldBackground = Color(0xFF000000),
        statusBarColor = Color(0xFF000000), navigationBarColor = Color(0xFF000000),
        topAppBarColor = Color(0xFF000000), topAppBarContentColor = Color(0xFFFFFFFF),
    )

    // ────────────────────────────────────────────────────── SKEUOMORPHISM ───
    val skeuomorphismLight = AppTheme(
        name = SKEUOMORPHISM, isDark = false,
        colorPrimary = Color(0xFF8B4513), colorSecondary = Color(0xFF556B2F),
        colorBackground = Color(0xFFD9CDBF), colorSurface = Color(0xFFF2E8D5),
        colorOnPrimary = Color(0xFFFFF8E7), colorOnBackground = Color(0xFF3B2F22),
        colorAccent = Color(0xFFB8860B),
        colorBorder = Color(0xFFA89078),
        headingFontFamily = FontFamily.Serif, bodyFontFamily = FontFamily.Serif,
        headingFontWeight = FontWeight.Bold,
        cornerRadiusSmall = 6.dp, cornerRadiusMedium = 10.dp, cornerRadiusLarge = 14.dp,
        cardElevation = 8.dp, buttonElevation = 4.dp,    // pronounced, realistic drop shadows
        borderWidth = 1.dp,
        buttonStyle = ButtonStyle.BEVELED,               // gradient highlight + emboss
        backgroundPattern = BackgroundPattern.NOISE,     // linen / leather texture
        iconStyle = IconStyle.FILLED,
        animationStyle = AnimationStyle.SUBTLE,
        cardBackgroundColor = Color(0xFFF2E8D5), cardBorderColor = Color(0xFFA89078),
        inputFieldBackground = Color(0xFFE8DCC5), inputFieldBorderColor = Color(0xFFA89078),
        dividerColor = Color(0xFFA89078),
        statusBarColor = Color(0xFF8B4513), navigationBarColor = Color(0xFFD9CDBF),
        topAppBarColor = Color(0xFF8B4513), topAppBarContentColor = Color(0xFFFFF8E7),
    )
    val skeuomorphismDark = skeuomorphismLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFB5723A), colorSecondary = Color(0xFF7A9350),
        colorBackground = Color(0xFF2C1810), colorSurface = Color(0xFF3D2317),
        colorOnBackground = Color(0xFFEAD9C0), colorOnSurface = Color(0xFFEAD9C0),
        colorBorder = Color(0xFF5A3D2B),
        cardBackgroundColor = Color(0xFF3D2317), cardBorderColor = Color(0xFF5A3D2B),
        inputFieldBackground = Color(0xFF33200F), inputFieldBorderColor = Color(0xFF5A3D2B),
        dividerColor = Color(0xFF5A3D2B),
        statusBarColor = Color(0xFF2C1810), navigationBarColor = Color(0xFF2C1810),
        topAppBarColor = Color(0xFF2C1810), topAppBarContentColor = Color(0xFFEAD9C0),
    )

    // ───────────────────────────────────────────────────────── CHRISTMAS ────
    val christmasLight = AppTheme(
        name = CHRISTMAS, isDark = false,
        colorPrimary = Color(0xFFC41E3A), colorSecondary = Color(0xFF165B33),
        colorBackground = Color(0xFFFFFFFF), colorSurface = Color(0xFFFFF8F8),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF1E1E1E),
        colorAccent = Color(0xFFFFD700),
        colorBorder = Color(0xFFE8D5D5),
        headingFontFamily = FontFamily.Serif, bodyFontFamily = FontFamily.Default,
        headingFontWeight = FontWeight.Bold,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 14.dp, cornerRadiusLarge = 20.dp,
        cardElevation = 2.dp,
        decorativeOverlay = OverlayEffect.SNOWFLAKES,
        animationStyle = AnimationStyle.PLAYFUL,
        iconStyle = IconStyle.ROUNDED,
        cardBorderColor = Color(0xFFE8D5D5), dividerColor = Color(0xFFE8D5D5),
        chipBackgroundColor = Color(0xFFFFD700).copy(alpha = 0.22f),
        chipTextColor = Color(0xFF8B6914),
        statusBarColor = Color(0xFFC41E3A), navigationBarColor = Color(0xFFFFFFFF),
        topAppBarColor = Color(0xFFC41E3A), topAppBarContentColor = Color(0xFFFFFFFF),
    )
    val christmasDark = christmasLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFE63950), colorSecondary = Color(0xFF2E8B57),
        colorBackground = Color(0xFF0D1B0D), colorSurface = Color(0xFF14261A),
        colorOnBackground = Color(0xFFF0EDE5), colorOnSurface = Color(0xFFF0EDE5),
        colorBorder = Color(0xFF2A3D2A), cardBorderColor = Color(0xFF2A3D2A),
        dividerColor = Color(0xFF2A3D2A),
        cardBackgroundColor = Color(0xFF14261A),
        dialogBackground = Color(0xFF14261A),
        inputFieldBackground = Color(0xFF14261A),
        snackbarBackground = Color(0xFF2A3D2A),
        snackbarTextColor = Color(0xFFF0EDE5),
        statusBarColor = Color(0xFF0D1B0D), navigationBarColor = Color(0xFF0D1B0D),
        topAppBarColor = Color(0xFF0D1B0D), topAppBarContentColor = Color(0xFFF0EDE5),
    )

    // ──────────────────────────────────────────────────────────── EASTER ────
    val easterLight = AppTheme(
        name = EASTER, isDark = false,
        colorPrimary = Color(0xFFC47FBF), colorSecondary = Color(0xFF7DC95E),
        colorBackground = Color(0xFFFFF9FB), colorSurface = Color(0xFFFFFFFF),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF4A3A47),
        colorAccent = Color(0xFFF9E04B),
        colorBorder = Color(0xFFF0E0EE),
        headingFontFamily = FontFamily.SansSerif, bodyFontFamily = FontFamily.SansSerif,
        headingFontWeight = FontWeight.Bold,
        cornerRadiusSmall = 14.dp, cornerRadiusMedium = 22.dp, cornerRadiusLarge = 32.dp,
        buttonCornerRadius = 32.dp,
        cardElevation = 1.dp,
        decorativeOverlay = OverlayEffect.EGGS,
        animationStyle = AnimationStyle.PLAYFUL,
        iconStyle = IconStyle.ROUNDED,
        spacingDensity = SpacingDensity.COMFORTABLE,     // light, airy
        cardBorderColor = Color(0xFFF0E0EE), dividerColor = Color(0xFFF0E0EE),
        chipBackgroundColor = Color(0xFF7DC95E).copy(alpha = 0.20f),
        chipTextColor = Color(0xFF4A7A32),
        statusBarColor = Color(0xFFFFF9FB), navigationBarColor = Color(0xFFFFF9FB),
        topAppBarColor = Color(0xFFFFF9FB), topAppBarContentColor = Color(0xFF4A3A47),
    )
    val easterDark = easterLight.copy(
        isDark = true,
        colorPrimary = Color(0xFFD79BD2), colorSecondary = Color(0xFF95D97A),
        colorBackground = Color(0xFF1A0F1A), colorSurface = Color(0xFF261A26),
        colorOnBackground = Color(0xFFF2E8F0), colorOnSurface = Color(0xFFF2E8F0),
        colorBorder = Color(0xFF3A2A38), cardBorderColor = Color(0xFF3A2A38),
        dividerColor = Color(0xFF3A2A38),
        cardBackgroundColor = Color(0xFF261A26),
        dialogBackground = Color(0xFF261A26),
        inputFieldBackground = Color(0xFF261A26),
        snackbarBackground = Color(0xFF3A2A38),
        snackbarTextColor = Color(0xFFF2E8F0),
        statusBarColor = Color(0xFF1A0F1A), navigationBarColor = Color(0xFF1A0F1A),
        topAppBarColor = Color(0xFF1A0F1A), topAppBarContentColor = Color(0xFFF2E8F0),
    )

    // ─────────────────────────────────────────────────────────────── EID ────
    val eidLight = AppTheme(
        name = EID, isDark = false,
        colorPrimary = Color(0xFF1B5E20), colorSecondary = Color(0xFFF9A825),
        colorBackground = Color(0xFFFFFFFF), colorSurface = Color(0xFFFAFDF9),
        colorOnPrimary = Color(0xFFFFFFFF), colorOnBackground = Color(0xFF1A2E1C),
        colorAccent = Color(0xFFB71C1C),
        colorBorder = Color(0xFFD8E5D8),
        headingFontFamily = FontFamily.Serif, bodyFontFamily = FontFamily.Default,
        headingFontWeight = FontWeight.SemiBold,
        cornerRadiusSmall = 8.dp, cornerRadiusMedium = 14.dp, cornerRadiusLarge = 20.dp,
        cardElevation = 3.dp,                            // lantern-like lift
        backgroundPattern = BackgroundPattern.GEOMETRIC, // subtle Islamic geometry at ~10%
        decorativeOverlay = OverlayEffect.CRESCENTS,
        animationStyle = AnimationStyle.SUBTLE,
        spacingDensity = SpacingDensity.COMFORTABLE,
        cardBorderColor = Color(0xFFD8E5D8), dividerColor = Color(0xFFD8E5D8),
        chipBackgroundColor = Color(0xFFF9A825).copy(alpha = 0.20f),
        chipTextColor = Color(0xFF8A6A10),
        statusBarColor = Color(0xFF1B5E20), navigationBarColor = Color(0xFFFFFFFF),
        topAppBarColor = Color(0xFF1B5E20), topAppBarContentColor = Color(0xFFFFFFFF),
    )
    val eidDark = eidLight.copy(
        isDark = true,
        colorPrimary = Color(0xFF4C9A51), colorSecondary = Color(0xFFFFC107),
        colorBackground = Color(0xFF0A1628), colorSurface = Color(0xFF122238),
        colorOnBackground = Color(0xFFE8F0E8), colorOnSurface = Color(0xFFE8F0E8),
        colorBorder = Color(0xFF23374F), cardBorderColor = Color(0xFF23374F),
        dividerColor = Color(0xFF23374F),
        cardBackgroundColor = Color(0xFF122238),
        dialogBackground = Color(0xFF122238),
        inputFieldBackground = Color(0xFF122238),
        snackbarBackground = Color(0xFF23374F),
        snackbarTextColor = Color(0xFFE8F0E8),
        statusBarColor = Color(0xFF0A1628), navigationBarColor = Color(0xFF0A1628),
        topAppBarColor = Color(0xFF0A1628), topAppBarContentColor = Color(0xFFE8F0E8),
    )
}
