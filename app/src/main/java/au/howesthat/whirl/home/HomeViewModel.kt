package au.howesthat.whirl.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.AccountRepository
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.LatestThreadsRepository
import au.howesthat.whirl.core.data.NewsRepository
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.network.WhirlpoolApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Temporary Phase 1 home: proves sign-in, the API and the cache on a device. Phase 2 replaces it with the drawer and
 * the real list screens.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    accounts: AccountRepository,
    api: WhirlpoolApi,
    private val forumRepo: ForumRepository,
    private val watchedRepo: WatchedRepository,
    private val latestRepo: LatestThreadsRepository,
    private val newsRepo: NewsRepository,
) : ViewModel() {
    val account = accounts.account.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val alert = api.alert
    val forums = forumRepo.forums.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val watched = watchedRepo.watched(unreadOnly = true).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val latest = latestRepo.threads.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val news = newsRepo.news.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _errors = MutableStateFlow<Map<String, Throwable>>(emptyMap())
    val errors: StateFlow<Map<String, Throwable>> = _errors.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val jobs = mapOf<String, suspend () -> Unit>(
                FORUMS to { forumRepo.refresh(force) },
                WATCHED to { watchedRepo.refresh(unreadOnly = true, force = force) },
                LATEST to { latestRepo.refresh(force) },
                NEWS to { newsRepo.refresh(force) },
            )
            val results = jobs.map { (name, job) -> async { name to runCatching { job() }.exceptionOrNull() } }.awaitAll()
            _errors.value = results.mapNotNull { (name, e) -> e?.let { name to it } }.toMap()
            _refreshing.value = false
        }
    }

    companion object {
        const val FORUMS = "forums"
        const val WATCHED = "watched"
        const val LATEST = "latest"
        const val NEWS = "news"
    }
}
