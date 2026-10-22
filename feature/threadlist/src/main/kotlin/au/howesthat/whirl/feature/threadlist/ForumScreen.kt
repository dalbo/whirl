package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import au.howesthat.whirl.core.data.ForumThreadsRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.ScrapedList
import au.howesthat.whirl.core.data.ScrapedListRepository
import au.howesthat.whirl.core.model.ForumGroup
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.PagePickerDialog
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** A forum's thread list, optionally narrowed to a group. */
@Serializable
data class ForumRoute(val forumId: Int, val title: String, val groupId: Int? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ForumViewModel @Inject constructor(
    savedState: SavedStateHandle,
    deps: ListDeps,
    private val scraped: ScrapedListRepository,
    private val apiThreads: ForumThreadsRepository,
) : ListViewModel(deps, OpenedFrom.LIST) {
    val route = savedState.toRoute<ForumRoute>()
    private val forumId = route.forumId

    /** The route's title, or (for links, which carry none) the forum's name from the forum list or the page. */
    val title: StateFlow<String> = combine(
        deps.forums.forums.map { cached -> cached.value.firstOrNull { it.forum.id == forumId }?.forum?.title },
        scraped.observe(ScrapedList.ForumPage(forumId, 1, route.groupId)).map { it?.value?.forum?.title },
    ) { listed, scrapedTitle -> route.title.ifBlank { listed ?: scrapedTitle.orEmpty() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, route.title)

    private val _group = MutableStateFlow(route.groupId)
    val group: StateFlow<Int?> = _group.asStateFlow()

    private val _groups = MutableStateFlow<List<ForumGroup>>(emptyList())
    /** The forum's groups, learned from its pages (empty for forums without groups). */
    val groups: StateFlow<List<ForumGroup>> = _groups.asStateFlow()

    private val _pageCount = MutableStateFlow(1)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    /** The page on screen; refresh and errors apply to it. */
    private val currentPage = MutableStateFlow(1)

    /** Private forums (known, or learned from "Not signed in") list from the API and open threads in the browser. */
    val isPrivate: StateFlow<Boolean> = privateIds.map { forumId in it }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val apiList = apiThreads.threads(forumId)

    // Scraped pages always look read; the API's UNREAD flag covers the forum's 100 most recently active threads.
    private val unreadIds: StateFlow<Set<ThreadId>> = apiList
        .map { cached -> cached.value.filter { it.isUnread }.mapTo(mutableSetOf()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val privateState: StateFlow<ListState> = apiList
        .map { cached -> ListState(cached.updatedAt?.let { listOf(Section(null, cached.value.map { it.toItem() })) }, cached.updatedAt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState(null, null))

    fun page(page: Int): Flow<ListState> = _group.flatMapLatest { group ->
        combine(scraped.observe(ScrapedList.ForumPage(forumId, page, group)), unreadIds) { cached, unread ->
            ListState(cached?.value?.rows?.map { it.toItem(cached.value.forum, unread) }?.let { listOf(Section(null, it)) }, cached?.updatedAt)
        }
    }

    init {
        refresh(force = false)
        // A forum found to be private switches to the API list.
        viewModelScope.launch { isPrivate.drop(1).distinctUntilChanged().collect { refresh(force = false) } }
    }

    fun showPage(page: Int) {
        if (currentPage.value == page) return
        currentPage.value = page
        refresh(force = false)
    }

    fun selectGroup(group: Int?) {
        if (_group.value == group) return
        _group.value = group
        _pageCount.value = 1
        currentPage.value = 1
        refresh(force = false)
    }

    fun browserUrl(): String = WhirlpoolUrls().forum(forumId, currentPage.value, _group.value).toString()

    /** Posting stays in the browser, where the user is logged in to Whirlpool. */
    fun newThreadUrl(): String = WhirlpoolUrls().newThread(forumId).toString()

    override suspend fun fetch(force: Boolean) {
        if (isPrivate.value) {
            apiThreads.refresh(forumId, force)
            return
        }
        val list = ScrapedList.ForumPage(forumId, currentPage.value, _group.value)
        scraped.refresh(list, force)
        scraped.observe(list).first()?.value?.let { page ->
            _pageCount.value = page.pageCount
            if (page.groups.isNotEmpty()) _groups.value = page.groups
        }
        // The unread overlay is a nicety: a failure here must not hide the page.
        runCatching { apiThreads.refresh(forumId, force) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: ForumViewModel = hiltViewModel()) {
    val isPrivate by viewModel.isPrivate.collectAsStateWithLifecycle()
    val group by viewModel.group.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val pageCount by viewModel.pageCount.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { if (isPrivate) 1 else pageCount }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(pager.settledPage) { viewModel.showPage(pager.settledPage + 1) }
    LaunchedEffect(group) { pager.scrollToPage(0) }

    val groupName = groups.firstOrNull { it.id == group }?.name
    val title by viewModel.title.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = whirlTopBarColors(),
                    title = { ListTitle(title, updatedAt = null, subtitle = groupName) },
                    navigationIcon = { NavIconButton(navIcon) },
                    actions = {
                        // Private forums search in the browser, where the user is logged in; offer the browser instead.
                        if (!isPrivate) {
                            IconButton(onClick = {
                                nav.openSearch(SearchRoute(forumId = viewModel.route.forumId, groupId = group, scopeTitle = groupName ?: title))
                            }) { Icon(Icons.Default.Search, stringResource(R.string.search)) }
                        }
                        RefreshButton { viewModel.refresh() }
                        Overflow(onNewThread = { browser(viewModel.newThreadUrl()) }, onOpenInBrowser = { browser(viewModel.browserUrl()) })
                    },
                )
                if (!isPrivate && (groups.isNotEmpty() || pageCount > 1)) {
                    Surface(tonalElevation = 2.dp) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (groups.isNotEmpty()) GroupPicker(groups, group, viewModel::selectGroup, Modifier.weight(1f))
                            else Row(Modifier.weight(1f)) {}
                            if (pageCount > 1) {
                                // Announced when a swipe changes the page.
                                TextButton(
                                    onClick = { picking = true },
                                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                ) {
                                    Text(stringResource(au.howesthat.whirl.core.ui.R.string.page_of, pager.currentPage + 1, pageCount))
                                }
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        if (isPrivate) {
            val state by viewModel.privateState.collectAsStateWithLifecycle()
            ThreadListBody(
                sections = state.sections,
                refreshing = refreshing,
                error = error,
                onRefresh = { viewModel.refresh() },
                viewModel = viewModel,
                nav = nav,
                emptyText = stringResource(R.string.empty_private_forum),
                browserUrl = viewModel.browserUrl(),
                modifier = Modifier.padding(padding),
            )
        } else {
            HorizontalPager(pager, Modifier.padding(padding).fillMaxSize(), key = { it }) { index ->
                val pageState by remember(index, viewModel) { viewModel.page(index + 1) }.collectAsStateWithLifecycle(ListState(null, null))
                val current = index == pager.settledPage
                ThreadListBody(
                    sections = pageState.sections,
                    refreshing = refreshing && current,
                    // Errors belong to the page being shown.
                    error = error.takeIf { current },
                    onRefresh = { viewModel.refresh() },
                    viewModel = viewModel,
                    nav = nav,
                    emptyText = stringResource(R.string.empty_forum),
                    browserUrl = viewModel.browserUrl(),
                )
            }
        }
    }

    if (picking) {
        PagePickerDialog(
            pageCount = pageCount,
            current = pager.currentPage + 1,
            onDismiss = { picking = false },
            onPick = { page ->
                picking = false
                scope.launch { pager.scrollToPage(page - 1) }
            },
        )
    }
}

@Composable
private fun GroupPicker(groups: List<ForumGroup>, selected: Int?, onSelect: (Int?) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier) {
        val choose = stringResource(R.string.choose_group)
        TextButton(onClick = { open = true }, Modifier.semantics { onClick(label = choose, action = null) }) {
            Text(
                groups.firstOrNull { it.id == selected }?.name ?: stringResource(R.string.all_groups),
                Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.all_groups)) }, onClick = { open = false; onSelect(null) })
            groups.forEach { g -> DropdownMenuItem(text = { Text(g.name) }, onClick = { open = false; onSelect(g.id) }) }
        }
    }
}

@Composable
private fun Overflow(onNewThread: () -> Unit, onOpenInBrowser: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(text = { Text(stringResource(R.string.new_thread)) }, onClick = { open = false; onNewThread() })
        DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser)) }, onClick = { open = false; onOpenInBrowser() })
    }
}
