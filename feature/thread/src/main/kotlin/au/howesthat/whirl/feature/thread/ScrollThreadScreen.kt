package au.howesthat.whirl.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.ui.ErrorState
import au.howesthat.whirl.core.ui.FindHighlight
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.OnFindShortcut
import au.howesthat.whirl.core.ui.PostBodyView
import au.howesthat.whirl.core.ui.ScrollThumb
import au.howesthat.whirl.core.ui.errorMessage
import au.howesthat.whirl.core.ui.readableWidth
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.topBarSubtleColor
import au.howesthat.whirl.core.ui.whirlTopBarColors
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.NumberFormat

/** A row of the scroll reader's list. */
internal sealed interface ScrollRow {
    val key: Any

    data object Status : ScrollRow { override val key = "status" }
    data class Note(val body: PostBody) : ScrollRow { override val key = "note" }
    data class Earlier(val page: Int, val pages: Int, val label: String?) : ScrollRow { override val key = "earlier" }
    data object NewPosts : ScrollRow { override val key = "new_posts" }
    data class Gap(val missing: Int, val before: Int) : ScrollRow { override val key = "gap_$before" }
    data class PostRow(val post: UiPost) : ScrollRow { override val key: Any = post.post.replyId }
    data class Later(val page: Int, val pages: Int, val label: String?) : ScrollRow { override val key = "later" }
    data object MarkRead : ScrollRow { override val key = "mark_read" }
}

/**
 * The rows for [state]: status, moderator note, "Load earlier", the posts with a divider where the archive left posts
 * out and the "New posts" marker before [firstUnread], "Load later", and Mark as read at the newest post.
 */
