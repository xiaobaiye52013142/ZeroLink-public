package com.helium4.localscreenshare.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    HorizontalDivider(
        modifier = modifier,
        thickness = theme.dividerThickness,
        color = theme.dividerColor,
    )
}

@Composable
fun AppVerticalDivider(modifier: Modifier = Modifier) {
    val theme = LocalAppTheme.current
    VerticalDivider(
        modifier = modifier,
        thickness = theme.dividerThickness,
        color = theme.dividerColor,
    )
}
