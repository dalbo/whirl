package au.howesthat.whirl.core.ui

import androidx.lifecycle.SavedStateHandle
import au.howesthat.whirl.core.model.WhirlpoolError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
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

    /** Whether [job] is a forced refresh, for [refreshSame]. */
    private var jobForced = false

    /**
     * Runs [fetch]. The latest call wins: one made during a fetch cancels it, so a page swipe or tab change always
     * loads what is now on screen (ignoring calls while busy left it blank). Only the newest call sets [refreshing]
     * and [error].
     */
    fun refresh(fetch: suspend () -> Unit) = start(forced = true, fetch)

    /**
     * For a screen that always fetches the same thing (News, Forums): a call during a fetch joins it, unless it is
     * [force]d and the running one is not (a pull to refresh during the opening load), which it then replaces. Pulling
     * again and again sends nothing more.
     */
    fun refreshSame(force: Boolean, fetch: suspend () -> Unit) {
        if (job?.isActive == true && (jobForced || !force)) return
        start(force, fetch)
    }

    private fun start(forced: Boolean, fetch: suspend () -> Unit) {
        job?.cancel()
        jobForced = forced
        _refreshing.value = true
        // Started once it is [job], so even a fetch that finishes at once can tell whether it is still the newest.
        val next = scope.launch(start = CoroutineStart.LAZY) {
            val outcome = try {
                fetch()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e
            }
            // A replaced fetch could still end in an error of its own (a cancelled call can surface as an IOException).
            if (job === coroutineContext.job) _error.value = outcome
        }
        job = next
        next.invokeOnCompletion { if (job === next) _refreshing.value = false }
        next.start()
    }

    /**
     * Fetches again once a rejected key is replaced ([keyRejected] going false, from the "key not accepted" banner)
     * while this screen's last failure was that key, so the old error does not stay up until Retry.
     */
    fun retryWhenKeyAccepted(keyRejected: Flow<Boolean>, fetch: suspend () -> Unit) {
        scope.launch { keyRejected.collect { rejected -> if (!rejected && _error.value is WhirlpoolError.BadKey) refresh(fetch) } }
    }

    private companion object {
        const val QUERY_KEY = "findQuery"
    }
}