internal fun scrollRows(state: ScrollUiState, firstUnread: Int?, markReadButton: Boolean): List<ScrollRow> = buildList {
    val thread = state.thread
    add(ScrollRow.Status)
    state.note?.let { add(ScrollRow.Note(it)) }
    if (thread != null && state.canLoadEarlier) {
        val index = state.window.first().index - 1
        add(ScrollRow.Earlier(index + 1, thread.pages.size, thread.pages[index].label))
    }
    var previous: Int? = null
    for (post in state.posts) {
        val n = post.post.number
        // Within the window, a jump in numbering is posts the archive leaves out (hidden by a moderator).
        if (previous != null && n > previous + 1) add(ScrollRow.Gap(n - previous - 1, n))
        if (n == firstUnread) add(ScrollRow.NewPosts)
        add(ScrollRow.PostRow(post))
        previous = n
    }
    if (thread != null && state.canLoadLater) {
        val index = state.window.last().index + 1
        add(ScrollRow.Later(index + 1, thread.pages.size, thread.pages[index].label))
    }
    if (markReadButton && state.atEnd) add(ScrollRow.MarkRead)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScrollThread(
    viewModel: ScrollThreadViewModel,
    navIcon: NavIcon,
    onWatched: () -> Unit,
    onReadInPages: (topPost: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val myUserId by viewModel.myUserId.collectAsStateWithLifecycle()
    val firstUnread by viewModel.firstUnread.collectAsStateWithLifecycle()
    val isWatched by viewModel.isWatched.collectAsStateWithLifecycle()
    val request by viewModel.scrollTo.collectAsStateWithLifecycle()
    val findQuery by viewModel.findQuery.collectAsStateWithLifecycle()
    val find by viewModel.findMatches.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    val list = rememberLazyListState()
    var findIndex by remember(findQuery) { mutableIntStateOf(-1) }
    val matchIds = find?.replyIds.orEmpty()
    OnFindShortcut { if (findQuery == null) viewModel.setFindQuery("") }

    val rows = remember(state, firstUnread, isWatched, settings.watchedAutoMarkAsRead) {
        scrollRows(state, firstUnread, markReadButton = isWatched && !settings.watchedAutoMarkAsRead)
    }

    // Bring the requested post into view once its rows exist.
    LaunchedEffect(request, rows) {
        val r = request ?: return@LaunchedEffect
        val posts = rows.filterIsInstance<ScrollRow.PostRow>()
        if (posts.isEmpty()) return@LaunchedEffect
        val target = r.indexIn(posts.map { it.post.post }) ?: return@LaunchedEffect
        // The first unread post scrolls to its marker.
        val post = posts[target]
        val row = rows.indexOf(post)
        val marker = if (post.post.post.number == firstUnread) rows.indexOf(ScrollRow.NewPosts).takeIf { it >= 0 } else null
        list.scrollToItem(marker ?: row)
        viewModel.scrollHandled()
    }
    // A drag outranks a pending scroll (it cancels scrollToItem part way): drop the request, or it would fire on the
    // next change to the rows, moving the posts from under the reader.
    LaunchedEffect(list) {
        list.interactionSource.interactions.collect { if (it is DragInteraction.Start) viewModel.scrollHandled() }
    }
    // The post at the top, for process death and switching to pages.
    LaunchedEffect(list, rows) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            val post = rows.drop(i).firstNotNullOfOrNull { (it as? ScrollRow.PostRow)?.post?.post?.number }
            post?.let(viewModel::setTopPost)
        }
    }

    val numbers = NumberFormat.getIntegerInstance()
    val range = state.posts.takeIf { it.isNotEmpty() }?.let {
        stringResource(R.string.scroll_posts_range, numbers.format(it.first().post.number), numbers.format(it.last().post.number))
    }.orEmpty()

    Column(modifier) {
        val query = findQuery
        if (query != null) {
            fun step(by: Int) {
                if (matchIds.isEmpty()) return
                findIndex = if (findIndex < 0) (if (by > 0) 0 else matchIds.lastIndex) else (findIndex + by).mod(matchIds.size)
                viewModel.showMatch(matchIds[findIndex])
            }
            ThreadFindBar(
                query = query,
                onQueryChange = viewModel::setFindQuery,
                onClose = { viewModel.setFindQuery(null) },
                pageOf = range,
                matchCount = matchIds.size,
                index = findIndex,
                onStep = ::step,
                windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            )
        } else TopAppBar(
            colors = whirlTopBarColors(),
            windowInsets = TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top),
            title = {
                Column {
                    Text(state.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(range, style = MaterialTheme.typography.labelSmall, color = topBarSubtleColor())
                }
            },
            navigationIcon = { NavIconButton(navIcon) },
            actions = {
                IconButton(onClick = viewModel::refresh) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                ThreadMenu(
                    isWatched = isWatched,
                    onWatchAction = viewModel::perform,
                    myUserId = myUserId,
                    onUsersPosts = viewModel::showUsersPosts,
                    onOpPosts = viewModel::showOpPosts,
                    onModeratorPosts = viewModel::showModeratorPosts,
                    onRepPosts = viewModel::showRepPosts,
                    onFirst = viewModel::first,
                    onLast = viewModel::last,
                    onGoTo = null,
                    onReply = { browser(viewModel.replyToThreadUrl()) },
                    onOpenInBrowser = { browser(viewModel.threadUrl()) },
                    onWatched = onWatched,
                    onFind = { viewModel.setFindQuery("") },
                    scrolling = true,
                    onSwitchMode = { onReadInPages(viewModel.topPost()) },
                )
            },
        )

        val base = MaterialTheme.typography.bodyMedium
        val size = settings.postFontSize.sp
        val textStyle = base.copy(fontSize = size.sp, lineHeight = (size * 1.4f).sp)
        val opening = state.window.isEmpty()
        PullToRefreshBox(
            isRefreshing = state.loading == ScrollLoad.REFRESH,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                opening && state.error != null -> ErrorState(
                    state.error!!,
                    onRetry = viewModel::refresh,
                    modifier = Modifier.fillMaxWidth(),
                    onOpenInBrowser = { browser(viewModel.threadUrl()) },
                )
                opening -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    LazyColumn(Modifier.fillMaxSize(), state = list) {
                        items(rows, key = { it.key }) { row ->
                            when (row) {
                                ScrollRow.Status -> StatusRow(state)
                                is ScrollRow.Note -> NoteRow(row.body, textStyle, viewModel)
                                is ScrollRow.Earlier -> LoadRow(
                                    stringResource(R.string.scroll_load_earlier),
                                    pageLabel(row.page, row.pages, row.label),
                                    loading = state.loading == ScrollLoad.EARLIER,
                                    onClick = viewModel::loadEarlier,
                                )
                                ScrollRow.NewPosts -> NewPostsMarker()
                                is ScrollRow.Gap -> Text(
                                    pluralStringResource(R.plurals.scroll_posts_missing, row.missing, row.missing),
                                    Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                is ScrollRow.PostRow -> {
                                    val post = row.post
                                    PostFrame {
                                        PostItem(
                                            post = post,
                                            textStyle = textStyle,
                                            isMine = myUserId != null && post.post.author.id == myUserId,
                                            onLink = viewModel::openLink,
                                            onQuote = viewModel::openQuote,
                                            onUser = { viewModel.openUser(post.post.author.id) },
                                            onUsersPosts = { viewModel.showUsersPosts(post.post.author.id) },
                                            onReply = { browser(viewModel.replyToPostUrl(post.post)) },
                                            onEdit = { browser(viewModel.editPostUrl(post.post)) },
                                            onReport = { browser(viewModel.reportPostUrl(post.post)) },
                                            onOpenInBrowser = { browser(viewModel.postUrl(post.post)) },
                                            highlight = find?.takeIf { post.post.replyId in it.replyIds }
                                                ?.let { FindHighlight(it.query, current = post.post.replyId == matchIds.getOrNull(findIndex)) },
                                        )
                                    }
                                }
                                is ScrollRow.Later -> LoadRow(
                                    stringResource(R.string.scroll_load_later),
                                    pageLabel(row.page, row.pages, row.label),
                                    loading = state.loading == ScrollLoad.LATER,
                                    onClick = viewModel::loadLater,
                                )
                                ScrollRow.MarkRead -> Box(Modifier.readableWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    FilledTonalButton(onClick = { viewModel.perform(WatchAction.MARK_READ) }) {
                                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(ButtonDefaults.IconSize))
                                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                        Text(stringResource(R.string.mark_read))
                                    }
                                }
                            }
                        }
                    }
                    ScrollThumb(list, Modifier.align(Alignment.CenterEnd))
                }
            }
        }
    }
}

