package au.howesthat.whirl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.OpenThread
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.PendingWatch
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadOpener
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchedPosition
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.feature.thread.ThreadRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainState(val settings: Settings, val setupDone: Boolean)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settings: SettingsRepository,
    api: WhirlpoolApi,
    private val watched: WatchedRepository,
    private val forums: ForumRepository,
) : ViewModel() {
    /** Null until settings are read, which keeps the splash screen up. */
    val state: StateFlow<MainState?> = combine(settings.settings, settings.isSetupDone, ::MainState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val account = settings.account.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whirlpool's site notice from the last API call. */
    val alert = api.alert

    private val _link = MutableStateFlow<Any?>(null)
    /** A route from a link the app was opened with, waiting for the NavHost (which may still be behind setup). */
    val link: StateFlow<Any?> = _link.asStateFlow()

    fun openLink(route: Any?) {
        if (route != null) _link.value = route
    }

    fun linkHandled() {
        _link.value = null
    }

    private val opener = ThreadOpener()

    /**
     * A thread's notification: opens it as a tap in Watched would, by the same rules (the browser for private forums
     * or with "open in the browser"; marked read with auto mark read). [link] then holds a [ThreadRoute] or an
     * [OpenThread.Browser].
     */
    fun openWatchedThread(id: ThreadId, forumId: Int, position: WatchedPosition) {
        viewModelScope.launch {
            val s = settings.settings.first()
            if (s.watchedAutoMarkAsRead) launch { watched.perform(WatchAction.MARK_READ, listOf(id), undoable = false) }
            _link.value = when (val target = opener.open(id, forumId, forumId in forums.privateIds.first(), OpenedFrom.WATCHED, s, position)) {
                is OpenThread.InApp -> ThreadRoute(
                    target.id.value, target.page, scrollToPost = target.scrollToPost, forumId = target.forumId, fromWatched = target.fromWatched,
                    lastReadPost = target.lastReadPost,
                )
                is OpenThread.Browser -> target
            }
        }
    }

    /** Watch actions waiting for Undo, and failures, for the app-wide snackbar. */
    val watchEvents = watched.events

    suspend fun undo(p: PendingWatch) = watched.undo(p)

    fun commit(p: PendingWatch) = watched.commit(p)
}
