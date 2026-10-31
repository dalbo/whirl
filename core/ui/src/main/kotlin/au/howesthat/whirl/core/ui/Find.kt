package au.howesthat.whirl.core.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Find shortcuts from a hardware keyboard, sent by MainActivity (no screen has focus to receive keys itself). */
val LocalFindRequests = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/** Ctrl+F, or "/" on its own: a key press nothing on screen used, such as a text field. */
fun isFindShortcut(keyCode: Int, metaState: Int): Boolean {
    val modifiers = metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK or KeyEvent.META_SHIFT_MASK)
    return (keyCode == KeyEvent.KEYCODE_F && modifiers and KeyEvent.META_CTRL_MASK != 0 && modifiers and KeyEvent.META_CTRL_MASK.inv() == 0) ||
        (keyCode == KeyEvent.KEYCODE_SLASH && modifiers == 0)
}

/** Runs [onFind] for each find shortcut while this screen is resumed, so only the screen on top answers. */
@Composable
fun OnFindShortcut(onFind: () -> Unit) {
    val requests = LocalFindRequests.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val action by rememberUpdatedState(onFind)
    LaunchedEffect(requests, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { requests.collect { action() } } }
}

/** The top bar action that opens Find in list. */
@Composable
fun FindButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Search, stringResource(R.string.find_in_list)) }
}

/**
 * Find in list: takes the place of a list's top bar while the user filters it. It grows with the font instead of
 * clipping like a field inside a TopAppBar. [matches] (null while unknown) is shown and read out by TalkBack once
 * typing pauses. With [onSearchWhirlpool], a chip under the field searches Whirlpool for the same words.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    label: String,
    matches: Int?,
    modifier: Modifier = Modifier,
    onSearchWhirlpool: ((String) -> Unit)? = null,
    /** Back closes the bar; false (a thread open beside the list) leaves Back to the thread unless the field has focus. */
    closeOnBack: Boolean = true,
) {
    val state = rememberTextFieldState(query)
    val change by rememberUpdatedState(onQueryChange)
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect { change(it) } }
    // Focus (and the keyboard) only when the bar opens, not each time the screen comes back from a thread.
    val focus = remember { FocusRequester() }
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focused) {
            focus.requestFocus()
            focused = true
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    var fieldFocused by remember { mutableStateOf(false) }
    BackHandler(enabled = closeOnBack || fieldFocused, onBack = onClose)
    // Choosing a thread beside the list leaves the field focused (only the keyboard went); let Back reach the thread.
    val focusManager = LocalFocusManager.current
    LaunchedEffect(closeOnBack) { if (!closeOnBack) focusManager.clearFocus() }
    // A shortcut with the bar already open goes back to the field.
    OnFindShortcut { focus.requestFocus() }

    val colors = whirlTopBarColors()
    Surface(modifier, color = colors.containerColor, contentColor = colors.titleContentColor) {
        Column(Modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
            Row(Modifier.heightIn(min = 64.dp).padding(start = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.find_close)) }
                SearchBarDefaults.InputField(
                    state = state,
                    // The keyboard's action key only hides the keyboard: the list is already filtered.
                    onSearch = { keyboard?.hide() },
                    expanded = false,
                    onExpandedChange = {},
                    modifier = Modifier.weight(1f).focusRequester(focus).onFocusChanged { fieldFocused = it.isFocused }.onPreviewKeyEvent { event ->
                        (event.key == Key.Escape && event.type == KeyEventType.KeyDown).also { escape -> if (escape) onClose() }
                    },
                    placeholder = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = if (query.isEmpty()) null else {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                matches?.let { MatchCount(it, query) }
                                IconButton(onClick = { state.clearText() }) { Icon(Icons.Default.Close, stringResource(R.string.find_clear)) }
                            }
                        }
                    },
                )
            }
            val words = query.trim()
            if (onSearchWhirlpool != null && words.isNotEmpty()) {
                AssistChip(
                    onClick = { onSearchWhirlpool(words) },
                    label = { Text(stringResource(R.string.search_whirlpool_for, words), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/** "12" beside the field; TalkBack reads "12 matches" once typing has paused for half a second. */
@Composable
private fun MatchCount(matches: Int, query: String) {
    val spoken = pluralStringResource(R.plurals.find_matches, matches, matches)
    val announced by produceState<String?>(null, spoken, query) {
        delay(500)
        value = spoken
    }
    Text(
        matches.toString(),
        Modifier.padding(end = 4.dp).clearAndSetSemantics {
            announced?.let {
                contentDescription = it
                liveRegion = LiveRegionMode.Polite
            }
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
