package au.howesthat.whirl.feature.thread

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.ui.style

@Composable
internal fun ThreadMenu(
    isWatched: Boolean,
    onWatchAction: (WatchAction) -> Unit,
    /** Whirl's own "Don't notify" for a watched thread. */
    isMuted: Boolean,
    onSetMuted: (Boolean) -> Unit,
    myUserId: Long?,
    onUsersPosts: (Long) -> Unit,
    onOpPosts: () -> Unit,
    onModeratorPosts: () -> Unit,
    onRepPosts: () -> Unit,
    onFirst: () -> Unit,
    onLast: () -> Unit,
    /** Null hides "Go to page" (the scroll reader has no pages). */
    onGoTo: (() -> Unit)?,
    onReply: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onWatched: () -> Unit,
    onFind: () -> Unit,
    /** The scroll reader: First and Last are posts, and the switch reads "Read in pages". */
    scrolling: Boolean = false,
    onSwitchMode: () -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.more)) }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        if (isWatched) {
            DropdownMenuItem(text = { Text(stringResource(R.string.mark_read)) }, onClick = { open = false; onWatchAction(WatchAction.MARK_READ) })
            DropdownMenuItem(text = { Text(stringResource(R.string.unwatch)) }, onClick = { open = false; onWatchAction(WatchAction.UNWATCH) })
            DropdownMenuItem(
                text = { Text(stringResource(if (isMuted) R.string.unmute else R.string.mute)) },
                onClick = { open = false; onSetMuted(!isMuted) },
            )
        } else {
            DropdownMenuItem(text = { Text(stringResource(R.string.watch)) }, onClick = { open = false; onWatchAction(WatchAction.WATCH) })
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.find_on_page)) }, onClick = { open = false; onFind() })
        DropdownMenuItem(text = { Text(stringResource(R.string.watched_threads)) }, onClick = { open = false; onWatched() })
        HorizontalDivider()
        val first = if (scrolling) R.string.first_post else au.howesthat.whirl.core.ui.R.string.first_page
        val last = if (scrolling) R.string.newest_post else au.howesthat.whirl.core.ui.R.string.last_page
        DropdownMenuItem(text = { Text(stringResource(first)) }, onClick = { open = false; onFirst() })
        DropdownMenuItem(text = { Text(stringResource(last)) }, onClick = { open = false; onLast() })
        onGoTo?.let { goTo ->
            DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.go_to_page)) }, onClick = { open = false; goTo() })
        }
        DropdownMenuItem(
            text = { Text(stringResource(if (scrolling) R.string.read_in_pages else R.string.read_as_scroll)) },
            onClick = { open = false; onSwitchMode() },
        )
        HorizontalDivider()
        // Filtered in the app from the thread's archive pages (Whirlpool shows its own filters only to signed-in users).
        Text(
            stringResource(R.string.filters_title),
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        @Composable
        fun FilterItem(label: String, onClick: () -> Unit) = DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onClick() })
        myUserId?.let { me -> FilterItem(stringResource(R.string.filter_mine)) { onUsersPosts(me) } }
        // The thread starter's id is known only once page 1 has been read, so this item finds it first.
        FilterItem(stringResource(R.string.filter_op), onOpPosts)
        FilterItem(stringResource(R.string.filter_mods), onModeratorPosts)
        FilterItem(stringResource(R.string.filter_reps), onRepPosts)
        HorizontalDivider()
        DropdownMenuItem(text = { Text(stringResource(R.string.reply_to_thread)) }, onClick = { open = false; onReply() })
        DropdownMenuItem(text = { Text(stringResource(au.howesthat.whirl.core.ui.R.string.open_in_browser)) }, onClick = { open = false; onOpenInBrowser() })
    }
}
