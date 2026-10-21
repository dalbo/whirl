package au.howesthat.whirl.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import au.howesthat.whirl.feature.forums.ForumsScreen
import au.howesthat.whirl.feature.news.NewsScreen
import au.howesthat.whirl.feature.settings.SettingsScreen
import au.howesthat.whirl.feature.thread.ThreadNav
import au.howesthat.whirl.feature.thread.ThreadRoute
import au.howesthat.whirl.feature.thread.ThreadScreen
import au.howesthat.whirl.feature.user.UserRoute
import au.howesthat.whirl.feature.user.UserScreen
import au.howesthat.whirl.feature.threadlist.ForumRoute
import au.howesthat.whirl.feature.threadlist.ForumScreen
import au.howesthat.whirl.feature.threadlist.LatestScreen
import au.howesthat.whirl.feature.threadlist.PopularScreen
import au.howesthat.whirl.feature.threadlist.RecentScreen
import au.howesthat.whirl.feature.threadlist.SearchRoute
import au.howesthat.whirl.feature.threadlist.SearchScreen
import au.howesthat.whirl.feature.threadlist.ThreadListNav
import au.howesthat.whirl.feature.threadlist.WatchedScreen
import kotlinx.coroutines.launch

/**
 * The drawer and every destination once setup is done. [startRoute] comes from the `homepage` setting and is fixed for
 * this composition, so changing the start screen in Settings applies from the next launch, as in 2.14.12.
 */
@Composable
fun WhirlApp(
    startRoute: Any,
    account: Account?,
    alert: String,
    link: Any? = null,
    onLinkHandled: () -> Unit = {},
    /** App-wide snackbars (watch actions), shown above every screen. */
    snackbars: @Composable (SnackbarHostState) -> Unit = {},
) {
    val snackbarHost = remember { SnackbarHostState() }
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val isTopLevel = TOP_LEVEL.any { entry -> destination?.hasRoute(entry.route::class) == true }

    val drawerIcon = remember(drawerState) { NavIcon.Drawer { scope.launch { drawerState.open() } } }
    val backIcon = remember(nav) { NavIcon.Back { nav.popBackStack() } }
    val listNav = remember(nav) {
        ThreadListNav(
            openThread = { t ->
                nav.navigate(ThreadRoute(t.id.value, t.page, scrollToPost = t.scrollToPost, forumId = t.forumId, fromWatched = t.fromWatched))
            },
            openForum = { f -> nav.navigate(ForumRoute(f.id, f.title, f.groupId)) },
            openSearch = { nav.navigate(it) },
        )
    }
    val threadNav = remember(nav) {
        ThreadNav(
            openThread = { nav.navigate(it) },
            openForum = listNav.openForum,
            openUser = { nav.navigate(UserRoute(it)) },
            back = { nav.popBackStack() },
        )
    }

    // Links open on top of the start screen, so Back returns to it. A drawer destination (Watched, from the notification)
    // opens like a drawer tap; anything else is pushed, so a second thread link opens even while a thread is showing.
    LaunchedEffect(link) {
        link?.let {
            if (isTopLevelRoute(it)) nav.navigateTopLevel(it) else nav.navigate(it)
            onLinkHandled()
        }
    }

    snackbars(snackbarHost)
    Box(Modifier.fillMaxSize()) {
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
                composable<NewsRoute> { NewsScreen(drawerIcon) }
                composable<RecentRoute> { RecentScreen(drawerIcon, listNav) }
                composable<LatestRoute> { LatestScreen(drawerIcon, listNav) }
                composable<WatchedRoute> { WatchedScreen(drawerIcon, listNav) }
                composable<PopularRoute> { PopularScreen(drawerIcon, listNav) }
                composable<ForumRoute> { ForumScreen(backIcon, listNav) }
                composable<ThreadRoute> { ThreadScreen(backIcon, threadNav) }
                composable<UserRoute> { UserScreen(backIcon, listNav) }
                composable<SearchRoute> { SearchScreen(backIcon, listNav) }
                composable<ForumsRoute> { ForumsScreen(drawerIcon, onOpenForum = listNav.openForum, onSearch = { listNav.openSearch(SearchRoute()) }) }
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
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

/** Drawer destinations replace each other above the start screen; Settings and About push on top. */
private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        launchSingleTop = true
        if (isTopLevelRoute(route)) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            restoreState = true
        }
    }
}

/** Drawer destinations (not Settings or About, which push on top). */
internal fun isTopLevelRoute(route: Any): Boolean = TOP_LEVEL.any { it.route == route }
