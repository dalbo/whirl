package au.howesthat.whirl.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import au.howesthat.whirl.core.data.POSTS_PER_PAGE
import au.howesthat.whirl.core.data.ThreadPageRepository
import au.howesthat.whirl.core.data.pageOfPost
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.ui.rememberNow
import au.howesthat.whirl.core.ui.ErrorState
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.PagePickerDialog
import au.howesthat.whirl.core.ui.PostBodyView
import au.howesthat.whirl.core.ui.ScrollThumb
import androidx.compose.runtime.mutableIntStateOf
import au.howesthat.whirl.core.ui.FindHighlight
import au.howesthat.whirl.core.ui.OnFindShortcut
import au.howesthat.whirl.core.ui.style
import au.howesthat.whirl.core.ui.errorMessage
import au.howesthat.whirl.core.ui.readableWidth
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.timeAgo
import au.howesthat.whirl.core.ui.topBarSubtleColor
import au.howesthat.whirl.core.ui.whirlTopBarColors
import kotlinx.coroutines.launch

/** The Scaffold already pads for the sides (display cutout in landscape); the bars add only the status bar. */
private val TopBarInsets: WindowInsets
    @Composable get() = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top)

/** Navigation out of a thread, implemented by the app's NavHost. */
class ThreadNav(
    val openThread: (ThreadRoute) -> Unit,
    val openForum: (ForumRef) -> Unit,
    val openUser: (Long) -> Unit,
    val back: () -> Unit,
    /** The Watched threads list, straight from however deep the thread was reached. */
    val openWatched: () -> Unit = {},
)

/**
 * A thread, in pages or as one scroll ([ThreadRoute.scroll], else [scrollByDefault], the setting). The menu switches
 * between the two, keeping the post at the top of the screen.
 */
@Composable
fun ThreadScreen(
    route: ThreadRoute,
    navIcon: NavIcon,
    nav: ThreadNav,
    scrollByDefault: Boolean = false,
) {
    var scroll by rememberSaveable(route) { mutableStateOf(route.scroll ?: scrollByDefault) }
    // After a switch: how many so far (a fresh view model each time) and the post to open at (null: the newest).
    var switches by rememberSaveable(route) { mutableIntStateOf(0) }
    var switchedAt by rememberSaveable(route) { mutableStateOf<Int?>(null) }
    var switchedFilter by rememberSaveable(route) { mutableStateOf<String?>(null) }
    val opened = if (switches == 0) route else route.switchedTo(switchedAt).copy(filter = switchedFilter)
    val snackbar = remember { SnackbarHostState() }
    val key = "${if (scroll) "scroll" else "pages"}:$switches"

    // The top bars below are drawn inside the content and pad for the status bar themselves.
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { outer ->
        if (scroll) {
            val viewModel = hiltViewModel<ScrollThreadViewModel, ScrollThreadViewModel.Factory>(key = key) { it.create(opened) }
            ThreadEvents(viewModel.navigation, nav, snackbar)
            ScrollThread(viewModel, navIcon, nav.openWatched, onReadInPages = { top ->
                switchedAt = top
                switchedFilter = null
                switches++
                scroll = false
            }, Modifier.padding(outer))
        } else {
            val viewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(key = key) { it.create(opened) }
            ThreadEvents(viewModel.navigation, nav, snackbar, onReadFiltered = { filter ->
                switchedAt = viewModel.topPost()
                switchedFilter = filter.code
                switches++
                scroll = true
            })
            PagedThread(viewModel, navIcon, nav, onReadAsScroll = { top ->
                switchedAt = top
                switchedFilter = null
                switches++
                scroll = true
            }, Modifier.padding(outer))
        }
    }
}

/** The route after switching modes: opening at post [post], or at the newest post when null. */
internal fun ThreadRoute.switchedTo(post: Int?): ThreadRoute =
    if (post == null) copy(page = ThreadPageRepository.LAST_PAGE, scrollToPost = null, scrollToReply = null)
    else copy(page = pageOfPost(post), scrollToPost = post, scrollToReply = null)

