package au.howesthat.whirl.feature.thread

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.PostFilter
import au.howesthat.whirl.core.ui.ErrorState
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.PagePickerDialog
import au.howesthat.whirl.core.ui.PostBodyView
import au.howesthat.whirl.core.ui.errorMessage
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.timeAgo
import kotlinx.coroutines.launch
import java.time.Instant

/** Navigation out of a thread, implemented by the app's NavHost. */
class ThreadNav(
    val openThread: (ThreadRoute) -> Unit,
    val openForum: (ForumRef) -> Unit,
    val openUser: (Long) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(navIcon: NavIcon, nav: ThreadNav, viewModel: ThreadViewModel = hiltViewModel()) {
    val startPage by viewModel.startPage.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    val snackbar = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.link_failed)

    LaunchedEffect(viewModel) {
        viewModel.navigation.collect { event ->
            when (event) {
                is ThreadEvent.OpenThread -> nav.openThread(event.route)
                is ThreadEvent.OpenForum -> nav.openForum(event.forum)
                is ThreadEvent.OpenUser -> nav.openUser(event.userId)
                is ThreadEvent.Browser -> browser(event.url)
                is ThreadEvent.Failed -> snackbar.showSnackbar(failedText)
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outer ->
        val start = startPage
        if (start == null) {
            Column(Modifier.padding(outer)) {
                TopAppBar(title = { Text(title.orEmpty()) }, navigationIcon = { NavIconButton(navIcon) })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val failed = error
                    if (failed != null) {
                        ErrorState(failed, onRetry = { viewModel.refresh() }, onOpenInBrowser = { browser(viewModel.threadUrl(1)) })
                    } else {
                        CircularProgressIndicator()
                    }
                }
            }
        } else {
            ThreadPager(start, pageCount, title, filter, error, refreshing, navIcon, viewModel, Modifier.padding(outer))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThreadPager(
    start: Int,
    pageCount: Int,
    title: String?,
    filter: PostFilter,
    error: Throwable?,
    refreshing: Boolean,
    navIcon: NavIcon,
    viewModel: ThreadViewModel,
    modifier: Modifier,
) {
    val pager = rememberPagerState(initialPage = (start - 1).coerceAtLeast(0)) { pageCount }
    val jumpTo by viewModel.jumpTo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val browser = rememberOpenInBrowser()
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(pager.settledPage) { viewModel.showPage(pager.settledPage + 1) }
    LaunchedEffect(jumpTo) {
        jumpTo?.let {
            pager.scrollToPage(it - 1)
            viewModel.jumpHandled()
        }
    }

    Column(modifier) {
        TopAppBar(
            title = {
                Column(Modifier.clickable(role = Role.Button) { picking = true }) {
                    Text(title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(
                        stringResource(au.howesthat.whirl.core.ui.R.string.page_of, pager.currentPage + 1, pageCount),
                        filterLabel(filter).takeIf { filter != PostFilter.All },
                    ).joinToString(" · ")
                    Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = { NavIconButton(navIcon) },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                ThreadMenu(
                    filter = filter,
                    myUserId = viewModel.myUserId.collectAsStateWithLifecycle().value,
                    onFilter = viewModel::setFilter,
                    onFirst = { scope.launch { pager.scrollToPage(0) } },
                    onLast = viewModel::lastPage,
                    onGoTo = { picking = true },
                    onReply = { browser(viewModel.replyToThreadUrl()) },
                    onOpenInBrowser = { browser(viewModel.threadUrl(pager.currentPage + 1)) },
                )
            },
        )
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { it }, beyondViewportPageCount = 0) { index ->
            val current = index == pager.settledPage
            PageContent(
                page = index + 1,
                viewModel = viewModel,
                error = error.takeIf { current },
                refreshing = refreshing && current,
            )
        }
    }

    if (picking && pageCount > 1) {
        PagePickerDialog(pageCount, pager.currentPage + 1, onDismiss = { picking = false }) { page ->
            picking = false
            scope.launch { pager.scrollToPage(page - 1) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageContent(page: Int, viewModel: ThreadViewModel, error: Throwable?, refreshing: Boolean) {
    val ui by remember(page, viewModel) { viewModel.page(page) }.collectAsStateWithLifecycle(null)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val target by viewModel.scrollTarget.collectAsStateWithLifecycle()
    val myUserId by viewModel.myUserId.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    val list = rememberLazyListState()
    val base = MaterialTheme.typography.bodyMedium
    val textStyle = settings.postFontSize.sp?.let { base.copy(fontSize = it.sp, lineHeight = (it * 1.4f).sp) } ?: base

    val loaded = ui
    // Header rows before the posts: updated line, moderator note, parts.
    val headerCount = 1 + (if (loaded?.note != null) 1 else 0) + (if (loaded?.page?.parts?.isNotEmpty() == true) 1 else 0)
    LaunchedEffect(loaded, target) {
        val t = target ?: return@LaunchedEffect
        if (loaded == null || t.page != page) return@LaunchedEffect
        val index = loaded.posts.indexOfFirst { p ->
            (t.replyId != null && p.post.replyId == t.replyId) || (t.postNumber != null && p.post.number == t.postNumber)
        }
        if (index >= 0) list.scrollToItem(headerCount + index)
        viewModel.scrollHandled()
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
            else -> PostList(loaded, page, list, error, textStyle, myUserId, viewModel)
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
    myUserId: Long?,
    viewModel: ThreadViewModel,
) {
    val browser = rememberOpenInBrowser()
    val now = Instant.now()
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
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        ui.note?.let { note ->
            item(key = "note") {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.moderator_note), style = MaterialTheme.typography.labelMedium)
                        PostBodyView(note, onLink = viewModel::openLink, onQuote = { viewModel.openQuote(it, ui) }, textStyle = textStyle)
                    }
                }
            }
        }
        if (ui.page.parts.isNotEmpty()) {
            item(key = "parts") {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.parts), Modifier.align(Alignment.CenterVertically).padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
                    ui.page.parts.forEach { part ->
                        TextButton(
                            onClick = { viewModel.openLink("https://forums.whirlpool.net.au/thread/${part.threadId.value}") },
                            enabled = !part.isCurrent,
                        ) { Text(stringResource(R.string.part_n, part.number)) }
                    }
                }
            }
        }
        items(ui.posts, key = { it.post.replyId }) { post ->
            PostItem(
                post = post,
                textStyle = textStyle,
                isMine = myUserId != null && post.post.author.id == myUserId,
                onLink = viewModel::openLink,
                onQuote = { viewModel.openQuote(it, ui) },
                onUser = { viewModel.openLink("https://forums.whirlpool.net.au/user/${post.post.author.id}") },
                onUsersPosts = { viewModel.setFilter(PostFilter.ByUser(post.post.author.id)) },
                onReply = { browser(viewModel.replyToPostUrl(post.post)) },
                onEdit = { browser(viewModel.editPostUrl(post.post)) },
                onOpenInBrowser = { browser(viewModel.postUrl(post.post, page)) },
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostItem(
    post: UiPost,
    textStyle: TextStyle,
    isMine: Boolean,
    onLink: (String) -> Unit,
    onQuote: (Long) -> Unit,
    onUser: () -> Unit,
    onUsersPosts: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    val p = post.post
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = {}, onLongClick = { menu = true })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PostHeader(p, onUser)
            when {
                p.isHidden -> Text(
                    stringResource(R.string.post_hidden, p.hiddenReason.orEmpty()),
                    style = textStyle,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                p.isDeleted && post.body.blocks.isEmpty() -> Text(
                    stringResource(R.string.post_deleted),
                    style = textStyle,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> PostBodyView(post.body, onLink = onLink, onQuote = onQuote, textStyle = textStyle)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.post_reply)) }, onClick = { menu = false; onReply() })
            if (isMine) DropdownMenuItem(text = { Text(stringResource(R.string.post_edit)) }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.post_user_info, p.author.name)) }, onClick = { menu = false; onUser() })
            DropdownMenuItem(text = { Text(stringResource(R.string.post_users_posts, p.author.name)) }, onClick = { menu = false; onUsersPosts() })
            DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser)) }, onClick = { menu = false; onOpenInBrowser() })
        }
    }
}

