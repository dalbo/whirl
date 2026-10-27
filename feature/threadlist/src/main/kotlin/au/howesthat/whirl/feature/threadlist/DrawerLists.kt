package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import au.howesthat.whirl.core.data.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import au.howesthat.whirl.core.data.Cached
import au.howesthat.whirl.core.data.LatestThreadsRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.RecentRepository
import au.howesthat.whirl.core.data.ScrapedList
import au.howesthat.whirl.core.data.ScrapedListRepository
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.LocalAppStyle
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.Tabs
import au.howesthat.whirl.core.ui.whirlTopBarColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** Sections plus when they were fetched (null = never). */
data class ListState(val sections: List<Section>?, val updatedAt: Instant?)

private fun <T> Flow<Cached<T>?>.toListState(sections: (T) -> List<Section>): Flow<ListState> =
    map { cached -> ListState(cached?.takeIf { it.updatedAt != null }?.value?.let(sections), cached?.updatedAt) }

private fun ListViewModel.stateOf(flow: Flow<ListState>): StateFlow<ListState> =
    flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState(null, null))

// --- Latest activity ---------------------------------------------------------------------------------------------

@HiltViewModel
class LatestViewModel @Inject constructor(deps: ListDeps, private val repo: LatestThreadsRepository) :
    ListViewModel(deps, OpenedFrom.LIST) {
    val state = stateOf(repo.threads.toListState { list -> groupByForum(list.map { it.toItem() }) })

    init {
        refresh(force = false)
    }

    override suspend fun fetch(force: Boolean) {
        repo.refresh(force)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: LatestViewModel = hiltViewModel()) {
    SimpleList(stringResource(R.string.latest_title), navIcon, nav, viewModel, viewModel.state, stringResource(R.string.empty_latest))
}

// --- Recent threads ----------------------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecentViewModel @Inject constructor(private val deps: ListDeps, private val repo: RecentRepository) :
    ListViewModel(deps, OpenedFrom.RECENT) {

    val state = stateOf(
        combine(deps.settings.settings, deps.settings.account) { s, a -> Triple(s.recentHistoryDays, s.ignoreOwnRepliesRecent, a?.userId) }
            .distinctUntilChanged()
            .flatMapLatest { (days, ignoreOwn, me) ->
                repo.threads(days).toListState { list ->
                    // 2.14.12: "ignore my own replies" hides threads whose last post is mine.
                    groupByForum(list.filterNot { ignoreOwn && it.lastPoster.id == me }.map { it.toItem() })
                }
            },
    )

    init {
        refresh(force = false)
        // A changed history length is a different list.
        viewModelScope.launch { deps.settings.settings.map { it.recentHistoryDays }.distinctUntilChanged().drop(1).collect { refresh(force = false) } }
    }

    /** How far back the list goes; the picker on the screen changes the saved setting. */
    val days: StateFlow<Int> = deps.settings.settings.map { it.recentHistoryDays }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings().recentHistoryDays)

    fun selectDays(days: Int) {
        viewModelScope.launch { deps.settings.update { it.copy(recentHistoryDays = days) } }
    }

    override suspend fun fetch(force: Boolean) {
        repo.refresh(deps.settings.settings.first().recentHistoryDays, force)
    }
}

@Composable
fun RecentScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: RecentViewModel = hiltViewModel()) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    SimpleList(
        stringResource(R.string.recent_title), navIcon, nav, viewModel, viewModel.state, stringResource(R.string.empty_recent),
        subBar = { DaysPicker(days, viewModel::selectDays) },
    )
}

@Composable
private fun DaysPicker(selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Surface(tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val choose = stringResource(R.string.choose_recent_days)
            TextButton(onClick = { open = true }, Modifier.semantics { onClick(label = choose, action = null) }) {
                Text(pluralStringResource(R.plurals.recent_days, selected, selected))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                Settings.RECENT_DAYS.forEach { d ->
                    DropdownMenuItem(text = { Text(pluralStringResource(R.plurals.recent_days, d, d)) }, onClick = { open = false; onSelect(d) })
                }
            }
        }
    }
}

// --- Popular -----------------------------------------------------------------------------------------------------

@HiltViewModel
class PopularViewModel @Inject constructor(deps: ListDeps, private val repo: ScrapedListRepository) :
    ListViewModel(deps, OpenedFrom.LIST) {
    val state = stateOf(repo.observe(ScrapedList.Popular).toListState { page -> sectionsOf(page.rows.map { it.toItem(null) }, sectioned = true) })

    init {
        refresh(force = false)
    }

    override suspend fun fetch(force: Boolean) {
        repo.refresh(ScrapedList.Popular, force)
    }
}

@Composable
fun PopularScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: PopularViewModel = hiltViewModel()) {
    SimpleList(
        stringResource(R.string.popular_title), navIcon, nav, viewModel, viewModel.state, stringResource(R.string.empty_popular),
        browserUrl = WhirlpoolUrls().popular().toString(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleList(
    title: String,
    navIcon: NavIcon,
    nav: ThreadListNav,
    viewModel: ListViewModel,
    stateFlow: StateFlow<ListState>,
    emptyText: String,
    browserUrl: String? = null,
    /** Shown under the top bar, like the forum screen's group and page pickers. */
    subBar: (@Composable () -> Unit)? = null,
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = whirlTopBarColors(),
                    title = { ListTitle(title, state.updatedAt) },
                    navigationIcon = { NavIconButton(navIcon) },
                    actions = { RefreshButton { viewModel.refresh() } },
                )
                subBar?.invoke()
            }
        },
    ) { padding ->
        ThreadListBody(
            sections = state.sections,
            refreshing = refreshing,
            error = error,
            onRefresh = { viewModel.refresh() },
            viewModel = viewModel,
            nav = nav,
            emptyText = emptyText,
            browserUrl = browserUrl,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
internal fun RefreshButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
}

// --- Watched threads ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WatchedViewModel @Inject constructor(deps: ListDeps, private val repo: WatchedRepository, private val visits: AppVisits) :
    ListViewModel(deps, OpenedFrom.WATCHED) {

    private val _unreadOnly = MutableStateFlow(true)
    /** The Unread/All tab (2.14.12 replaced 1.8's "hide read" setting with these tabs). */
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    val state = stateOf(_unreadOnly.flatMapLatest { unread -> repo.watched(unread).toListState { list -> groupByForum(list.map { it.toItem() }) } })

    /** Threads with unread posts, for "N unread" in the title. */
    val unreadThreads: StateFlow<Int> = repo.watched(unreadOnly = true)
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
        repo.refresh(_unreadOnly.value, force)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchedScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: WatchedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unreadOnly by viewModel.unreadOnly.collectAsStateWithLifecycle()
    val unread by viewModel.unreadThreads.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onShown()
        onPauseOrDispose {}
    }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = whirlTopBarColors(),
                    title = {
                        ListTitle(
                            stringResource(R.string.watched_title),
                            state.updatedAt,
                            subtitle = unread.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.watched_unread, it, it) },
                        )
                    },
                    navigationIcon = { NavIconButton(navIcon) },
                    actions = { RefreshButton { viewModel.refresh() } },
                )
                WatchedTabs(unreadOnly, unread, viewModel::showUnreadOnly)
            }
        },
    ) { padding ->
        ThreadListBody(
            sections = state.sections,
            refreshing = refreshing,
            error = error,
            onRefresh = { viewModel.refresh() },
            viewModel = viewModel,
            nav = nav,
            emptyText = stringResource(if (unreadOnly) R.string.empty_watched_unread else R.string.empty_watched),
            modifier = Modifier.padding(padding),
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

