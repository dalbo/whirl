package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.AppVisits
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.ui.LocalAppStyle
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.Tabs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Watched threads in All that match the find query; [count] is null when All has not been fetched. */
data class AllMatches(val count: Int?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchedViewModel @Inject constructor(deps: ListDeps, private val visits: AppVisits) :
    ListViewModel(deps, OpenedFrom.WATCHED) {

    private val _unreadOnly = MutableStateFlow(true)
    /** The Unread/All tab (2.14.12 replaced 1.8's "hide read" setting with these tabs). */
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    /** The thread open beside the list (two panes): kept on the Unread tab after it is marked read. */
    private val kept = MutableStateFlow<ThreadId?>(null)

    fun keep(id: ThreadId?) {
        kept.value = id
    }

    val state = stateOf(
        _unreadOnly.flatMapLatest { unread ->
            val sections = deps.watched.watched(unread).toListState { list -> groupByForum(list.map { it.toItem() }) }
            if (!unread) {
                sections
            } else {
                // Opening a thread marks it read, which takes it off Unread while it is open beside the list.
                var previous: List<Section>? = null
                combine(sections, kept) { state, id ->
                    val shown = state.sections?.let { keepRow(it, previous, id) }
                    previous = shown
                    state.copy(sections = shown)
                }
            }
        }.filtered(),
    )

    /**
     * Finding in Unread: how many threads in All match too, offered when Unread has none. The count is null when All
     * was never fetched (Watched opens on Unread), so the hint only says to look there.
     */
    val allMatches: StateFlow<AllMatches?> = combine(_unreadOnly, query, deps.watched.watched(unreadOnly = false)) { unread, query, all ->
        val words = TextQuery(query)
        when {
            !unread || words.isBlank -> null
            all.updatedAt == null -> AllMatches(null)
            else -> filterSections(groupByForum(all.value.map { it.toItem() }), words).sumOf { it.items.size }.takeIf { it > 0 }?.let(::AllMatches)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Mark all as read: the unread threads the list shows, so only the matching ones while finding. Checked against
     * the Unread list, since the thread kept open beside the list there is already read.
     */
    val markAllIds: StateFlow<List<ThreadId>> = combine(state, deps.watched.watched(unreadOnly = true)) { shown, unread ->
        val unreadIds = unread.value.filter { it.unreadCount > 0 }.mapTo(mutableSetOf()) { it.id }
        shown.sections.orEmpty().flatMap { it.items }.mapNotNull { it.openId }.filter { it in unreadIds }.distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun markAllRead() {
        val ids = markAllIds.value
        if (ids.isNotEmpty()) viewModelScope.launch { deps.watchQueue.perform(WatchAction.MARK_READ, ids) }
    }

    /** Threads with unread posts, for "N unread" in the title. */
    val unreadThreads: StateFlow<Int> = deps.watched.watched(unreadOnly = true)
        .map { cached -> cached.value.count { it.unreadCount > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private var refreshedVisit: Int? = null

    /**
     * Watched is checked the first time it comes on screen in each visit to the app (opening it or returning to it
     * from the background), in the background while the cached list shows. Coming back from a thread uses the cache
     * unless it is older than the usual 10 minutes: a refresh after every thread was slow on older phones.
     */
    fun onShown() {
        val visit = visits.current
        refresh(force = visit != refreshedVisit)
        refreshedVisit = visit
    }

    fun showUnreadOnly(unread: Boolean) {
        _unreadOnly.value = unread
        refresh(force = false)
    }

    override suspend fun fetch(force: Boolean) {
        deps.watched.refresh(_unreadOnly.value, force)
    }
}

@Composable
fun WatchedScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: WatchedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unreadOnly by viewModel.unreadOnly.collectAsStateWithLifecycle()
    val selected = LocalSelectedThread.current
    LaunchedEffect(selected) { viewModel.keep(selected) }
    val unread by viewModel.unreadThreads.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val allMatches by viewModel.allMatches.collectAsStateWithLifecycle()
    val markAll by viewModel.markAllIds.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose {}
    }
    ThreadListScaffold(
        title = stringResource(R.string.watched_title),
        navIcon = navIcon,
        nav = nav,
        viewModel = viewModel,
        state = state,
        emptyText = stringResource(if (unreadOnly) R.string.empty_watched_unread else R.string.empty_watched),
        subtitle = unread.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.watched_unread, it, it) },
        actions = { WatchedMenu(markAll.size, finding = !query.isNullOrBlank(), viewModel::markAllRead) },
        subBar = { WatchedTabs(unreadOnly, unread, viewModel::showUnreadOnly) },
        findEmptyAction = allMatches?.let { all ->
            {
                OutlinedButton(onClick = { viewModel.showUnreadOnly(false) }) {
                    Text(all.count?.let { pluralStringResource(R.plurals.find_show_in_all, it, it) } ?: stringResource(R.string.find_look_in_all))
                }
            }
        },
    )
}

/** Watched's ⋮: Mark all as read, which while finding marks only the [count] threads shown. */
@Composable
private fun WatchedMenu(count: Int, finding: Boolean, onMarkAll: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(if (finding) pluralStringResource(R.plurals.mark_shown_read, count, count) else stringResource(R.string.mark_all_read)) },
            enabled = count > 0,
            onClick = {
                open = false
                onMarkAll()
            },
        )
    }
}

/** Unread/All: Material tabs, or pills in a track with the unread count ([Tabs.PILLS]). */
@Composable
private fun WatchedTabs(unreadOnly: Boolean, unread: Int, onSelect: (Boolean) -> Unit) {
    val layout = LocalAppStyle.current.layout
    if (layout.tabs != Tabs.PILLS) {
        PrimaryTabRow(selectedTabIndex = if (unreadOnly) 0 else 1) {
            Tab(selected = unreadOnly, onClick = { onSelect(true) }, text = { Text(stringResource(R.string.watched_tab_unread)) })
            Tab(selected = !unreadOnly, onClick = { onSelect(false) }, text = { Text(stringResource(R.string.watched_tab_all)) })
        }
        return
    }
    val track = RoundedCornerShape(layout.cornerDp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = layout.gutterDp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, track).padding(4.dp).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(true, false).forEach { onlyUnread ->
            val selected = unreadOnly == onlyUnread
            Tab(
                selected = selected,
                onClick = { onSelect(onlyUnread) },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape((layout.corner - 4).coerceAtLeast(0).dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(if (onlyUnread) R.string.watched_tab_unread else R.string.watched_tab_all))
                        // The title already says "N unread", which TalkBack reads; the count here is visual.
                        if (onlyUnread && unread > 0) {
                            Badge(Modifier.clearAndSetSemantics {}, containerColor = MaterialTheme.colorScheme.primary) { Text(unread.toString()) }
                        }
                    }
                },
            )
        }
    }
}
