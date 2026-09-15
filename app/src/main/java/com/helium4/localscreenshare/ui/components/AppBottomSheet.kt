package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/**
 * Themed modal bottom sheet. Only the top corners are rounded, so the sheet reads as
 * anchored to the bottom edge regardless of how large the theme's radius token is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalAppTheme.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = t.dialogBackground,
        contentColor = t.colorOnSurface,
        shape = RoundedCornerShape(
            topStart = t.cornerRadiusLarge,
            topEnd = t.cornerRadiusLarge,
        ),
        dragHandle = { BottomSheetDefaults.DragHandle(color = t.colorOutline) },
        content = content,
    )
}
