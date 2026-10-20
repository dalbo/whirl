package au.howesthat.whirl.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.R
import au.howesthat.whirl.core.data.Cached
import au.howesthat.whirl.core.ui.UpdatedAgo
import au.howesthat.whirl.core.ui.errorMessage
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenSettings: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    val forums by viewModel.forums.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val news by viewModel.news.collectAsStateWithLifecycle()
    val errors by viewModel.errors.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val now = Instant.now()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    account?.let { Text(stringResource(R.string.home_logged_in_as, it.userName, it.userId), style = MaterialTheme.typography.titleMedium) }
                    if (alert.isNotBlank()) Text(alert, color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.home_phase1_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item {
                    Section(stringResource(R.string.home_forums), forums, errors[HomeViewModel.FORUMS], now) { list ->
                        listOf(pluralStringResource(R.plurals.home_forums_count, list.size, list.size))
                    }
                }
                item {
                    Section(stringResource(R.string.home_watched_unread), watched, errors[HomeViewModel.WATCHED], now) { list ->
                        list.map { pluralStringResource(R.plurals.home_watched_row, it.unreadCount, it.title, it.unreadCount, it.lastPage) }
                    }
                }
                item {
                    Section(stringResource(R.string.home_latest), latest, errors[HomeViewModel.LATEST], now) { list ->
                        list.take(10).map { "${it.title} · ${it.forumName}" }
                    }
                }
                item {
                    Section(stringResource(R.string.home_news), news, errors[HomeViewModel.NEWS], now) { list ->
                        list.take(5).map { it.title }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> Section(title: String, cached: Cached<List<T>>?, error: Throwable?, now: Instant, lines: @Composable (List<T>) -> List<String>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            UpdatedAgo(cached?.updatedAt, now)
            error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
            cached?.value?.let { list -> lines(list).forEach { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        }
    }
}
