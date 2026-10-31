package au.howesthat.whirl.feature.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.UserRepository
import au.howesthat.whirl.core.model.UserProfile
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.whirlTopBarColors
import au.howesthat.whirl.feature.threadlist.ListDeps
import au.howesthat.whirl.feature.threadlist.ListState
import au.howesthat.whirl.feature.threadlist.ListTitle
import au.howesthat.whirl.feature.threadlist.ListViewModel
import au.howesthat.whirl.feature.threadlist.ThreadListBody
import au.howesthat.whirl.feature.threadlist.ThreadListNav
import au.howesthat.whirl.feature.threadlist.sectionsOf
import au.howesthat.whirl.feature.threadlist.toItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

@Serializable
data class UserRoute(val userId: Long) : NavKey

@HiltViewModel(assistedFactory = UserViewModel.Factory::class)
class UserViewModel @AssistedInject constructor(
    @Assisted route: UserRoute,
    deps: ListDeps,
    private val repo: UserRepository,
) : ListViewModel(deps, OpenedFrom.LIST) {
    @AssistedFactory
    interface Factory {
        fun create(route: UserRoute): UserViewModel
    }

    val userId = route.userId

    val profile: StateFlow<UserProfile?> = repo.profile(userId).map { it?.value }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<ListState> = repo.profile(userId)
        .map { cached -> ListState(cached?.value?.recentActivity?.let { rows -> sectionsOf(rows.map { it.toItem(null) }, sectioned = true) }, cached?.updatedAt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState(null, null))

    init {
        refresh(force = false)
    }

    fun profileUrl(): String = WhirlpoolUrls().user(userId).toString()

    override suspend fun fetch(force: Boolean) {
        repo.refresh(userId, force)
    }
}

/** A user's profile: the "About this user" lines, then their recent activity as a thread list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserScreen(
    route: UserRoute,
    navIcon: NavIcon,
    nav: ThreadListNav,
    viewModel: UserViewModel = hiltViewModel<UserViewModel, UserViewModel.Factory> { it.create(route) },
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = whirlTopBarColors(),
                title = { ListTitle(profile?.user?.name ?: stringResource(R.string.user_title), state.updatedAt, subtitle = "#${viewModel.userId}") },
                navigationIcon = { NavIconButton(navIcon) },
                actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) } },
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
            emptyText = stringResource(R.string.no_recent_activity),
            browserUrl = viewModel.profileUrl(),
            modifier = Modifier.padding(padding),
            header = {
                profile?.let { p ->
                    item(key = "about") {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.about.forEach { (label, value) ->
                                Row {
                                    Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    Text(value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            TextButton(onClick = { browser(viewModel.profileUrl()) }) { Text(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser)) }
                        }
                    }
                    item(key = "recent") {
                        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Text(
                                stringResource(R.string.recent_activity),
                                Modifier.fillMaxWidth().semantics { heading() }.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                        }
                    }
                }
            },
        )
    }
}
