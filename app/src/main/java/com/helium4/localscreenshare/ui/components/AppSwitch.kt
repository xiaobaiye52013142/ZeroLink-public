package com.helium4.localscreenshare.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val t = LocalAppTheme.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = t.switchThumbColor,
            checkedTrackColor = t.switchTrackColor,
            checkedBorderColor = t.switchTrackColor,
            uncheckedThumbColor = t.colorOutline,
            uncheckedTrackColor = t.colorSurfaceVariant,
            uncheckedBorderColor = t.colorOutline,
        ),
    )
}
