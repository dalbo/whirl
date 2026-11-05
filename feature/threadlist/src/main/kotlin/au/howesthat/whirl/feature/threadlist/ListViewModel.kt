package au.howesthat.whirl.feature.threadlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.OpenThread
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadOpener
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.data.WatchQueue
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.ui.ListScreenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What every thread list needs to decide how a tap opens a thread and which watch actions a row offers, plus the
 * list's saved state (the find query).
 */
class ListDeps @Inject constructor(
    val settings: SettingsRepository,
    val forums: ForumRepository,
    val watched: WatchedRepository,
    val watchQueue: WatchQueue,
    val savedState: SavedStateHandle,
)

/** Refresh state, errors and thread opening shared by every thread list screen. */
abstract class ListViewModel(protected val deps: ListDeps, private val from: OpenedFrom) : ViewModel() {
    private val opener = ThreadOpener()
    protected val settings: StateFlow<Settings> = deps.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    protected val privateIds: StateFlow<Set<Int>> = deps.forums.privateIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Watched threads (both lists, plus actions waiting out their Undo window), to offer Watch or Unwatch. */
    val watchedIds: StateFlow<Set<ThreadId>> = deps.watched.watchedIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val screen = ListScreenState(viewModelScope, deps.savedState)
    val refreshing: StateFlow<Boolean> = screen.refreshing
    val error: StateFlow<Throwable?> = screen.error
    val query: StateFlow<String?> = screen.query

    fun setQuery(query: String?) = screen.setQuery(query)

    /** This list with only the rows that match [query]. */
    protected fun Flow<ListState>.filtered(matchForum: Boolean = true): Flow<ListState> =
        combine(query) { state, query -> state.copy(sections = state.sections?.let { filterSections(it, TextQuery(query), matchForum) }) }

    protected abstract suspend fun fetch(force: Boolean)

    init {
        // Outside Watched, the rows only know what is watched if the All list has been fetched.
        if (from != OpenedFrom.WATCHED) viewModelScope.launch { deps.watched.ensureKnown() }
    }

    /** Refreshes what is on screen; the latest call wins ([ListScreenState.refresh]). */
    fun refresh(force: Boolean = true) = screen.refresh { fetch(force) }

    /** How to open [item]; waits for the settings on the first tap after launch, so it never decides on the defaults. */
    suspend fun open(item: ThreadItem): OpenThread? {
        val id = item.openId ?: return null
        val forumId = item.forum?.id
        val current = deps.settings.settings.first()
        // 2.14.12 "auto mark as read": opening a watched thread from Watched marks it read, with no Undo. Not only when
        // the cached row shows unread posts: the list can be minutes old, and reading in Whirl does not move
        // Whirlpool's read position (no web session), so a thread opened on a stale "0" stayed unread on the site.
        // Threads opened from other lists or links are marked by the thread screen.
        if (from == OpenedFrom.WATCHED && current.watchedAutoMarkAsRead) {
            viewModelScope.launch { deps.watchQueue.perform(WatchAction.MARK_READ, listOf(id), undoable = false) }
        }
        return opener.open(id, forumId, forumId in privateIds.value, from, current, item.watched)
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
        viewModelScope.launch { deps.watchQueue.perform(action, listOf(id), undoable = deps.settings.settings.first().watchUndo, announce = true) }
    }

    suspend fun lastPost(item: ThreadItem): OpenThread? {
        val id = item.openId ?: return null
        val forumId = item.forum?.id
        return opener.lastPost(id, forumId, forumId in privateIds.value, deps.settings.settings.first())
    }

    fun browserUrl(item: ThreadItem): String = opener.browserUrl(item.openId ?: item.id)
}
