package com.helium4.localscreenshare.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

@Composable
fun AppTabRow(
    selectedIndex: Int,
    titles: List<String>,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalAppTheme.current
    TabRow(
        selectedTabIndex = selectedIndex,
        modifier = modifier,
        containerColor = t.colorSurface,
        contentColor = t.colorPrimary,
        indicator = { positions ->
            if (selectedIndex < positions.size) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selectedIndex]),
                    color = t.colorPrimary,
                )
            }
        },
        divider = {
            HorizontalDivider(thickness = t.dividerThickness, color = t.dividerColor)
        },
    ) {
        titles.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Tab(
                selected = selected,
                onClick = { onTabSelected(index) },
                selectedContentColor = t.colorPrimary,
                unselectedContentColor = t.colorOnSurface.copy(alpha = 0.60f),
                text = {
                    Text(
                        text = title,
                        color = if (selected) t.colorPrimary else t.colorOnSurface.copy(alpha = 0.60f),
                        fontFamily = t.labelFontFamily,
                        fontSize = t.labelFontSize,
                    )
                },
            )
        }
    }
}
