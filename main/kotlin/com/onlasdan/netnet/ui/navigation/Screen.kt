package com.onlasdan.netnet.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.DataUsage
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for Net Speed Indicator
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        selectedIcon = Icons.Filled.Speed,
        unselectedIcon = Icons.Outlined.Speed,
        testTag = "nav_dashboard_tab"
    )

    data object DataUsage : Screen(
        route = "data_usage",
        title = "Data Usage",
        selectedIcon = Icons.Filled.DataUsage,
        unselectedIcon = Icons.Outlined.DataUsage,
        testTag = "nav_data_usage_tab"
    )

    data object Diagnostics : Screen(
        route = "diagnostics",
        title = "Diagnostics",
        selectedIcon = Icons.Filled.NetworkCheck,
        unselectedIcon = Icons.Outlined.NetworkCheck,
        testTag = "nav_diagnostics_tab"
    )

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Dashboard, DataUsage, Diagnostics)
    }
}
