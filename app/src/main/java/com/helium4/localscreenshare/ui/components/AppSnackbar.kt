package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/** Drop-in replacement for SnackbarHost that paints from theme tokens. */
@Composable
fun AppSnackbarHost(hostState: SnackbarHostState) {
    val t = LocalAppTheme.current
    SnackbarHost(hostState) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(t.cornerRadiusSmall),
            containerColor = t.snackbarBackground,
            contentColor = t.snackbarTextColor,
            actionColor = t.colorAccent,
            actionContentColor = t.colorAccent,
            dismissActionContentColor = t.snackbarTextColor,
        )
    }
}
