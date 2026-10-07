package com.lapel.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lapel.app.R
import com.lapel.app.ui.common.ComingSoonScreen
import kotlinx.serialization.Serializable

@Serializable object DashboardRoute
@Serializable object OrdersRoute
@Serializable object StockRoute
@Serializable object CustomersRoute
@Serializable object SettingsRoute

private enum class TopLevelDestination(
    val route: Any,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    DASHBOARD(DashboardRoute, R.string.tab_dashboard, Icons.Outlined.Dashboard),
    ORDERS(OrdersRoute, R.string.tab_orders, Icons.Outlined.ShoppingCart),
    STOCK(StockRoute, R.string.tab_stock, Icons.Outlined.Inventory2),
    CUSTOMERS(CustomersRoute, R.string.tab_customers, Icons.Outlined.People),
    SETTINGS(SettingsRoute, R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun LapelApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = DashboardRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<DashboardRoute> { ComingSoonScreen(R.string.tab_dashboard, R.string.coming_dashboard) }
            composable<OrdersRoute> { ComingSoonScreen(R.string.tab_orders, R.string.coming_orders) }
            composable<StockRoute> { ComingSoonScreen(R.string.tab_stock, R.string.coming_stock) }
            composable<CustomersRoute> { ComingSoonScreen(R.string.tab_customers, R.string.coming_customers) }
            composable<SettingsRoute> { ComingSoonScreen(R.string.tab_settings, R.string.coming_settings) }
        }
    }
}