@Composable
private fun PostHeader(p: Post, onUser: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.author.name,
                Modifier.weight(1f, fill = false).clickable(role = Role.Button, onClick = onUser),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            p.userGroup?.let {
                Text(" · $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Box(Modifier.weight(1f))
            Text("#${p.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val meta = listOfNotNull(
            stringResource(R.string.post_op).takeIf { p.isOp },
            p.postedText,
            p.editedText?.let { stringResource(R.string.post_edited, it) },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (p.isOp) FontWeight.Medium else null,
            )
        }
        p.quip?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun filterLabel(filter: PostFilter): String = when (filter) {
    PostFilter.All -> stringResource(R.string.filter_all)
    PostFilter.Moderators -> stringResource(R.string.filter_mods)
    PostFilter.Reps -> stringResource(R.string.filter_reps)
    is PostFilter.ByUser -> stringResource(R.string.filter_user)
}

@Composable
private fun ThreadMenu(
    filter: PostFilter,
    myUserId: Long?,
    onFilter: (PostFilter) -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    onGoTo: () -> Unit,
    onReply: () -> Unit,
    onOpenInBrowser: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.first_page)) }, onClick = { open = false; onFirst() })
        DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.last_page)) }, onClick = { open = false; onLast() })
        DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.go_to_page)) }, onClick = { open = false; onGoTo() })
        HorizontalDivider()
        val filters = listOfNotNull(
            PostFilter.All,
            myUserId?.let { PostFilter.ByUser(it) },
            PostFilter.Moderators,
            PostFilter.Reps,
        )
        filters.forEach { f ->
            DropdownMenuItem(
                text = { Text(if (f is PostFilter.ByUser) stringResource(R.string.filter_mine) else filterLabel(f)) },
                leadingIcon = if (f == filter) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else {
                    null
                },
                onClick = { open = false; onFilter(f) },
            )
        }
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.reply_to_thread)) }, onClick = { open = false; onReply() })
        DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser)) }, onClick = { open = false; onOpenInBrowser() })
    }
}
