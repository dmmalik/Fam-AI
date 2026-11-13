package ai.fam.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import ai.fam.app.R

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab("capture", R.string.nav_capture, Icons.Filled.PhotoCamera),
    Tab("history", R.string.nav_history, Icons.Filled.History),
    Tab("settings", R.string.nav_settings, Icons.Filled.Settings),
)

@Composable
fun FamNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = tabs.any { destination?.hierarchy?.any { node -> node.route == it.route } == true }
    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "capture") {
            composable("capture") {
                CaptureScreen(
                    padding = padding,
                    onOpenResult = { id -> nav.navigate("result/$id") },
                )
            }
            composable("history") {
                HistoryScreen(
                    padding = padding,
                    onOpen = { id -> nav.navigate("result/$id") },
                )
            }
            composable("settings") {
                SettingsScreen(padding = padding)
            }
            composable("result/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                ResultScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onAdvice = { recordId -> nav.navigate("advice/$recordId") },
                )
            }
            composable("advice/{id}") { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull()
                AdviceScreen(id = id, onBack = { nav.popBackStack() })
            }
        }
    }
}
