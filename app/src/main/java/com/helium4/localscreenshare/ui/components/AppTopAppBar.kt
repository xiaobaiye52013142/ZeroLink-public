package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val theme = LocalAppTheme.current
    TopAppBar(
        modifier = modifier,
        title = {
            Text(
                text = title,
                fontFamily = theme.headingFontFamily,
                fontWeight = theme.headingFontWeight,
                color = theme.topAppBarContentColor,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = theme.topAppBarContentColor,
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = theme.topAppBarColor,
            titleContentColor = theme.topAppBarContentColor,
            navigationIconContentColor = theme.topAppBarContentColor,
            actionIconContentColor = theme.topAppBarContentColor,
        ),
    )
}
