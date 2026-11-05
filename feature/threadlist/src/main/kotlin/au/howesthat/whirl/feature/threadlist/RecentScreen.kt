package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.RecentRepository
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.ui.NavIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecentViewModel @Inject constructor(deps: ListDeps, private val repo: RecentRepository) :
    ListViewModel(deps, OpenedFrom.RECENT) {

    val state = stateOf(
        combine(deps.settings.settings, deps.settings.account) { s, a -> Triple(s.recentHistoryDays, s.ignoreOwnRepliesRecent, a?.userId) }
            .distinctUntilChanged()
            .flatMapLatest { (days, ignoreOwn, me) ->
                repo.threads(days).toListState { list ->
                    // 2.14.12: "ignore my own replies" hides threads whose last post is mine.
                    groupByForum(list.filterNot { ignoreOwn && it.lastPoster.id == me }.map { it.toItem() })
                }
            }
            .filtered(),
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val days by viewModel.days.collectAsStateWithLifecycle()
    ThreadListScaffold(
        stringResource(R.string.recent_title), navIcon, nav, viewModel, state, stringResource(R.string.empty_recent),
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
