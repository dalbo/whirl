package au.howesthat.whirl.navigation

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import au.howesthat.whirl.R
import au.howesthat.whirl.core.data.PendingWatch
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchEvent
import au.howesthat.whirl.core.ui.errorMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest

/**
 * Snackbars for watch actions, app-wide so they survive "back to list". An action is sent when its snackbar goes away
 * (timeout, a newer action replacing it, or the screen going away) unless Undo was pressed. With Undo turned off in
 * settings it has been sent already, and the snackbar only says it was done.
 */
@Composable
fun WatchSnackbars(
    host: SnackbarHostState,
    events: Flow<WatchEvent>,
    onUndo: suspend (PendingWatch) -> Unit,
    onCommit: (PendingWatch) -> Unit,
) {
    val undo = stringResource(R.string.undo)
    val applied = mapOf(
        WatchAction.WATCH to stringResource(R.string.watch_done),
        WatchAction.MARK_READ to stringResource(R.string.mark_read_done),
        WatchAction.UNWATCH to stringResource(R.string.unwatch_done),
    )
    val resources = LocalResources.current
    var failure by remember { mutableStateOf<WatchEvent.Failed?>(null) }

    LaunchedEffect(host, events) {
        events.collectLatest { event ->
            when (event) {
                is WatchEvent.Applied -> {
                    val p = event.pending
                    var undone = false
                    try {
                        // Mark all as read says how many.
                        val text = if (p.action == WatchAction.MARK_READ && p.ids.size > 1) {
                            resources.getQuantityString(R.plurals.mark_read_done_threads, p.ids.size, p.ids.size)
                        } else {
                            applied.getValue(p.action)
                        }
                        // Undo turned off: it is already on its way, so only say so.
                        if (!event.undoable) {
                            host.showSnackbar(text, duration = SnackbarDuration.Short)
                            return@collectLatest
                        }
                        val result = host.showSnackbar(text, actionLabel = undo, duration = SnackbarDuration.Long)
                        if (result == SnackbarResult.ActionPerformed) {
                            undone = true
                            onUndo(p)
                        }
                    } finally {
                        if (!undone) onCommit(p)
                    }
                }
                is WatchEvent.Failed -> failure = event
            }
        }
    }

    failure?.let { f ->
        val text = stringResource(
            when (f.action) {
                WatchAction.WATCH -> R.string.watch_failed
                WatchAction.MARK_READ -> R.string.mark_read_failed
                WatchAction.UNWATCH -> R.string.unwatch_failed
            },
            errorMessage(f.error),
        )
        LaunchedEffect(f) {
            host.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
            failure = null
        }
    }
}