@Composable
private fun ThreadEvents(
    events: kotlinx.coroutines.flow.Flow<ThreadEvent>,
    nav: ThreadNav,
    snackbar: SnackbarHostState,
    onReadFiltered: (ReaderFilter) -> Unit = {},
) {
    val browser = rememberOpenInBrowser()
    val failedText = stringResource(R.string.link_failed)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is ThreadEvent.OpenThread -> nav.openThread(event.route)
                is ThreadEvent.OpenForum -> nav.openForum(event.forum)
                is ThreadEvent.OpenUser -> nav.openUser(event.userId)
                is ThreadEvent.Browser -> browser(event.url)
                // Launched: showSnackbar suspends until the snackbar goes, which would hold back the next event.
                is ThreadEvent.Failed -> launch { snackbar.showSnackbar(failedText) }
                ThreadEvent.Back -> nav.back()
                is ThreadEvent.ReadFiltered -> onReadFiltered(event.filter)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagedThread(
    viewModel: ThreadViewModel,
    navIcon: NavIcon,
    nav: ThreadNav,
    onReadAsScroll: (topPost: Int?) -> Unit,
    modifier: Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    Box(modifier) {
        val start = state.startPage
        if (start == null) {
            Column {
                TopAppBar(colors = whirlTopBarColors(), title = { Text(state.title.orEmpty()) }, navigationIcon = { NavIconButton(navIcon) }, windowInsets = TopBarInsets)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val failed = state.error
                    if (failed != null) {
                        ErrorState(failed, onRetry = { viewModel.refresh() }, onOpenInBrowser = { browser(viewModel.threadUrl(1)) })
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            ThreadPager(start, state, navIcon, nav.openWatched, onReadAsScroll, viewModel, Modifier)
        }
    }
}

/** What every page needs from the thread, collected once by the pager. */
private data class PageContext(
    val pageCount: Int,
    val settings: Settings,
    val myUserId: Long?,
    val firstUnread: Int?,
    val isWatched: Boolean,
    val find: FindMatches?,
    val currentMatch: Long?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadPager(
    start: Int,
    state: ThreadUiState,
    navIcon: NavIcon,
    onWatched: () -> Unit,
    onReadAsScroll: (topPost: Int?) -> Unit,
    viewModel: ThreadViewModel,
    modifier: Modifier,
) {
    val pageCount = state.pageCount
    val pager = rememberPagerState(initialPage = (start - 1).coerceAtLeast(0)) { pageCount }
    val jumpTo by viewModel.jumpTo.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val myUserId by viewModel.myUserId.collectAsStateWithLifecycle()
    val firstUnread by viewModel.firstUnread.collectAsStateWithLifecycle()
    val isWatched by viewModel.isWatched.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val browser = rememberOpenInBrowser()
    var picking by remember { mutableStateOf(false) }
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()
    val find by viewModel.findMatches.collectAsStateWithLifecycle()
    // Which match next/previous last went to; starts again on a new page or query.
    var findIndex by remember(find?.page, findQuery) { mutableIntStateOf(-1) }
    val matchIds = find?.replyIds.orEmpty()
    fun step(by: Int) {
        val f = find ?: return
        if (f.replyIds.isEmpty()) return
        findIndex = if (findIndex < 0) (if (by > 0) 0 else f.replyIds.lastIndex) else (findIndex + by).mod(f.replyIds.size)
        viewModel.showMatch(f.page, f.replyIds[findIndex])
    }
    OnFindShortcut { if (findQuery == null) viewModel.setFindQuery("") }

    LaunchedEffect(pager.settledPage) { viewModel.showPage(pager.settledPage + 1) }
    LaunchedEffect(jumpTo) {
        jumpTo?.let {
            pager.scrollToPage(it - 1)
            viewModel.jumpHandled()
        }
    }

    val pageOf = stringResource(au.howesthat.whirl.core.ui.R.string.page_of, pager.currentPage + 1, pageCount)
    Column(modifier) {
        val query = findQuery
        if (query != null) {
            ThreadFindBar(
                query = query,
                onQueryChange = viewModel::setFindQuery,
                onClose = { viewModel.setFindQuery(null) },
                pageOf = pageOf,
                matchCount = matchIds.size,
                index = findIndex,
                onStep = ::step,
                windowInsets = TopBarInsets,
            )
        } else TopAppBar(
            colors = whirlTopBarColors(),
            windowInsets = TopBarInsets,
            title = {
                Column(Modifier.clickable(role = Role.Button, onClickLabel = stringResource(au.howesthat.whirl.core.ui.R.string.go_to_page)) { picking = true }) {
                    Text(state.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // Announced when a swipe changes the page.
                    Text(
                        pageOf,
                        Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.labelSmall,
                        color = topBarSubtleColor(),
                    )
                }
            },
            navigationIcon = { NavIconButton(navIcon) },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                ThreadMenu(
                    isWatched = isWatched,
                    onWatchAction = viewModel::perform,
                    myUserId = myUserId,
                    onUsersPosts = { viewModel.showMyPosts() },
                    onOpPosts = viewModel::showOpPosts,
                    onModeratorPosts = viewModel::showModeratorPosts,
                    onRepPosts = viewModel::showRepPosts,
                    onFirst = { scope.launch { pager.scrollToPage(0) } },
                    onLast = viewModel::lastPage,
                    onGoTo = { picking = true },
                    onReply = { browser(viewModel.replyToThreadUrl()) },
                    onOpenInBrowser = { browser(viewModel.threadUrl(pager.currentPage + 1)) },
                    onWatched = onWatched,
                    onFind = { viewModel.setFindQuery("") },
                    onSwitchMode = { onReadAsScroll(viewModel.topPost() ?: ((pager.currentPage) * POSTS_PER_PAGE + 1)) },
                )
            },
        )
        val context = PageContext(pageCount, settings, myUserId, firstUnread, isWatched, find, matchIds.getOrNull(findIndex))
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }, beyondViewportPageCount = 0) { index ->
            val current = index == pager.settledPage
            PageContent(
                page = index + 1,
                viewModel = viewModel,
                context = context,
                error = state.error.takeIf { current },
                refreshing = state.refreshing && current,
            )
        }
    }

    if (picking && pageCount > 1) {
        PagePickerDialog(
            pageCount,
            pager.currentPage + 1,
            onDismiss = { picking = false },
            firstUnreadPage = viewModel.firstUnreadPage(),
            onFirstUnread = {
                picking = false
                viewModel.goToFirstUnread()
            },
        ) { page ->
            picking = false
            scope.launch { pager.scrollToPage(page - 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageContent(page: Int, viewModel: ThreadViewModel, context: PageContext, error: Throwable?, refreshing: Boolean) {
    val ui by remember(page, viewModel) { viewModel.page(page) }.collectAsStateWithLifecycle(null)
    // Read per page: a quote tap or find step moves it, and only the page on screen needs to recompose.
    val target by viewModel.scrollTarget.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    val list = rememberLazyListState()
    val base = MaterialTheme.typography.bodyMedium
    val size = context.settings.postFontSize.sp
    val textStyle = base.copy(fontSize = size.sp, lineHeight = (size * 1.4f).sp)

    val loaded = ui
    // Header rows before the posts: updated line, moderator note, parts.
    val headerCount = 1 + (if (loaded?.note != null) 1 else 0) + (if (loaded?.page?.parts?.isNotEmpty() == true) 1 else 0)
    // The "New posts" marker sits above the first unread post.
    val markerAt = loaded?.posts?.indexOfFirst { it.post.number == context.firstUnread } ?: -1
    LaunchedEffect(loaded, target) {
        val t = target ?: return@LaunchedEffect
        if (loaded == null || t.page != page) return@LaunchedEffect
        val step = t.step(loaded.posts.map { it.post }, loaded.updatedAt)
        // Past the marker, a post is one row further down; the first unread post itself scrolls to the marker.
        step.index?.let { list.scrollToItem(headerCount + it + if (markerAt in 0 until it) 1 else 0) }
        if (step.done) viewModel.scrollHandled()
    }
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) viewModel.userScrolled() }
    }
    // The post at the top of the page, for switching to the scroll reader.
    LaunchedEffect(list, loaded) {
        val posts = loaded?.posts ?: return@LaunchedEffect
        snapshotFlow { list.firstVisibleItemIndex }.collect { i ->
            posts.getOrNull((i - headerCount).coerceIn(0, posts.lastIndex))?.let { viewModel.setTopPost(page, it.post.number) }
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.fillMaxSize()) {
        when {
            loaded == null && error != null -> ErrorState(
                error,
                onRetry = { viewModel.refresh() },
                modifier = Modifier.fillMaxWidth(),
                onOpenInBrowser = { browser(viewModel.threadUrl(page)) },
            )
            loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                PostList(loaded, page, list, error, textStyle, context, viewModel)
                // At the edge of the pane, not of the readable-width column of posts.
                ScrollThumb(list, Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun PostList(
    ui: UiPage,
    page: Int,
    list: LazyListState,
    error: Throwable?,
    textStyle: TextStyle,
    context: PageContext,
    viewModel: ThreadViewModel,
) {
    val browser = rememberOpenInBrowser()
    val find = context.find
    // With "Mark as read when opened" the thread was marked as it opened: nothing left for the button to do.
    val autoMarkRead = context.settings.watchedAutoMarkAsRead
    val now = rememberNow()
    LazyColumn(Modifier.fillMaxSize(), state = list) {
        item(key = "updated") {
            Column {
                error?.let {
                    Text(
                        errorMessage(it),
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                ui.updatedAt?.let {
                    Text(
                        stringResource(R.string.updated_ago, timeAgo(it, now)),
                        Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ui.note?.let { note ->
            item(key = "note") {
                // Theme colours only: the note's site styling (pale orange) does not survive a dark scheme.
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = cardShape(),
                    modifier = Modifier.readableWidth().cardGutter(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(R.string.moderator_note),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        PostBodyView(note, onLink = viewModel::openLink, onQuote = { viewModel.openQuote(it, ui) }, textStyle = textStyle)
                    }
                }
            }
        }
        partsNav(ui.page.parts)?.let { parts ->
            item(key = "parts") {
                PartsRow(
                    parts,
                    onOpen = { viewModel.openPart(it.threadId) },
                    modifier = Modifier.readableWidth().cardGutter(),
                )
            }
        }
        ui.posts.forEach { post ->
            if (post.post.number == context.firstUnread) item(key = "new_posts") { NewPostsMarker() }
            item(key = post.post.replyId) {
                // Posts stay a readable width in landscape and on large screens.
                PostFrame {
                    PostItem(
                        post = post,
                        textStyle = textStyle,
                        isMine = context.myUserId != null && post.post.author.id == context.myUserId,
                        onLink = viewModel::openLink,
                        onQuote = { viewModel.openQuote(it, ui) },
                        onUser = { viewModel.openUser(post.post.author.id) },
                        onUsersPosts = { viewModel.showUsersPosts(post.post.author.id, post.post.author.name) },
                        onReply = { browser(viewModel.replyToPostUrl(post.post)) },
                        onEdit = { browser(viewModel.editPostUrl(post.post)) },
                        onReport = { browser(viewModel.reportPostUrl(post.post)) },
                        onOpenInBrowser = { browser(viewModel.postUrl(post.post, page)) },
                        highlight = find?.takeIf { it.page == page && post.post.replyId in it.replyIds }
                            ?.let { FindHighlight(it.query, current = post.post.replyId == context.currentMatch) },
                    )
                }
            }
        }
        // As on Whirlpool and in Whirldroid: mark a watched thread read at the end of its last page, for those who
        // leave auto mark as read off so a half-read thread stays unread.
        if (context.isWatched && page == context.pageCount && !autoMarkRead) {
            item(key = "mark_read") {
                Box(Modifier.readableWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    FilledTonalButton(onClick = { viewModel.perform(WatchAction.MARK_READ) }) {
                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.mark_read))
                    }
                }
            }
        }
    }
}
