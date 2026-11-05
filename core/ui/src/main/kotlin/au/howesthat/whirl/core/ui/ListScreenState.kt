package au.howesthat.whirl.core.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What every list screen's view model keeps besides its list: refreshing, the last refresh's failure, and the find
 * query. Called from the main thread.
 */
class ListScreenState(private val scope: CoroutineScope, private val savedState: SavedStateHandle) {
    /** Find in list: null while the bar is closed, else what is typed. Saved, so it survives process death. */
    val query: StateFlow<String?> = savedState.getStateFlow(QUERY_KEY, null)

    fun setQuery(query: String?) {
        savedState[QUERY_KEY] = query
    }

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    /** The last refresh's failure; the cached list (if any) stays on screen. */
    val error: StateFlow<Throwable?> = _error.asStateFlow()

    private var job: Job? = null

    /**
     * Runs [fetch]. The latest call wins: one made during a fetch cancels it, so a pull to refresh, page swipe or tab
     * change always loads (ignoring calls while busy dropped them). Only the newest call clears [refreshing].
     */
    fun refresh(fetch: suspend () -> Unit) {
        job?.cancel()
        _refreshing.value = true
        val next = scope.launch {
            try {
                fetch()
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e
            }
        }
        job = next
        next.invokeOnCompletion { if (job === next) _refreshing.value = false }
    }

    private companion object {
        const val QUERY_KEY = "findQuery"
    }
}
