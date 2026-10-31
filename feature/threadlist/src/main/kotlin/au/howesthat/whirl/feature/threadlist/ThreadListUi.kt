package au.howesthat.whirl.feature.threadlist

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.OpenThread
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.RowKind
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.ui.ActionsMenu
import au.howesthat.whirl.core.ui.EmptyState
import au.howesthat.whirl.core.ui.LocalAppStyle
import au.howesthat.whirl.core.ui.MenuAction
import au.howesthat.whirl.core.ui.RowPosition
import au.howesthat.whirl.core.ui.SectionHeading
import au.howesthat.whirl.core.ui.SectionRow
import au.howesthat.whirl.core.ui.Unread
import au.howesthat.whirl.core.ui.menuClickable
import au.howesthat.whirl.core.ui.ErrorState
import au.howesthat.whirl.core.ui.errorMessage
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.timeAgo
import au.howesthat.whirl.core.ui.topBarSubtleColor
import java.time.Instant

/** Navigation out of a thread list, implemented by the app's NavHost. */
class ThreadListNav(
    val openThread: (OpenThread.InApp) -> Unit,
    val openForum: (ForumRef) -> Unit,
    val openSearch: (SearchRoute) -> Unit,
)

/**
 * The thread open beside this list in a two-pane layout (null on a phone, or with no thread chosen): its row is
 * highlighted, Watched keeps it while it is unread no longer, and Back goes to the thread before the find bar.
 */
val LocalSelectedThread = compositionLocalOf<ThreadId?> { null }

/** Turns an [OpenThread] decision into navigation or a Custom Tab. */
@Composable
fun rememberThreadLauncher(nav: ThreadListNav): (OpenThread?) -> Unit {
    val browser = rememberOpenInBrowser()
    return remember(nav, browser) {
        { target ->
            when (target) {
                is OpenThread.InApp -> nav.openThread(target)
                is OpenThread.Browser -> browser(target.url)
                null -> Unit
            }
        }
    }
}

