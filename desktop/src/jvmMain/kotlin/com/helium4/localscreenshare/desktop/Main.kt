package com.helium4.localscreenshare.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ZeroLink",
        state = rememberWindowState(width = 900.dp, height = 600.dp)
    ) {
        MaterialTheme {
            MainScreen()
        }
    }
}
