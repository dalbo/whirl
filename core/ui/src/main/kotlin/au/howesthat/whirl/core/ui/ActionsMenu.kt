package au.howesthat.whirl.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics

/** One entry of a row's long-press menu. */
class MenuAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/**
 * Tap and long-press for a row whose long press opens [actions] (shown with [ActionsMenu]). TalkBack gets the same
 * entries as custom actions, so nobody has to find the long press, and hears [onLongClickLabel] for it.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.menuClickable(
    actions: List<MenuAction>,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onLongClickLabel: String? = null,
): Modifier = combinedClickable(enabled = enabled, onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick)
    .semantics {
        if (enabled) customActions = actions.filter { it.enabled }.map { a -> CustomAccessibilityAction(a.label) { a.onClick(); true } }
    }

@Composable
fun ActionsMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { a ->
            DropdownMenuItem(text = { Text(a.label) }, enabled = a.enabled, onClick = { onDismiss(); a.onClick() })
        }
    }
}
