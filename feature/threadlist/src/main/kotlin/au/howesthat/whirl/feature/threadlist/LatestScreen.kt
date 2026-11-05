package au.howesthat.whirl.feature.threadlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.LatestThreadsRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.ui.NavIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class LatestViewModel @Inject constructor(deps: ListDeps, private val repo: LatestThreadsRepository) :
    ListViewModel(deps, OpenedFrom.LIST) {
    val state = stateOf(repo.threads.toListState { list -> groupByForum(list.map { it.toItem() }) }.filtered())

    init {
        refresh(force = false)
    }

    override suspend fun fetch(force: Boolean) {
        repo.refresh(force)
    }
}

@Composable
fun LatestScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: LatestViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ThreadListScaffold(stringResource(R.string.latest_title), navIcon, nav, viewModel, state, stringResource(R.string.empty_latest))
}
