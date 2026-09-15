package com.helium4.localscreenshare.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.helium4.localscreenshare.ui.theme.LocalAppTheme

data class AppBottomNavItem(
    val icon: ImageVector,
    val label: String,
)

@Composable
fun AppBottomNav(
    items: List<AppBottomNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalAppTheme.current
    NavigationBar(
        modifier = modifier,
        containerColor = t.bottomNavBackgroundColor,
        contentColor = t.bottomNavSelectedColor,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        fontFamily = t.labelFontFamily,
                        fontSize = t.labelFontSize,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = t.bottomNavSelectedColor,
                    selectedTextColor = t.bottomNavSelectedColor,
                    indicatorColor = t.bottomNavSelectedColor.copy(alpha = 0.14f),
                    unselectedIconColor = t.bottomNavUnselectedColor,
                    unselectedTextColor = t.bottomNavUnselectedColor,
                ),
            )
        }
    }
}
