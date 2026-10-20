package au.howesthat.whirl.feature.threadlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.OpenThread
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadOpener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What every thread list needs to decide how a tap opens a thread. */
class ListDeps @Inject constructor(val settings: SettingsRepository, val forums: ForumRepository)

/** Refresh state, errors and thread opening shared by every thread list screen. */
abstract class ListViewModel(deps: ListDeps, private val from: OpenedFrom) : ViewModel() {
    private val opener = ThreadOpener()
    protected val settings: StateFlow<Settings> = deps.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    protected val privateIds: StateFlow<Set<Int>> = deps.forums.privateIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    /** The last refresh's failure; the cached list (if any) stays on screen. */
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    protected abstract suspend fun fetch(force: Boolean)

    fun refresh(force: Boolean = true) {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            _error.value = try {
                fetch(force)
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

    fun open(item: ThreadItem): OpenThread? {
        val id = item.openId ?: return null
        val forumId = item.forum?.id
        return opener.open(id, forumId, forumId in privateIds.value, from, settings.value, item.watched)
    }

    fun lastPost(item: ThreadItem): OpenThread? {
        val id = item.openId ?: return null
        val forumId = item.forum?.id
        return opener.lastPost(id, forumId, forumId in privateIds.value, settings.value)
    }

    fun browserUrl(item: ThreadItem): String = opener.browserUrl(item.openId ?: item.id)
}
