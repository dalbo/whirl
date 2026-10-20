package au.howesthat.whirl.navigation

import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import au.howesthat.whirl.R
import au.howesthat.whirl.about.AboutScreen
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.feature.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * The drawer and every destination once setup is done. [startRoute] comes from the `homepage` setting and is fixed for
 * this composition, so changing the start screen in Settings applies from the next launch, as in 2.14.12.
 */
@Composable
fun WhirlApp(startRoute: Any, account: Account?, alert: String) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val isTopLevel = TOP_LEVEL.any { entry -> destination?.hasRoute(entry.route::class) == true }

    val drawerIcon = remember(drawerState) { NavIcon.Drawer { scope.launch { drawerState.open() } } }
    val backIcon = remember(nav) { NavIcon.Back { nav.popBackStack() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Edge swipes stay free for pagers on pushed screens; the drawer opens from the menu button there anyway.
        gesturesEnabled = isTopLevel || drawerState.isOpen,
        drawerContent = {
            DrawerContent(
                account = account,
                alert = alert,
                isSelected = { route -> destination?.hierarchy?.any { it.hasRoute(route) } == true },
                onSelect = { entry ->
                    scope.launch { drawerState.close() }
                    nav.navigateTopLevel(entry.route)
                },
            )
        },
    ) {
        NavHost(nav, startDestination = startRoute) {
            composable<NewsRoute> { PlaceholderScreen(stringResource(R.string.nav_news), drawerIcon) }
            composable<RecentRoute> { PlaceholderScreen(stringResource(R.string.nav_recent), drawerIcon) }
            composable<LatestRoute> { PlaceholderScreen(stringResource(R.string.nav_latest), drawerIcon) }
            composable<WatchedRoute> { PlaceholderScreen(stringResource(R.string.nav_watched), drawerIcon) }
            composable<PopularRoute> { PlaceholderScreen(stringResource(R.string.nav_popular), drawerIcon) }
            composable<ForumsRoute> { PlaceholderScreen(stringResource(R.string.nav_forums), drawerIcon) }
            composable<SettingsRoute> {
                SettingsScreen(
                    onBack = { nav.popBackStack() },
                    // Signing out clears setupDone, and MainActivity swaps this whole tree for the setup wizard.
                    onSignedOut = {},
                )
            }
            composable<AboutRoute> { AboutScreen(backIcon) }
        }
    }
}

/** Drawer destinations replace each other above the start screen; Settings and About push on top. */
private fun NavHostController.navigateTopLevel(route: Any) {
    val isTopLevel = TOP_LEVEL.any { it.route == route }
    navigate(route) {
        launchSingleTop = true
        if (isTopLevel) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    }
}
