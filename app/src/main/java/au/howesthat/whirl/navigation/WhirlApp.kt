package au.howesthat.whirl.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import au.howesthat.whirl.about.AboutScreen
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.data.OpenThread
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.feature.forums.ForumsScreen
import au.howesthat.whirl.feature.news.NewsScreen
import au.howesthat.whirl.feature.settings.SettingsScreen
import au.howesthat.whirl.feature.thread.ThreadNav
import au.howesthat.whirl.feature.thread.ThreadRoute
import au.howesthat.whirl.feature.thread.ThreadScreen
import au.howesthat.whirl.feature.threadlist.ForumRoute
import au.howesthat.whirl.feature.threadlist.ForumScreen
import au.howesthat.whirl.feature.threadlist.LatestScreen
import au.howesthat.whirl.feature.threadlist.PopularScreen
import au.howesthat.whirl.feature.threadlist.RecentScreen
import au.howesthat.whirl.feature.threadlist.SearchRoute
import au.howesthat.whirl.feature.threadlist.SearchScreen
import au.howesthat.whirl.feature.threadlist.ThreadListNav
import au.howesthat.whirl.feature.threadlist.WatchedScreen
import au.howesthat.whirl.feature.user.UserRoute
import au.howesthat.whirl.feature.user.UserScreen
import kotlinx.coroutines.launch

/**
 * The drawer and every destination once setup is done. [startRoute] comes from the `homepage` setting and is fixed for
 * this composition, so changing the start screen in Settings applies from the next launch, as in 2.14.12.
 */
@Composable
fun WhirlApp(
    startRoute: NavKey,
    account: Account?,
    alert: String,
    /** A route to open, or [OpenThread.Browser] for a thread notification that opens in the browser. */
    link: Any? = null,
    /** The "Screen animations" setting: off switches screens without the cross-fade. */
    animations: Boolean = true,
    onLinkHandled: () -> Unit = {},
    /** App-wide snackbars (watch actions), shown above every screen. */
    snackbars: @Composable (SnackbarHostState) -> Unit = {},
) {
    val snackbarHost = remember { SnackbarHostState() }
    val tops = remember { TOP_LEVEL.map { it.route } }
    val backStack = rememberSaveable(
        saver = Saver<TopLevelBackStack, String>(
            save = { TopLevelBackStack.encode(it.save()) },
            restore = { TopLevelBackStack(startRoute, tops, TopLevelBackStack.decode(it)) },
        ),
    ) { TopLevelBackStack(startRoute, tops) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val top = backStack.visible.last()
    val isTopLevel = backStack.isTopLevel(top)

    val drawerIcon = remember(drawerState) { NavIcon.Drawer { scope.launch { drawerState.open() } } }
    val backIcon = remember(backStack) { NavIcon.Back { backStack.pop() } }
    val listNav = remember(backStack) {
        ThreadListNav(
            openThread = { t ->
                backStack.push(ThreadRoute(t.id.value, t.page, scrollToPost = t.scrollToPost, forumId = t.forumId, fromWatched = t.fromWatched))
            },
            openForum = { f -> backStack.push(ForumRoute(f.id, f.title, f.groupId)) },
            openSearch = { backStack.push(it) },
        )
    }
    val threadNav = remember(backStack) {
        ThreadNav(
            openThread = { backStack.push(it) },
            openForum = listNav.openForum,
            openUser = { backStack.push(UserRoute(it)) },
            back = { backStack.pop() },
        )
    }

    // Links open on top of the start screen, so Back returns to it. A drawer destination (Watched, from the notification)
    // opens like a drawer tap; anything else is pushed, so a second thread link opens even while a thread is showing.
    val browser = rememberOpenInBrowser()
    LaunchedEffect(link) {
        link?.let {
            when {
                // A thread notification's thread that opens in the browser (private forum, or "open in the browser").
                it is OpenThread.Browser -> browser(it.url)
                it !is NavKey -> Unit
                backStack.isTopLevel(it) -> backStack.select(it)
                else -> backStack.push(it)
            }
            onLinkHandled()
        }
    }

    val provider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<NewsRoute> { NewsScreen(drawerIcon) }
        entry<RecentRoute> { RecentScreen(drawerIcon, listNav) }
        entry<LatestRoute> { LatestScreen(drawerIcon, listNav) }
        entry<WatchedRoute> { WatchedScreen(drawerIcon, listNav) }
        entry<PopularRoute> { PopularScreen(drawerIcon, listNav) }
        entry<ForumRoute> { ForumScreen(it, backIcon, listNav) }
        entry<ThreadRoute> { ThreadScreen(it, backIcon, threadNav) }
        entry<UserRoute> { UserScreen(it, backIcon, listNav) }
        entry<SearchRoute> { SearchScreen(it, backIcon, listNav) }
        entry<ForumsRoute> { ForumsScreen(drawerIcon, onOpenForum = listNav.openForum, onSearch = { words -> listNav.openSearch(SearchRoute(query = words)) }) }
        entry<SettingsRoute> {
            SettingsScreen(
                onBack = { backStack.pop() },
                // Signing out clears setupDone, and MainActivity swaps this whole tree for the setup wizard.
                onSignedOut = {},
            )
        }
        entry<AboutRoute> { AboutScreen(backIcon) }
    }
    // One set of entries per drawer stack, each with its own saved state and ViewModels, so a stack that is not on
    // screen keeps them (Navigation 2's saveState/restoreState).
    val entries = backStack.all.mapValues { (topRoute, stack) ->
        key(topRoute) {
            rememberDecoratedNavEntries(
                backStack = stack,
                entryDecorators = listOf(
                    // Saved state first: the ViewModels' SavedStateHandles come from it.
                    rememberSaveableStateHolderNavEntryDecorator(rememberSaveableStateHolder()),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = provider,
            )
        }
    }
    val startEntries = entries.getValue(backStack.start)
    val shown = if (backStack.current == backStack.start) startEntries else startEntries.take(1) + entries.getValue(backStack.current)

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
                    isSelected = { route -> top::class == route },
                    onSelect = { entry ->
                        scope.launch { drawerState.close() }
                        // Settings and About push on top; drawer destinations switch stacks.
                        if (backStack.isTopLevel(entry.route)) backStack.select(entry.route) else backStack.push(entry.route)
                    },
                )
            },
        ) {
            if (animations) {
                // Navigation 2's defaults, kept so screens change as before: a 700 ms cross-fade, and the back
                // gesture's page shrinking into a card.
                NavDisplay(
                    entries = shown,
                    onBack = { backStack.pop() },
                    transitionSpec = { fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS)) },
                    popTransitionSpec = { fadeIn(tween(FADE_MILLIS)) togetherWith fadeOut(tween(FADE_MILLIS)) },
                    predictivePopTransitionSpec = {
                        fadeIn(spring(stiffness = Spring.StiffnessMedium)) togetherWith scaleOut(targetScale = 0.7f)
                    },
                )
            } else {
                // The back gesture has its own pair (the page shrinking into a card); without it, it still animated.
                NavDisplay(
                    entries = shown,
                    onBack = { backStack.pop() },
                    transitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    popTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                    predictivePopTransitionSpec = { EnterTransition.None togetherWith ExitTransition.None },
                )
            }
        }
        SnackbarHost(snackbarHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

private const val FADE_MILLIS = 700

/** Drawer destinations (not Settings or About, which push on top). */
internal fun isTopLevelRoute(route: Any): Boolean = TOP_LEVEL.any { it.route == route }
