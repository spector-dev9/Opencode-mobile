package io.github.spectordev9.opencode.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.spectordev9.opencode.AppDependencies
import io.github.spectordev9.opencode.BuildConfig
import io.github.spectordev9.opencode.LicensesActivity
import io.github.spectordev9.opencode.data.ThemeMode
import io.github.spectordev9.opencode.feature.chat.ChatRoute
import io.github.spectordev9.opencode.feature.sessions.SessionsRoute
import io.github.spectordev9.opencode.feature.settings.SettingsScreen
import io.github.spectordev9.opencode.feature.terminal.TerminalScreen
import io.github.spectordev9.opencode.feature.workspace.WorkspaceRoute
import io.github.spectordev9.opencode.ui.components.DemoBanner
import io.github.spectordev9.opencode.ui.theme.OpenCodeMobileTheme

private object Routes {
    const val SESSIONS = "sessions"
    const val WORKSPACE = "workspace"
    const val TERMINAL = "terminal"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{sessionId}?title={title}"

    fun chat(sessionId: String, title: String?): String =
        "chat/${Uri.encode(sessionId)}?title=${Uri.encode(title.orEmpty())}"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.SESSIONS, "Sessions", Icons.Outlined.ChatBubbleOutline),
    TopLevelDestination(Routes.WORKSPACE, "Workspace", Icons.Outlined.FolderOpen),
    TopLevelDestination(Routes.TERMINAL, "Terminal", Icons.Outlined.Terminal),
    TopLevelDestination(Routes.SETTINGS, "Settings", Icons.Outlined.Settings),
)

@Composable
fun OpenCodeMobileApp(dependencies: AppDependencies) {
    val themeMode by dependencies.preferences.themeMode.collectAsStateWithLifecycle()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    OpenCodeMobileTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val showBottomBar = currentRoute in topLevelDestinations.map { it.route }
        val context = LocalContext.current

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        topLevelDestinations.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    if (currentRoute != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(
                                                navController.graph.findStartDestination().id,
                                            ) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = destination.icon,
                                        contentDescription = destination.label,
                                    )
                                },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (dependencies.isDemo) {
                    DemoBanner()
                }
                NavHost(
                    navController = navController,
                    startDestination = Routes.SESSIONS,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(Routes.SESSIONS) {
                        SessionsRoute(
                            repository = dependencies.chat,
                            onOpenSession = { id, title ->
                                navController.navigate(Routes.chat(id, title))
                            },
                        )
                    }
                    composable(Routes.WORKSPACE) {
                        WorkspaceRoute(workspace = dependencies.workspace)
                    }
                    composable(Routes.TERMINAL) {
                        TerminalScreen()
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            themeMode = themeMode,
                            isDemo = dependencies.isDemo,
                            versionName = BuildConfig.VERSION_NAME,
                            onThemeModeChange = dependencies.preferences::setThemeMode,
                            onOpenLicenses = {
                                context.startActivity(
                                    Intent(context, LicensesActivity::class.java),
                                )
                            },
                        )
                    }
                    composable(
                        route = Routes.CHAT,
                        arguments = listOf(
                            navArgument("sessionId") { type = NavType.StringType },
                            navArgument("title") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                        ),
                    ) { entry ->
                        ChatRoute(
                            repository = dependencies.chat,
                            sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                            initialTitle = entry.arguments
                                ?.getString("title")
                                ?.takeIf { it.isNotBlank() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
