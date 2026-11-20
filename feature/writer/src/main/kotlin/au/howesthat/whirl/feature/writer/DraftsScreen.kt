package au.howesthat.whirl.feature.writer

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import au.howesthat.whirl.core.data.Draft
import au.howesthat.whirl.core.data.DraftRepository
import au.howesthat.whirl.core.ui.ListPaneRoute
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.whirlTopBarColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** Drafts, from the drawer (`docs/plans/post-writer.md` §6): a list pane, so a draft opens beside it in two panes. */
@Serializable
data object DraftsRoute : NavKey, ListPaneRoute

@HiltViewModel
class DraftsViewModel @Inject constructor(private val drafts: DraftRepository) : ViewModel() {
    /** Null until read. Newest first. */
    val all: StateFlow<List<Draft>?> = drafts.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(draft: Draft) {
        viewModelScope.launch { drafts.delete(draft.threadId) }
    }

    /** Undo of [delete]: the draft as it was. */
    fun restore(draft: Draft) {
        viewModelScope.launch { drafts.save(draft.threadId, draft.threadTitle, draft.text) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(navIcon: NavIcon, onOpen: (threadId: String, title: String) -> Unit, viewModel: DraftsViewModel = hiltViewModel()) {
    val drafts by viewModel.all.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deleted = stringResource(R.string.drafts_deleted)
    val undo = stringResource(R.string.drafts_undo)
    val deleteLabel = stringResource(R.string.writer_delete_draft)

    fun delete(draft: Draft) {
        viewModel.delete(draft)
        scope.launch {
            if (snackbar.showSnackbar(deleted, actionLabel = undo, duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) viewModel.restore(draft)
        }
    }

    Scaffold(
        topBar = { TopAppBar(colors = whirlTopBarColors(), title = { Text(stringResource(R.string.drafts_title)) }, navigationIcon = { NavIconButton(navIcon) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val list = drafts ?: return@Scaffold
        if (list.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.drafts_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        val now = remember(list) { System.currentTimeMillis() }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(list, key = { it.threadId.value }) { draft ->
                val swipe = rememberSwipeToDismissBoxState()
                SwipeToDismissBox(
                    state = swipe,
                    backgroundContent = {},
                    onDismiss = { delete(draft) },
                ) {
                    ListItem(
                        modifier = Modifier
                            .clickable { onOpen(draft.threadId.value, draft.threadTitle) }
                            // Swiping is not for everyone: TalkBack gets Delete as an action.
                            .semantics { customActions = listOf(CustomAccessibilityAction(deleteLabel) { delete(draft); true }) },
                        headlineContent = { Text(draft.threadTitle.ifBlank { draft.threadId.value }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(draft.text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Text(
                                DateUtils.getRelativeTimeSpanString(draft.updatedAt.toEpochMilli(), now, DateUtils.MINUTE_IN_MILLIS).toString(),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}