/**
 * A thread list with pull to refresh. With nothing cached, an error fills the screen (Retry, Open in browser);
 * with a cached list, the error shows above it and the list stays usable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListBody(
    sections: List<Section>?,
    refreshing: Boolean,
    error: Throwable?,
    onRefresh: () -> Unit,
    viewModel: ListViewModel,
    nav: ThreadListNav,
    emptyText: String,
    modifier: Modifier = Modifier,
    /** The page to offer in the browser when the list fails; null hides the button. */
    browserUrl: String? = null,
    /** Cross-forum lists offer "Go to forum" on rows and make headers tappable. */
    showForum: Boolean = sections?.any { it.forum != null } == true,
    header: (LazyListScope.() -> Unit)? = null,
    footer: (LazyListScope.() -> Unit)? = null,
    /** Find in list's words; when they leave nothing, the empty text says so instead of [emptyText]. */
    findQuery: String? = null,
    /** Offered under "Nothing here matches" (Watched's "Show N in All"). */
    findEmptyAction: (@Composable () -> Unit)? = null,
    /** "Nothing here matches “%1\$s”", or a forum page's "on this page". */
    @StringRes findNoneText: Int = au.howesthat.whirl.core.ui.R.string.find_none,
) {
    val launch = rememberThreadLauncher(nav)
    val browser = rememberOpenInBrowser()
    val now = Instant.now()
    // Read so rows recompose when a watch action changes what they offer.
    val watchedIds by viewModel.watchedIds.collectAsStateWithLifecycle()
    val selected = LocalSelectedThread.current
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        when {
            sections == null && error != null -> ErrorState(
                error,
                onRetry = onRefresh,
                modifier = Modifier.fillMaxWidth(),
                onOpenInBrowser = browserUrl?.let { url -> { browser(url) } },
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                header?.invoke(this)
                if (error != null) {
                    item(key = "error") {
                        Text(
                            errorMessage(error),
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                if (sections != null && sections.all { it.items.isEmpty() } && !findQuery.isNullOrBlank()) {
                    item(key = "empty") {
                        EmptyState(stringResource(findNoneText, findQuery.trim()), Modifier.fillMaxWidth(), action = findEmptyAction)
                    }
                } else if (sections != null && sections.all { it.items.isEmpty() }) {
                    item(key = "empty") {
                        EmptyState(
                            emptyText,
                            Modifier.fillMaxWidth(),
                            action = browserUrl?.let { url -> { OutlinedButton(onClick = { browser(url) }) { Text(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser)) } } },
                        )
                    }
                }
                sections.orEmpty().forEachIndexed { index, section ->
                    section.forum?.let { forum ->
                        item(key = "section-$index") { SectionHeading(forum.title, onClick = { nav.openForum(forum) }) }
                    }
                    itemsIndexed(section.items, key = { _, it -> "$index-${it.id.value}" }) { i, item ->
                        SectionRow(RowPosition.of(i, section.items.size)) {
                            ThreadRowItem(
                                item = item,
                                now = now,
                                onOpen = { launch(viewModel.open(item)) },
                                onLastPost = { launch(viewModel.lastPost(item)) },
                                onOpenInBrowser = { browser(viewModel.browserUrl(item)) },
                                onOpenForum = item.forum?.takeIf { showForum }?.let { forum -> { nav.openForum(forum.copy(groupId = null)) } },
                                watchActions = remember(item, watchedIds) { viewModel.watchActions(item) },
                                onWatchAction = { action -> viewModel.perform(action, item) },
                                selected = selected != null && item.openId == selected,
                            )
                        }
                    }
                }
                footer?.invoke(this)
            }
        }
    }
}

@Composable
fun ThreadRowItem(
    item: ThreadItem,
    now: Instant,
    onOpen: () -> Unit,
    onLastPost: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onOpenForum: (() -> Unit)?,
    watchActions: List<WatchAction> = emptyList(),
    onWatchAction: (WatchAction) -> Unit = {},
    /** The thread open beside the list (two panes). */
    selected: Boolean = false,
) {
    var menu by remember { mutableStateOf(false) }
    val openable = item.openId != null
    val inactive = item.kind != RowKind.THREAD
    val actions = watchActions.map { action -> MenuAction(watchActionLabel(action)) { onWatchAction(action) } } +
        MenuAction(stringResource(R.string.row_last_post), enabled = openable, onClick = onLastPost) +
        MenuAction(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser), onClick = onOpenInBrowser) +
        listOfNotNull(onOpenForum?.let { MenuAction(stringResource(R.string.row_go_to_forum, item.forum?.title.orEmpty()), onClick = it) })
    val style = LocalAppStyle.current
    // Unread.ACCENT: a tinted row with an accent bar at its start and "N new" instead of the badge.
    val accent = style.layout.unread == Unread.ACCENT
    val unreadTint = style.unreadContainer.takeIf { accent && (item.isUnread || item.unreadCount > 0) }
    val bar = MaterialTheme.colorScheme.primary
    Box {
        ListItem(
            modifier = Modifier
                .semantics { this.selected = selected }
                .menuClickable(
                    actions,
                    enabled = openable,
                    onLongClick = { menu = true },
                    onClick = onOpen,
                    onLongClickLabel = stringResource(au.howesthat.whirl.core.ui.R.string.show_actions),
                )
                .alpha(if (inactive) 0.6f else 1f)
                .then(
                    // The accent bar marks unread rows (Cards) and the thread open beside the list.
                    if (unreadTint == null && !selected) Modifier else Modifier.drawWithContent {
                        drawContent()
                        val width = 3.dp.toPx()
                        val x = if (layoutDirection == LayoutDirection.Ltr) 0f else size.width - width
                        drawRoundRect(bar, Offset(x, 16.dp.toPx()), Size(width, size.height - 32.dp.toPx()), CornerRadius(width / 2))
                    },
                ),
            colors = when {
                selected -> ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                unreadTint != null -> ListItemDefaults.colors(containerColor = unreadTint)
                item.isSticky -> ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                else -> ListItemDefaults.colors()
            },
            overlineContent = labels(item)?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            headlineContent = {
                Text(
                    item.title,
                    fontWeight = if (item.isUnread) (if (accent) FontWeight.SemiBold else FontWeight.Bold) else null,
                    fontStyle = if (inactive) FontStyle.Italic else null,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = { Text(details(item, now), maxLines = 2, overflow = TextOverflow.Ellipsis) },
            trailingContent = if (item.unreadCount > 0) {
                {
                    val unread = pluralStringResource(R.plurals.row_unread, item.unreadCount, item.unreadCount)
                    if (accent) {
                        Surface(
                            Modifier.clearAndSetSemantics { contentDescription = unread },
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                stringResource(R.string.row_new_count, item.unreadCount),
                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        Badge(Modifier.clearAndSetSemantics { contentDescription = unread }) { Text(item.unreadCount.toString()) }
                    }
                }
            } else {
                null
            },
        )
        ActionsMenu(menu, onDismiss = { menu = false }, actions)
    }
}

@Composable
private fun watchActionLabel(action: WatchAction): String = stringResource(
    when (action) {
        WatchAction.WATCH -> R.string.row_watch
        WatchAction.MARK_READ -> R.string.row_mark_read
        WatchAction.UNWATCH -> R.string.row_unwatch
    },
)

@Composable
private fun labels(item: ThreadItem): String? = listOfNotNull(
    stringResource(R.string.row_sticky).takeIf { item.isSticky },
    stringResource(R.string.row_closed).takeIf { item.isClosed },
    stringResource(R.string.row_moved).takeIf { item.kind == RowKind.MOVED },
    stringResource(R.string.row_deleted).takeIf { item.kind == RowKind.DELETED },
    item.group,
).joinToString(" · ").ifEmpty { null }

@Composable
private fun details(item: ThreadItem, now: Instant): String {
    if (item.kind != RowKind.THREAD) return item.note.orEmpty()
    val parts = mutableListOf<String>()
    item.replies?.let { parts += pluralStringResource(R.plurals.row_replies, it, it) }
    item.pageCount?.takeIf { it > 1 }?.let { parts += pluralStringResource(R.plurals.row_pages, it, it) }
    val time = item.lastPostedAt?.let { timeAgo(it, now) } ?: item.lastPostedText
    when {
        item.lastPoster != null && time != null -> parts += stringResource(R.string.row_last_by_at, item.lastPoster, time)
        item.lastPoster != null -> parts += stringResource(R.string.row_last_by, item.lastPoster)
        time != null -> parts += time
    }
    return parts.joinToString(" · ")
}

/** Title plus "Updated N ago" for a list's top bar. */
@Composable
fun ListTitle(title: String, updatedAt: Instant?, subtitle: String? = null) {
    Column {
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        val line = listOfNotNull(subtitle, updatedAt?.let { timeAgoUpdated(it) }).joinToString(" · ")
        if (line.isNotEmpty()) {
            Text(line, style = MaterialTheme.typography.labelSmall, color = topBarSubtleColor(), maxLines = 1)
        }
    }
}

@Composable
private fun timeAgoUpdated(at: Instant): String = stringResource(R.string.updated_ago, timeAgo(at, Instant.now()))
