package com.helium4.localscreenshare.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helium4.localscreenshare.BuildConfig

/**
 * QA affordance: a small pill in the bottom-right that names the active theme and advances to
 * the next one on tap. Compiled out of release builds by the [BuildConfig.DEBUG] guard.
 */
@Composable
fun DebugThemeSwitcher(modifier: Modifier = Modifier) {
    if (!BuildConfig.DEBUG) return

    val themeId by ThemeManager.themeId.collectAsStateWithLifecycle()
    val t = LocalAppTheme.current

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Text(
            text = "🎨 $themeId",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = t.colorOnPrimary,
            modifier = Modifier
                .padding(12.dp)
                .background(t.colorPrimary.copy(alpha = 0.92f), RoundedCornerShape(50))
                .clickable { ThemeManager.cycleDebugTheme() }
                .padding(PaddingValues(horizontal = 12.dp, vertical = 7.dp)),
        )
    }
}
