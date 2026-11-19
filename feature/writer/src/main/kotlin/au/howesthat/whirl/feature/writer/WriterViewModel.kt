package au.howesthat.whirl.feature.writer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.ApplicationScope
import au.howesthat.whirl.core.data.DefaultDispatcher
import au.howesthat.whirl.core.data.DraftRepository
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.scraper.PostBodyParser
import au.howesthat.whirl.core.whirlcode.WhirlCode
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The post writer (`docs/plans/post-writer.md` §5): the thread's draft as WhirlCode, saved as typed, with a preview
 * through the same parser and renderer as every post. While it is open, quotes for its thread come here
 * ([DraftRepository.attachWriter]), so none is lost to a save of older text.
 */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@HiltViewModel(assistedFactory = WriterViewModel.Factory::class)
class WriterViewModel @AssistedInject constructor(
    @Assisted private val route: WriterRoute,
    private val drafts: DraftRepository,
    @ApplicationScope private val appScope: CoroutineScope,
    @DefaultDispatcher private val parsing: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: WriterRoute): WriterViewModel
    }

    val threadId = ThreadId(route.threadId)
    val threadTitle: String get() = route.threadTitle

    /** The draft being written. Read-only until [loaded], so nothing typed early is overwritten by the stored draft. */
    val text = TextFieldState()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** The text as the site would show it, converted off Main a moment after typing stops. */
    val preview: StateFlow<PostBody> = snapshotFlow { text.text.toString() }
        .debounce(PREVIEW_DELAY_MS)
        .mapLatest { source -> withContext(parsing) { PostBodyParser.parse(WhirlCode.toHtml(source)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PostBody(emptyList()))

    /** Whether there is anything to copy. */
    val isEmpty: StateFlow<Boolean> = snapshotFlow { text.text.isBlank() }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private var writer: AutoCloseable? = null
    /** The text last stored; written from viewModelScope and from the application scope. */
    @Volatile private var saved: String? = null

    init {
        viewModelScope.launch {
            val stored = drafts.get(threadId)?.text.orEmpty()
            text.setTextAndPlaceCursorAtEnd(stored)
            text.undoState.clearHistory()
            saved = stored
            writer = drafts.attachWriter(threadId) { quote ->
                withContext(Dispatchers.Main.immediate) {
                    val added = DraftRepository.append(text.text.toString(), quote)
                    text.edit {
                        replace(0, length, added)
                        placeCursorAtEnd()
                    }
                    added
                }
            }
            _loaded.value = true
            snapshotFlow { text.text.toString() }.drop(1).debounce(SAVE_DELAY_MS).collect(::save)
        }
    }

    /** Applies a formatting operation to the text and its selection. */
    fun format(operation: (Edit) -> Edit) {
        val result = operation(Edit(text.text.toString(), text.selection))
        text.edit {
            replace(0, length, result.text)
            selection = TextRange(result.selection.start.coerceIn(0, length), result.selection.end.coerceIn(0, length))
        }
    }

    /** Where Copy and reply sends the user: the post the reply started from, else the thread. */
    fun replyUrl(): String = (route.replyTo?.let(URLS::replyToPost) ?: URLS.replyToThread(threadId)).toString()

    /** The draft, saved now (before Copy and reply opens the browser, which may end the process). */
    fun saveNow() {
        val current = text.text.toString()
        appScope.launch { withContext(NonCancellable) { save(current) } }
    }

    fun deleteDraft() {
        text.clearText()
        saved = ""
        appScope.launch { withContext(NonCancellable) { drafts.delete(threadId) } }
    }

    private suspend fun save(current: String) {
        if (current == saved) return
        saved = current
        drafts.save(threadId, route.threadTitle, current)
    }

    override fun onCleared() {
        writer?.close()
        // viewModelScope is cancelled by now: the last save goes through the application's scope.
        if (_loaded.value) {
            val last = text.text.toString()
            if (last != saved) appScope.launch { withContext(NonCancellable) { drafts.save(threadId, route.threadTitle, last) } }
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 500L
        const val PREVIEW_DELAY_MS = 250L
        val URLS = WhirlpoolUrls()
    }
}
