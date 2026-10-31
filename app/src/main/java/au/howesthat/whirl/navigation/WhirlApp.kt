// The list-detail scene strategy is experimental in material3-adaptive 1.3.
@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

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
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.window.core.layout.WindowSizeClass
import au.howesthat.whirl.R
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.feature.threadlist.LocalSelectedThread
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
import au.howesthat.whirl.feature.settings.CheckHistoryScreen
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
    /** The "Threads beside the list" setting (experimental). */
    twoPane: Boolean = false,
    /** How many panes fit; null measures the window. Tests pass one, since Robolectric's window is not the point. */
    paneDirective: PaneScaffoldDirective? = null,
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
    val directive = paneDirective ?: windowDirective(twoPane)
    val twoPanes = directive.maxHorizontalPartitions > 1
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val top = backStack.visible.last()
    val isTopLevel = backStack.isTopLevel(top)

    val drawerIcon = remember(drawerState) { NavIcon.Drawer { scope.launch { drawerState.open() } } }
    val backIcon = remember(backStack) { NavIcon.Back { backStack.pop() } }
    val listNav = remember(backStack) {
        ThreadListNav(
            // In two panes the tap comes from the list beside a thread, which it replaces; on a phone this is a push.
            openThread = { t ->
                backStack.openFromList(ThreadRoute(t.id.value, t.page, scrollToPost = t.scrollToPost, forumId = t.forumId, fromWatched = t.fromWatched), ::isListPane)
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
                // A watched thread's notification: beside Watched when there is room for both.
                twoPanes && it is ThreadRoute && it.fromWatched -> {
                    backStack.select(WatchedRoute)
                    backStack.openFromList(it, ::isListPane)
                }
                backStack.isTopLevel(it) -> backStack.select(it)
                else -> backStack.push(it)
            }
            onLinkHandled()
        }
    }

    // List screens and threads carry pane metadata; each list is its own scene (its key), and a thread joins the scene
    // of the list below it, so a list pushed above a thread starts with the placeholder, not that thread.
    val paneMotion = if (animations) emptyMap() else ListDetailSceneStrategy.paneAnimation(EnterTransition.None, ExitTransition.None, snap())
    val listMeta: (NavKey) -> Map<String, Any> = { key ->
        ListDetailSceneStrategy.listPane(sceneKey = key, detailPlaceholder = { ChooseThread() }) +
            ListDetailSceneStrategy.preferredPaneSize(width = LIST_PANE_WIDTH) + paneMotion
    }
    val threadMeta: (ThreadRoute) -> Map<String, Any> = { key ->
        backStack.listBelow(key, ::isListPane)?.let { list -> ListDetailSceneStrategy.detailPane(sceneKey = list) + paneMotion }.orEmpty()
    }

    /** A list screen, told which thread is open beside it when there are two panes. */
    @Composable
    fun ListPane(key: NavKey, content: @Composable () -> Unit) {
        val selected = (backStack.above(key) as? ThreadRoute)?.takeIf { twoPanes }?.let { ThreadId(it.threadId) }
        CompositionLocalProvider(LocalSelectedThread provides selected) { content() }
    }

    val provider: (NavKey) -> NavEntry<NavKey> = entryProvider {
        entry<NewsRoute> { NewsScreen(drawerIcon) }
        entry<RecentRoute>(metadata = listMeta) { ListPane(it) { RecentScreen(drawerIcon, listNav) } }
        entry<LatestRoute>(metadata = listMeta) { ListPane(it) { LatestScreen(drawerIcon, listNav) } }
        entry<WatchedRoute>(metadata = listMeta) { ListPane(it) { WatchedScreen(drawerIcon, listNav) } }
        entry<PopularRoute>(metadata = listMeta) { ListPane(it) { PopularScreen(drawerIcon, listNav) } }
        entry<ForumRoute>(metadata = listMeta) { ListPane(it) { ForumScreen(it, backIcon, listNav) } }
        entry<ThreadRoute>(metadata = threadMeta) { ThreadScreen(it, backIcon, threadNav) }
        entry<UserRoute>(metadata = listMeta) { ListPane(it) { UserScreen(it, backIcon, listNav) } }
        entry<SearchRoute>(metadata = listMeta) { ListPane(it) { SearchScreen(it, backIcon, listNav) } }
        entry<ForumsRoute> { ForumsScreen(drawerIcon, onOpenForum = listNav.openForum, onSearch = { words -> listNav.openSearch(SearchRoute(query = words)) }) }
        entry<SettingsRoute> {
            SettingsScreen(
                onBack = { backStack.pop() },
                // Signing out clears setupDone, and MainActivity swaps this whole tree for the setup wizard.
                onSignedOut = {},
                onCheckHistory = { backStack.push(CheckHistoryRoute) },
            )
        }
        entry<CheckHistoryRoute> { CheckHistoryScreen(onBack = { backStack.pop() }) }
        entry<AboutRoute> { AboutScreen(backIcon, onOpenThread = { id -> backStack.push(ThreadRoute(id)) }) }
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
            // Edge swipes stay free for pagers on pushed screens; the drawer opens from the menu button there anyway. In
            // two panes a drawer destination's list beside a thread counts as on screen.
            gesturesEnabled = isTopLevel || drawerState.isOpen ||
                (twoPanes && top is ThreadRoute && backStack.listBelow(top, ::isListPane)?.let(backStack::isTopLevel) == true),
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
            val listDetail = rememberListDetailSceneStrategy<NavKey>(
                // Back from a thread reached by a link returns to the thread before it, not straight to the list.
                backNavigationBehavior = BackNavigationBehavior.PopLatest,
                directive = directive,
            )
            val strategies = listOf(listDetail, SinglePaneSceneStrategy())
            if (animations) {
                // Navigation 2's defaults, kept so screens change as before: a 700 ms cross-fade, and the back
                // gesture's page shrinking into a card.
                NavDisplay(
                    entries = shown,
                    sceneStrategies = strategies,
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
                    sceneStrategies = strategies,
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
private val LIST_PANE_WIDTH = 400.dp

/** Screens shown as the list beside a thread. */
internal fun isListPane(key: NavKey): Boolean =
    key is WatchedRoute || key is RecentRoute || key is LatestRoute || key is PopularRoute ||
        key is ForumRoute || key is SearchRoute || key is UserRoute

/**
 * Two panes when the setting is on and the window is at least 840 dp wide and 480 dp tall: the height rule keeps a
 * phone in landscape (about 900 x 400 dp) to one pane, where a 400 dp list would leave the thread too narrow.
 */
@Composable
private fun windowDirective(twoPane: Boolean): PaneScaffoldDirective {
    val info = currentWindowAdaptiveInfoV2()
    val directive = calculatePaneScaffoldDirective(info)
    val wide = info.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) &&
        info.windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    return if (twoPane && wide) directive else directive.copy(maxHorizontalPartitions = 1)
}

/** The detail pane before a thread is chosen. */
@Composable
private fun ChooseThread() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Text(
            stringResource(R.string.choose_thread),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Drawer destinations (not Settings or About, which push on top). */
internal fun isTopLevelRoute(route: Any): Boolean = TOP_LEVEL.any { it.route == route }
