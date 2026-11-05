package au.howesthat.whirl.feature.threadlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.ScrapedList
import au.howesthat.whirl.core.data.ScrapedListRepository
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.NavIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class PopularViewModel @Inject constructor(deps: ListDeps, private val repo: ScrapedListRepository) :
    ListViewModel(deps, OpenedFrom.LIST) {
    val state = stateOf(repo.observe(ScrapedList.Popular).toListState { page -> sectionsOf(page.rows.map { it.toItem(null) }, sectioned = true) }.filtered())

    init {
        refresh(force = false)
    }

    override suspend fun fetch(force: Boolean) {
        repo.refresh(ScrapedList.Popular, force)
    }
}

@Composable
fun PopularScreen(navIcon: NavIcon, nav: ThreadListNav, viewModel: PopularViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ThreadListScaffold(
        stringResource(R.string.popular_title), navIcon, nav, viewModel, state, stringResource(R.string.empty_popular),
        browserUrl = WhirlpoolUrls().popular().toString(),
    )
}
