package au.howesthat.whirl.feature.threadlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.OpenThread
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadOpener
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.model.ThreadId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What every thread list needs to decide how a tap opens a thread and which watch actions a row offers. */
class ListDeps @Inject constructor(val settings: SettingsRepository, val forums: ForumRepository, val watched: WatchedRepository)

/** Refresh state, errors and thread opening shared by every thread list screen. */
abstract class ListViewModel(private val deps: ListDeps, private val from: OpenedFrom) : ViewModel() {
    private val opener = ThreadOpener()
    protected val settings: StateFlow<Settings> = deps.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    protected val privateIds: StateFlow<Set<Int>> = deps.forums.privateIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Watched threads (both lists, plus actions waiting out their Undo window), to offer Watch or Unwatch. */
    val watchedIds: StateFlow<Set<ThreadId>> = deps.watched.watchedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    /** The last refresh's failure; the cached list (if any) stays on screen. */
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    protected abstract suspend fun fetch(force: Boolean)

    init {
        // Outside Watched, the rows only know what is watched if the All list has been fetched.
        if (from != OpenedFrom.WATCHED) viewModelScope.launch { deps.watched.ensureKnown() }
    }

    private var refreshJob: Job? = null

    /**
     * Refreshes what is on screen. The latest call wins: a page swipe or tab change during a fetch cancels it, so the
     * new page always loads (ignoring calls while busy left it blank).
     */
    fun refresh(force: Boolean = true) {
        refreshJob?.cancel()
        _refreshing.value = true
        val job = viewModelScope.launch {
            try {
                fetch(force)
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e
            }
        }
        refreshJob = job
        // Only the newest job clears the indicator, not one it cancelled.
        job.invokeOnCompletion { if (refreshJob === job) _refreshing.value = false }
    }

    fun open(item: ThreadItem): OpenThread? {
        val id = item.openId ?: return null
        val forumId = item.forum?.id
        // 2.14.12 "auto mark as read": opening a watched thread from Watched marks it read, with no Undo. Not only when
        // the cached row shows unread posts: the list can be minutes old, and reading in Whirl does not move
        // Whirlpool's read position (no web session), so a thread opened on a stale "0" stayed unread on the site.
        // Threads opened from other lists or links are marked by the thread screen.
        if (from == OpenedFrom.WATCHED && settings.value.watchedAutoMarkAsRead) {
            viewModelScope.launch { deps.watched.perform(WatchAction.MARK_READ, listOf(id), undoable = false) }
        }
        return opener.open(id, forumId, forumId in privateIds.value, from, settings.value, item.watched)
    }

    /** The watch actions a row's menu offers. */
    fun watchActions(item: ThreadItem): List<WatchAction> {
        val id = item.openId ?: return emptyList()
        val watched = item.watched != null || id in watchedIds.value
        return when {
            !watched -> listOf(WatchAction.WATCH)
            item.unreadCount > 0 -> listOf(WatchAction.MARK_READ, WatchAction.UNWATCH)
            else -> listOf(WatchAction.UNWATCH)
        }
    }

    fun perform(action: WatchAction, item: ThreadItem) {
        val id = item.openId ?: return
        viewModelScope.launch { deps.watched.perform(action, listOf(id)) }
    }

    fun lastPost(item: ThreadItem): OpenThread? {
        val id = item.openId ?: return null
        val forumId = item.forum?.id
        return opener.lastPost(id, forumId, forumId in privateIds.value, settings.value)
    }

    fun browserUrl(item: ThreadItem): String = opener.browserUrl(item.openId ?: item.id)
}
