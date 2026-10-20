package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.ScrapedList
import au.howesthat.whirl.core.data.ScrapedListRepository
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** Thread-title search, optionally scoped to a forum (and group), as the forum screen's search box did in 2.14.12. */
@Serializable
data class SearchRoute(val query: String = "", val forumId: Int? = null, val groupId: Int? = null, val scopeTitle: String? = null)

/**
 * Search results in list view (`/forum/?action=search&o=list`), the thread table 2.14.12 parsed: 100 per page, with
 * forum section headers. The page has no usable page count, so "Next" shows while a page is full.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    deps: ListDeps,
    private val repo: ScrapedListRepository,
) : ListViewModel(deps, OpenedFrom.LIST) {
    private val route = savedState.toRoute<SearchRoute>()

    private val _text = MutableStateFlow(route.query)
    val text: StateFlow<String> = _text.asStateFlow()

    private val _scoped = MutableStateFlow(route.forumId != null)
    /** Whether the search is limited to the forum it was opened from. */
    val scoped: StateFlow<Boolean> = _scoped.asStateFlow()
    val scopeTitle: String? = route.scopeTitle

    private val _search = MutableStateFlow(route.query.takeIf(String::isNotBlank)?.let { list(it, 1) })
    val search: StateFlow<ScrapedList.Search?> = _search.asStateFlow()

    val state: StateFlow<ListState> = _search.flatMapLatest { search ->
        search?.let { s -> repo.observe(s).map { cached -> ListState(cached?.value?.rows?.let { rows -> sectionsOf(rows.map { it.toItem(null) }, sectioned = true) }, cached?.updatedAt) } }
            ?: flowOf(ListState(null, null))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState(null, null))

    /** True when the page shown is full, so there may be another. */
    val hasNext: StateFlow<Boolean> = state.map { s -> (s.sections?.sumOf { it.items.size } ?: 0) >= PAGE_SIZE }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        if (_search.value != null) refresh(force = false)
    }

    private fun list(query: String, page: Int) = ScrapedList.Search(
        query = query.trim(),
        forumId = route.forumId.takeIf { _scoped.value },
        groupId = route.groupId.takeIf { _scoped.value },
        page = page,
    )

    fun setText(value: String) {
        _text.value = value
    }

    fun setScoped(scoped: Boolean) {
        _scoped.value = scoped
        if (_search.value != null) submit()
    }

    fun submit() {
        val query = _text.value.trim().takeIf(String::isNotEmpty) ?: return
        _search.value = list(query, 1)
        refresh(force = false)
    }

    fun page(delta: Int) {
        val current = _search.value ?: return
        _search.value = current.copy(page = (current.page + delta).coerceAtLeast(1))
        refresh(force = false)
    }

    fun browserUrl(): String? = _search.value?.let { WhirlpoolUrls().searchList(it.query, it.forumId, it.groupId, it.searchIn, it.page).toString() }

    override suspend fun fetch(force: Boolean) {
        _search.value?.let { repo.refresh(it, force) }
    }

    private companion object {
        const val PAGE_SIZE = 100
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: SearchViewModel = hiltViewModel()) {
    val text by viewModel.text.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val scoped by viewModel.scoped.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasNext by viewModel.hasNext.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (search == null) focus.requestFocus() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        TextField(
                            value = text,
                            onValueChange = viewModel::setText,
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            placeholder = { Text(stringResource(R.string.search_hint)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { viewModel.submit() }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                        )
                    },
                    navigationIcon = { NavIconButton(navIcon) },
                    actions = { IconButton(onClick = { viewModel.submit() }) { Icon(Icons.Default.Search, stringResource(R.string.search)) } },
                )
                viewModel.scopeTitle?.let { title ->
                    Row(Modifier.padding(horizontal = 16.dp)) {
                        InputChip(
                            selected = scoped,
                            onClick = { viewModel.setScoped(!scoped) },
                            label = { Text(stringResource(if (scoped) R.string.search_in else R.string.search_everywhere, title)) },
                            trailingIcon = if (scoped) {
                                { Icon(Icons.Default.Close, stringResource(R.string.search_everywhere_action)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (search == null) return@Scaffold
        ThreadListBody(
            sections = state.sections,
            refreshing = refreshing,
            error = error,
            onRefresh = { viewModel.refresh() },
            viewModel = viewModel,
            nav = nav,
            emptyText = stringResource(R.string.search_none),
            browserUrl = viewModel.browserUrl(),
            modifier = Modifier.padding(padding),
            footer = {
                val page = search?.page ?: 1
                if (page > 1 || hasNext) {
                    item(key = "pages") {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(onClick = { viewModel.page(-1) }, enabled = page > 1) { Text(stringResource(R.string.previous_page)) }
                            Text(stringResource(R.string.page_n, page))
                            OutlinedButton(onClick = { viewModel.page(1) }, enabled = hasNext) { Text(stringResource(R.string.next_page)) }
                        }
                    }
                }
            },
        )
    }
}
