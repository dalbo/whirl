package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.Cached
import au.howesthat.whirl.core.data.LatestThreadsRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.RecentRepository
import au.howesthat.whirl.core.data.ScrapedList
import au.howesthat.whirl.core.data.ScrapedListRepository
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
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

    override suspend fun fetch(force: Boolean) {
        repo.refresh(deps.settings.settings.first().recentHistoryDays, force)
    }
}

@Composable
fun RecentScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: RecentViewModel = hiltViewModel()) {
    SimpleList(stringResource(R.string.recent_title), navIcon, nav, viewModel, viewModel.state, stringResource(R.string.empty_recent))
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
) {
    val state by stateFlow.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { ListTitle(title, state.updatedAt) },
                navigationIcon = { NavIconButton(navIcon) },
                actions = { RefreshButton { viewModel.refresh() } },
            )
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
class WatchedViewModel @Inject constructor(deps: ListDeps, private val repo: WatchedRepository) :
    ListViewModel(deps, OpenedFrom.WATCHED) {

    private val _unreadOnly = MutableStateFlow(true)
    /** The Unread/All tab (2.14.12 replaced 1.8's "hide read" setting with these tabs). */
    val unreadOnly: StateFlow<Boolean> = _unreadOnly.asStateFlow()

    val state = stateOf(_unreadOnly.flatMapLatest { unread -> repo.watched(unread).toListState { list -> groupByForum(list.map { it.toItem() }) } })

    /** Threads with unread posts, for "N unread" in the title. */
    val unreadThreads: StateFlow<Int> = repo.watched(unreadOnly = true)
        .map { cached -> cached.value.count { it.unreadCount > 0 } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        refresh(force = false)
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
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
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
                PrimaryTabRow(selectedTabIndex = if (unreadOnly) 0 else 1) {
                    Tab(selected = unreadOnly, onClick = { viewModel.showUnreadOnly(true) }, text = { Text(stringResource(R.string.watched_tab_unread)) })
                    Tab(selected = !unreadOnly, onClick = { viewModel.showUnreadOnly(false) }, text = { Text(stringResource(R.string.watched_tab_all)) })
                }
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
