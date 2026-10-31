package au.howesthat.whirl.feature.forums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.ForumListItem
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.ActionsMenu
import au.howesthat.whirl.core.ui.EmptyState
import au.howesthat.whirl.core.ui.ErrorState
import au.howesthat.whirl.core.ui.FindBar
import au.howesthat.whirl.core.ui.FindButton
import au.howesthat.whirl.core.ui.MenuAction
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.OnFindShortcut
import au.howesthat.whirl.core.ui.RowPosition
import au.howesthat.whirl.core.ui.SectionHeading
import au.howesthat.whirl.core.ui.SectionRow
import au.howesthat.whirl.core.ui.UpdatedAgo
import au.howesthat.whirl.core.ui.errorMessage
import au.howesthat.whirl.core.ui.menuClickable
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.whirlTopBarColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/** A titled group of forums; [isFavourites] marks the user's own section at the top. */
data class ForumSection(val title: String, val forums: List<ForumListItem>, val isFavourites: Boolean = false)

/** Favourites first (in API order), then the API's sections in order. */
fun forumSections(forums: List<ForumListItem>, favouritesTitle: String): List<ForumSection> {
    val favourites = forums.filter { it.isFavourite }
    val sections = forums.groupBy { it.forum.section }.map { (title, list) -> ForumSection(title, list) }
    return listOfNotNull(favourites.takeIf { it.isNotEmpty() }?.let { ForumSection(favouritesTitle, it, isFavourites = true) }) + sections
}

/**
 * Find in list: forums whose title matches [query], or every forum in a section whose title does (words may mix the
 * two); sections left empty drop.
 */
fun filterForums(sections: List<ForumSection>, query: TextQuery): List<ForumSection> =
    if (query.isBlank) sections
    else sections.map { section -> section.copy(forums = section.forums.filter { query.matches(it.forum.title, section.title) }) }
        .filter { it.forums.isNotEmpty() }

data class ForumsState(val forums: List<ForumListItem>?, val updatedAt: Instant?)

@HiltViewModel
class ForumsViewModel @Inject constructor(private val repo: ForumRepository, private val savedState: SavedStateHandle) : ViewModel() {
    /** Find in list: null while the bar is closed. Saved, so it survives process death. */
    val query: StateFlow<String?> = savedState.getStateFlow(QUERY_KEY, null)

    fun setQuery(query: String?) {
        savedState[QUERY_KEY] = query
    }

    val state: StateFlow<ForumsState> = repo.forums
        .map { ForumsState(it.value.takeIf { _ -> it.updatedAt != null || it.value.isNotEmpty() }, it.updatedAt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ForumsState(null, null))

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            _error.value = try {
                repo.refresh(force)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun setFavourite(forumId: Int, favourite: Boolean) {
        viewModelScope.launch { repo.setFavourite(forumId, favourite) }
    }

    private companion object {
        const val QUERY_KEY = "findQuery"
    }
}

/**
 * The forum list. Public forums open in the app ([onOpenForum]); private ones in the browser, where the user is logged
 * in. The find bar's chip is the app's site-wide Whirlpool search ([onSearch], with the words typed).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumsScreen(navIcon: NavIcon, onOpenForum: (ForumRef) -> Unit, onSearch: (String) -> Unit, viewModel: ForumsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    val urls = remember { WhirlpoolUrls() }
    val favourites = stringResource(R.string.favourites)
    // About 100 forums: cheap enough to filter here, where the localised "Favourites" title is known.
    val sections = remember(state.forums, favourites, query) { state.forums?.let { filterForums(forumSections(it, favourites), TextQuery(query)) } }

    OnFindShortcut { if (query == null) viewModel.setQuery("") }
    Scaffold(
        topBar = {
            val find = query
            if (find == null) {
                TopAppBar(
                    colors = whirlTopBarColors(),
                    title = { Text(stringResource(R.string.forums_title)) },
                    navigationIcon = { NavIconButton(navIcon) },
                    actions = {
                        FindButton { viewModel.setQuery("") }
                        IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    },
                )
            } else {
                FindBar(
                    query = find,
                    onQueryChange = viewModel::setQuery,
                    onClose = { viewModel.setQuery(null) },
                    label = stringResource(au.howesthat.whirl.core.ui.R.string.find_in, stringResource(R.string.forums_title)),
                    matches = sections?.sumOf { it.forums.size },
                    onSearchWhirlpool = onSearch,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.padding(padding).fillMaxSize()) {
            val failed = error
            if (sections == null && failed != null) {
                ErrorState(failed, onRetry = { viewModel.refresh() }, modifier = Modifier.fillMaxWidth(), onOpenInBrowser = { browser(urls.index().toString()) })
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "updated") {
                        UpdatedAgo(state.updatedAt, Instant.now(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        failed?.let { Text(errorMessage(it), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                    }
                    if (sections != null && sections.isEmpty() && !query.isNullOrBlank()) {
                        item(key = "empty") {
                            EmptyState(stringResource(au.howesthat.whirl.core.ui.R.string.find_none, query.orEmpty().trim()), Modifier.fillMaxWidth())
                        }
                    }
                    sections.orEmpty().forEach { section ->
                        item(key = "h-${section.isFavourites}-${section.title}") { SectionHeading(section.title) }
                        itemsIndexed(section.forums, key = { _, it -> "${section.isFavourites}-${it.forum.id}" }) { i, item ->
                            SectionRow(RowPosition.of(i, section.forums.size), flatDivider = false) {
                                ForumRow(
                                    item,
                                    onOpen = {
                                        if (item.isPrivate) browser(urls.forum(item.forum.id).toString())
                                        else onOpenForum(ForumRef(item.forum.id, item.forum.title))
                                    },
                                    onToggleFavourite = { viewModel.setFavourite(item.forum.id, !item.isFavourite) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ForumRow(item: ForumListItem, onOpen: () -> Unit, onToggleFavourite: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val actions = listOf(
        MenuAction(stringResource(if (item.isFavourite) R.string.remove_favourite else R.string.add_favourite), onClick = onToggleFavourite),
    )
    Box {
        ListItem(
            modifier = Modifier.menuClickable(
                actions,
                onLongClick = { menu = true },
                onClick = onOpen,
                onLongClickLabel = stringResource(au.howesthat.whirl.core.ui.R.string.show_actions),
            ),
            headlineContent = { Text(item.forum.title) },
            supportingContent = if (item.isPrivate) {
                { Text(stringResource(R.string.private_forum)) }
            } else {
                null
            },
            trailingContent = {
                when {
                    // The supporting line already says "Private forum".
                    item.isPrivate -> Icon(Icons.Default.Lock, contentDescription = null)
                    item.isFavourite -> Icon(Icons.Default.Star, stringResource(R.string.favourite))
                }
            },
        )
        ActionsMenu(menu, onDismiss = { menu = false }, actions)
    }
}