@Composable
private fun pageLabel(page: Int, pages: Int, label: String?): String =
    if (label != null) stringResource(R.string.scroll_archive_page_dated, page, pages, label)
    else stringResource(R.string.scroll_archive_page, page, pages)

@Composable
private fun StatusRow(state: ScrollUiState) {
    val loading = state.loading
    // Announced politely: a load finishing, failing, or the reader going offline.
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        val error = state.error
        val message = when {
            error is ArchiveLimit -> stringResource(R.string.scroll_limit)
            error != null -> errorMessage(error)
            state.offline -> stringResource(R.string.scroll_offline)
            else -> null
        }
        message?.let {
            Text(
                it,
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (loading == ScrollLoad.EARLIER || loading == ScrollLoad.LATER) {
            Text(
                stringResource(R.string.scroll_loading),
                Modifier.readableWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoteRow(note: PostBody, textStyle: androidx.compose.ui.text.TextStyle, viewModel: ScrollThreadViewModel) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = cardShape(),
        modifier = Modifier.readableWidth().cardGutter(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.moderator_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            PostBodyView(note, onLink = viewModel::openLink, onQuote = viewModel::openQuote, textStyle = textStyle)
        }
    }
}

/** "Load earlier posts" / "Load later posts": an explicit button, since content appearing on its own is disorienting. */
@Composable
private fun LoadRow(text: String, detail: String, loading: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.readableWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(onClick = onClick, enabled = !loading) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(ButtonDefaults.IconSize), strokeWidth = 2.dp)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            }
            Text(text)
        }
        Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
