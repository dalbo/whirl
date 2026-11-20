package au.howesthat.whirl.feature.thread

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.DraftRepository
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.whirlcode.Quote
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * A thread's reply draft, for the post writer's ways in (`docs/plans/post-writer.md` §6): Quote in the post menu, Write a
 * reply in the thread menu, and the Draft chip in the top bar.
 */
@HiltViewModel(assistedFactory = ThreadDraftViewModel.Factory::class)
class ThreadDraftViewModel @AssistedInject constructor(
    @Assisted threadId: String,
    private val drafts: DraftRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(threadId: String): ThreadDraftViewModel
    }

    private val id = ThreadId(threadId)
    val draft = drafts.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The thread's title, from whichever reader is showing it; a draft keeps it for the Drafts list. */
    var title: String = ""

    private val quoted = Channel<Int>(Channel.BUFFERED)
    /** After each Quote: how many quotes the draft now holds. */
    val quotes = quoted.receiveAsFlow()

    fun quote(post: UiPost) {
        viewModelScope.launch {
            val name = title.ifBlank { draft.value?.threadTitle.orEmpty() }
            quoted.send(drafts.appendQuote(id, name, Quote.of(post.post.replyId, post.post.author.name, post.body)))
        }
    }
}

/** What the post and thread menus need of the draft; null outside a thread. */
internal class ReplyDraft(val hasDraft: Boolean, val quote: (UiPost) -> Unit, val write: () -> Unit, val setTitle: (String) -> Unit)

internal val LocalReplyDraft = staticCompositionLocalOf<ReplyDraft?> { null }

/** "Quoted · 3 quotes in your reply", with Open. */
@Composable
internal fun QuotedSnackbars(viewModel: ThreadDraftViewModel, snackbar: SnackbarHostState, onOpen: () -> Unit) {
    val resources = LocalResources.current
    val open = stringResource(R.string.draft_open)
    LaunchedEffect(viewModel) {
        viewModel.quotes.collect { count ->
            launch {
                val text = resources.getQuantityString(R.plurals.quoted, count, count)
                if (snackbar.showSnackbar(text, actionLabel = open, duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) onOpen()
            }
        }
    }
}

/** Tells the draft the thread's title, once a reader knows it. */
@Composable
internal fun ReplyDraftTitle(title: String?) {
    val draft = LocalReplyDraft.current
    LaunchedEffect(title, draft) { if (!title.isNullOrBlank()) draft?.setTitle?.invoke(title) }
}

/** In a reader's top bar: shown while the thread has a draft, and opens it. */
@Composable
internal fun DraftChip() {
    val draft = LocalReplyDraft.current ?: return
    if (!draft.hasDraft) return
    TextButton(onClick = draft.write) { Text(stringResource(R.string.draft_chip)) }
}

/** The reply draft for [viewModel]'s thread, while composing [content]. */
@Composable
internal fun ProvideReplyDraft(viewModel: ThreadDraftViewModel, onWrite: () -> Unit, content: @Composable () -> Unit) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalReplyDraft provides ReplyDraft(hasDraft = draft != null, quote = viewModel::quote, write = onWrite, setTitle = { viewModel.title = it }),
        content = content,
    )
}
