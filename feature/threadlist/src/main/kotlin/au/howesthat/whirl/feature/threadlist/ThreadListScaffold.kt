package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.Cached
import au.howesthat.whirl.core.ui.FindBar
import au.howesthat.whirl.core.ui.FindButton
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.OnFindShortcut
import au.howesthat.whirl.core.ui.whirlTopBarColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant

/** Sections plus when they were fetched (null = never). */
data class ListState(val sections: List<Section>?, val updatedAt: Instant?)

internal fun <T> Flow<Cached<T>?>.toListState(sections: (T) -> List<Section>): Flow<ListState> =
    map { cached -> ListState(cached?.takeIf { it.updatedAt != null }?.value?.let(sections), cached?.updatedAt) }

internal fun ListViewModel.stateOf(flow: Flow<ListState>): StateFlow<ListState> =
    flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState(null, null))

/**
 * A thread list screen: its top bar (title, "Updated N ago" after [subtitle], find, refresh and [actions]), find in
 * list's bar in its place while finding (with the same [actions]), [subBar] below, and the list. [canFind] false leaves
 * find out (a user's profile).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadListScaffold(
    title: String,
    navIcon: NavIcon,
    nav: ThreadListNav,
    viewModel: ListViewModel,
    state: ListState,
    emptyText: String,
    subtitle: String? = null,
    canFind: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    /** Shown under the top bar, like the forum screen's group and page pickers. */
    subBar: (@Composable () -> Unit)? = null,
    browserUrl: String? = null,
    findEmptyAction: (@Composable () -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val findQuery = query.takeIf { canFind }
    Scaffold(
        topBar = {
            Column {
                val titleBar = @Composable {
                    TopAppBar(
                        colors = whirlTopBarColors(),
                        title = { ListTitle(title, state.updatedAt, subtitle) },
                        navigationIcon = { NavIconButton(navIcon) },
                        actions = {
                            if (canFind) FindButton { viewModel.setQuery("") }
                            RefreshButton { viewModel.refresh() }
                            actions()
                        },
                    )
                }
                if (canFind) ListTopBar(title, viewModel, nav, findQuery, state.sections, findActions = actions, titleBar = titleBar) else titleBar()
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
            header = header,
            findQuery = findQuery,
            findEmptyAction = findEmptyAction,
        )
    }
}

/**
 * [titleBar], or Find in list's bar while [query] is not null. Cross-forum lists search all of Whirlpool from its chip;
 * a forum page passes its own scope.
 */
@Composable
internal fun ListTopBar(
    title: String,
    viewModel: ListViewModel,
    nav: ThreadListNav,
    query: String?,
    sections: List<Section>?,
    searchRoute: ((String) -> SearchRoute)? = { SearchRoute(query = it) },
    findActions: @Composable RowScope.() -> Unit = {},
    titleBar: @Composable () -> Unit,
) {
    // With a thread open beside the list, the shortcut finds in the thread.
    OnFindShortcut(enabled = LocalSelectedThread.current == null) { if (query == null) viewModel.setQuery("") }
    if (query == null) {
        titleBar()
    } else {
        FindBar(
            query = query,
            onQueryChange = viewModel::setQuery,
            onClose = { viewModel.setQuery(null) },
            label = stringResource(au.howesthat.whirl.core.ui.R.string.find_in, title),
            matches = sections?.sumOf { it.items.size },
            onSearchWhirlpool = searchRoute?.let { route -> { words -> nav.openSearch(route(words)) } },
            closeOnBack = LocalSelectedThread.current == null,
            actions = findActions,
        )
    }
}

@Composable
internal fun RefreshButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
}
