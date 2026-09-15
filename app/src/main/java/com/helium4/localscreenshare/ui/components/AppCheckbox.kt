package com.helium4.localscreenshare.ui.components

import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

@Composable
fun AppCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val t = LocalAppTheme.current
    Checkbox(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = CheckboxDefaults.colors(
            checkedColor = t.checkboxColor,
            uncheckedColor = t.colorOutline,
            checkmarkColor = t.colorOnPrimary,
        ),
    )
}
