package com.helium4.localscreenshare.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

/**
 * Deliberately built on OutlinedTextField rather than Material's SearchBar: SearchBar is
 * still experimental and drags in its own expand/collapse surface, which fights the theme.
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val t = LocalAppTheme.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(t.cornerRadiusMedium),
        textStyle = TextStyle(
            fontFamily = t.bodyFontFamily,
            fontSize = t.bodyFontSize,
            color = t.inputFieldTextColor,
        ),
        placeholder = {
            Text(
                text = placeholder,
                color = t.inputFieldTextColor.copy(alpha = 0.55f),
                fontFamily = t.bodyFontFamily,
                fontSize = t.bodyFontSize,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = t.inputFieldTextColor.copy(alpha = 0.70f),
            )
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = t.inputFieldTextColor.copy(alpha = 0.70f),
                    )
                }
            }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = t.inputFieldTextColor,
            unfocusedTextColor = t.inputFieldTextColor,
            focusedContainerColor = t.inputFieldBackground,
            unfocusedContainerColor = t.inputFieldBackground,
            cursorColor = t.colorPrimary,
            focusedBorderColor = t.colorPrimary,
            unfocusedBorderColor = t.inputFieldBorderColor,
        ),
    )
}
