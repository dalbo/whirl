package au.howesthat.whirl.feature.thread

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import au.howesthat.whirl.core.model.ThreadPart

/** Where this thread sits in a multi-part series: [current] is null when the page marks no part as current. */
data class PartsNav(val current: ThreadPart?, val previous: ThreadPart?, val next: ThreadPart?, val all: List<ThreadPart>)

fun partsNav(parts: List<ThreadPart>): PartsNav? {
    if (parts.isEmpty()) return null
    val sorted = parts.sortedBy { it.number }
    val index = sorted.indexOfFirst { it.isCurrent }
    if (index < 0) return PartsNav(null, null, null, sorted)
    return PartsNav(sorted[index], sorted.getOrNull(index - 1), sorted.getOrNull(index + 1), sorted)
}

/**
 * One row for `.notebar.parts` however many parts there are (`9nznpky9` has 58): previous, "Part N of M" opening a
 * list of every part, next.
 */
@Composable
internal fun PartsRow(nav: PartsNav, onOpen: (ThreadPart) -> Unit, modifier: Modifier = Modifier) {
    var picking by remember { mutableStateOf(false) }
    val total = nav.all.size
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.previous?.let(onOpen) }, enabled = nav.previous != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.part_previous))
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            TextButton(onClick = { picking = true }) {
                Text(
                    nav.current?.let { stringResource(R.string.part_of, it.number, total) }
                        ?: stringResource(R.string.parts_count, total),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
        }
        IconButton(onClick = { nav.next?.let(onOpen) }, enabled = nav.next != null) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.part_next))
        }
    }
    if (picking) {
        PartsDialog(nav, onDismiss = { picking = false }) { part ->
            picking = false
            onOpen(part)
        }
    }
}

@Composable
private fun PartsDialog(nav: PartsNav, onDismiss: () -> Unit, onPick: (ThreadPart) -> Unit) {
    val start = nav.all.indexOf(nav.current).coerceAtLeast(0)
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (start - 2).coerceAtLeast(0))
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.parts_title)) },
        text = {
            LazyColumn(state = list) {
                items(nav.all, key = { it.number }) { part ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.part_n, part.number)) },
                        trailingContent = if (part.isCurrent) {
                            { Icon(Icons.Default.Check, stringResource(R.string.part_current)) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable(enabled = !part.isCurrent, role = Role.Button) { onPick(part) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
