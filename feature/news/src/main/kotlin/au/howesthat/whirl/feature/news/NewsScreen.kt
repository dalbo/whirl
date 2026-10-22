package au.howesthat.whirl.feature.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.NewsRepository
import au.howesthat.whirl.core.model.NewsItem
import au.howesthat.whirl.core.ui.ErrorState
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.UpdatedAgo
import au.howesthat.whirl.core.ui.errorMessage
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
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import javax.inject.Inject

/** Where a news item opens: Whirlpool's redirect to the article. */
fun newsUrl(item: NewsItem) = "https://whirlpool.net.au/news/go.cfm?article=${item.id}"

/** A day's news, newest day first, as 2.14.12 grouped it. */
data class NewsDay(val date: LocalDate, val items: List<NewsItem>)

fun groupByDay(items: List<NewsItem>, zone: ZoneId): List<NewsDay> =
    items.sortedByDescending { it.date }
        .groupBy { it.date.atZoneSameInstant(zone).toLocalDate() }
        .map { (date, list) -> NewsDay(date, list) }

data class NewsState(val days: List<NewsDay>?, val updatedAt: Instant?)

@HiltViewModel
class NewsViewModel @Inject constructor(private val repo: NewsRepository) : ViewModel() {
    val state: StateFlow<NewsState> = repo.news
        .map { NewsState(it.updatedAt?.let { _ -> groupByDay(it.value, ZoneId.systemDefault()) }, it.updatedAt) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NewsState(null, null))

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(navIcon: NavIcon, viewModel: NewsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val browser = rememberOpenInBrowser()
    Scaffold(
        topBar = {
            TopAppBar(
                colors = whirlTopBarColors(),
                title = { Text(stringResource(R.string.news_title)) },
                navigationIcon = { NavIconButton(navIcon) },
                actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) } },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { viewModel.refresh() }, modifier = Modifier.padding(padding).fillMaxSize()) {
            val failed = error
            val days = state.days
            if (days == null && failed != null) {
                ErrorState(failed, onRetry = { viewModel.refresh() }, modifier = Modifier.fillMaxWidth(), onOpenInBrowser = { browser("https://whirlpool.net.au/news/") })
            } else {
                val today = LocalDate.now()
                LazyColumn(Modifier.fillMaxSize()) {
                    item(key = "updated") {
                        UpdatedAgo(state.updatedAt, Instant.now(), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                        failed?.let { Text(errorMessage(it), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error) }
                    }
                    days.orEmpty().forEach { day ->
                        item(key = "d-${day.date}") { DayHeader(dayLabel(day.date, today)) }
                        items(day.items, key = { it.id }) { item ->
                            ListItem(
                                modifier = Modifier.clickable { browser(newsUrl(item)) },
                                overlineContent = { Text(item.source, maxLines = 1) },
                                headlineContent = { Text(item.title) },
                                supportingContent = item.blurb.takeIf { it.isNotBlank() }?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate, today: LocalDate): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (date) {
        today -> stringResource(R.string.today)
        today.minusDays(1) -> stringResource(R.string.yesterday)
        else -> if (date.isAfter(today.minusDays(7))) {
            date.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
        } else {
            DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(date)
        }
    }
}

@Composable
private fun DayHeader(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, Modifier.fillMaxWidth().semantics { heading() }.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}
