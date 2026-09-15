package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
) {
    val theme = LocalAppTheme.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it, fontFamily = theme.labelFontFamily, fontSize = theme.labelFontSize) } },
        placeholder = placeholder?.let { { Text(it, fontFamily = theme.bodyFontFamily, fontSize = theme.bodyFontSize) } },
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        isError = isError,
        shape = RoundedCornerShape(theme.cornerRadiusMedium),
        textStyle = TextStyle(
            fontFamily = theme.bodyFontFamily,
            fontSize = theme.bodyFontSize,
            color = theme.inputFieldTextColor,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = theme.inputFieldBackground,
            unfocusedContainerColor = theme.inputFieldBackground,
            errorContainerColor = theme.inputFieldBackground,
            focusedBorderColor = theme.colorPrimary,
            unfocusedBorderColor = theme.inputFieldBorderColor,
            errorBorderColor = theme.colorError,
            focusedTextColor = theme.inputFieldTextColor,
            unfocusedTextColor = theme.inputFieldTextColor,
            cursorColor = theme.colorPrimary,
            focusedLabelColor = theme.colorPrimary,
            unfocusedLabelColor = theme.colorOutline,
            focusedLeadingIconColor = theme.colorPrimary,
            unfocusedLeadingIconColor = theme.colorOutline,
            focusedTrailingIconColor = theme.colorPrimary,
            unfocusedTrailingIconColor = theme.colorOutline,
            focusedPlaceholderColor = theme.colorOutline,
            unfocusedPlaceholderColor = theme.colorOutline,
        ),
    )
}
