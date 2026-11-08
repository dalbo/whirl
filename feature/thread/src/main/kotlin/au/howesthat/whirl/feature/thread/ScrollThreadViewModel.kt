package au.howesthat.whirl.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.ArchiveRepository
import au.howesthat.whirl.core.data.DefaultDispatcher
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadPageRepository
import au.howesthat.whirl.core.data.ThreadPageRepository.Companion.LAST_PAGE
import au.howesthat.whirl.core.data.WatchQueue
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.data.WhirlpoolLink
import au.howesthat.whirl.core.data.POSTS_PER_PAGE
import au.howesthat.whirl.core.data.pageOfPost
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.scraper.PostBodyParser
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One archive page in the reader's window, its bodies parsed. */
data class ScrollPage(val index: Int, val path: String, val posts: List<UiPost>)

/** What the scroll reader is doing. */
enum class ScrollLoad { OPEN, EARLIER, LATER, REFRESH }

/**
 * The scroll reader: the open thread's archive pages ([ArchiveRepository.ArchiveThread.pages]) as one list. At most
 * [WINDOW] pages are held (each up to 800 posts, bodies parsed); loading past that drops the far end, which "Load
 * earlier" / "Load later" bring back from the cache.
 */
data class ScrollUiState(
    val title: String? = null,
    val thread: ArchiveRepository.ArchiveThread? = null,
    val window: List<ScrollPage> = emptyList(),
    val note: PostBody? = null,
    val offline: Boolean = false,
    val loading: ScrollLoad? = null,
    val error: Throwable? = null,
) {
    val posts: List<UiPost> get() = window.flatMap { it.posts }
    val canLoadEarlier: Boolean get() = (window.firstOrNull()?.index ?: 0) > 0
    val canLoadLater: Boolean get() = thread != null && (window.lastOrNull()?.index ?: Int.MAX_VALUE) < thread.pages.lastIndex
    /** The window ends at the thread's newest post. */
    val atEnd: Boolean get() = thread != null && window.isNotEmpty() && !canLoadLater
}

/** A post to bring into view: by reply id, by number (the first post at or after it, since hidden posts are missing), or the last. */
data class ScrollRequest(val replyId: Long? = null, val number: Int? = null, val last: Boolean = false)

/** Index in [posts] of the post [request] asks for, or null. */
internal fun ScrollRequest.indexIn(posts: List<Post>): Int? = when {
    last -> posts.lastIndex.takeIf { it >= 0 }
    replyId != null -> posts.indexOfFirst { it.replyId == replyId }.takeIf { it >= 0 }
    number != null -> posts.indexOfFirst { it.number >= number }.takeIf { it >= 0 } ?: posts.lastIndex.takeIf { it >= 0 }
    else -> null
}

/** [window] with [page] added before (or after) it, dropping the far end past [ScrollThreadViewModel.WINDOW] pages. */
internal fun grow(window: List<ScrollPage>, page: ScrollPage, earlier: Boolean): List<ScrollPage> {
    val grown = if (earlier) listOf(page) + window else window + page
    return when {
        grown.size <= ScrollThreadViewModel.WINDOW -> grown
        earlier -> grown.take(ScrollThreadViewModel.WINDOW)
        else -> grown.takeLast(ScrollThreadViewModel.WINDOW)
    }
}

/** Where a [ThreadRoute] opens in the scroll reader: a post number, or null for the newest post. */
internal fun ThreadRoute.scrollStart(): Int? = when {
    scrollToPost != null -> scrollToPost
    page == LAST_PAGE -> null
    else -> (page - 1) * POSTS_PER_PAGE + 1
}

@HiltViewModel(assistedFactory = ScrollThreadViewModel.Factory::class)
class ScrollThreadViewModel @AssistedInject constructor(
    @Assisted private val route: ThreadRoute,
    private val archive: ArchiveRepository,
    private val threadPages: ThreadPageRepository,
    private val settingsRepo: SettingsRepository,
    private val watchedRepo: WatchedRepository,
    private val watchQueue: WatchQueue,
    private val savedState: SavedStateHandle,
    @DefaultDispatcher private val parsing: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: ThreadRoute): ScrollThreadViewModel
    }

    val threadId = ThreadId(route.threadId)
    private val urls = WhirlpoolUrls()

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val myUserId: StateFlow<Long?> = settingsRepo.account.map { it?.userId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(ScrollUiState())
    val state: StateFlow<ScrollUiState> = _state.asStateFlow()

    private val _scrollTo = MutableStateFlow<ScrollRequest?>(null)
    val scrollTo: StateFlow<ScrollRequest?> = _scrollTo.asStateFlow()

    private val events = Channel<ThreadEvent>(Channel.BUFFERED)
    val navigation: Flow<ThreadEvent> = events.receiveAsFlow()

    val isWatched: StateFlow<Boolean> = watchedRepo.isWatched(threadId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, route.fromWatched)

    private val _firstUnread = MutableStateFlow(route.lastReadPost?.let { it + 1 })
    /** The first unread post as the thread opened, for the "New posts" marker. */
    val firstUnread: StateFlow<Int?> = _firstUnread.asStateFlow()

    private var loadJob: Job? = null

    init {
        // After process death, reopen where the reader was, not where the route pointed.
        val anchor = savedState.get<Int>(ANCHOR_KEY)
        open(anchor ?: route.scrollStart(), request = anchor?.let { ScrollRequest(number = it) } ?: initialRequest())
        viewModelScope.launch {
            watchedRepo.ensureKnown()
            if (_firstUnread.value == null) _firstUnread.value = watchedRepo.lastReadPost(threadId)?.let { it + 1 }
            autoMarkRead()
        }
    }

    private fun initialRequest(): ScrollRequest = when {
        route.scrollToReply != null -> ScrollRequest(replyId = route.scrollToReply)
        else -> route.scrollStart()?.let { ScrollRequest(number = it) } ?: ScrollRequest(last = true)
    }

    /** The post at the top of the screen, saved for process death and for switching to pages. */
    fun setTopPost(number: Int) {
        savedState[ANCHOR_KEY] = number
    }

    fun topPost(): Int? = savedState.get<Int>(ANCHOR_KEY)

    /** Opens the window at the archive page holding post [number] (null: the newest). */
    private fun open(number: Int?, request: ScrollRequest, force: Boolean = false, kind: ScrollLoad = ScrollLoad.OPEN) {
        launchLoad(kind) {
            val opened = archive.open(threadId, number, route.forumId, force)
            val index = opened.thread.indexOf(opened.path)
            val page = parsed(index, opened.path, archive.posts(threadId, opened.path))
            val note = opened.moderatorNoteHtml?.let { withContext(parsing) { PostBodyParser.parse(it) } }
            _state.update {
                it.copy(
                    title = opened.thread.title, thread = opened.thread, window = listOf(page),
                    note = note ?: it.note, offline = opened.offline, error = null,
                )
            }
            _scrollTo.value = request
        }
    }

    /** The page before the window, from the cache or fetched. */
    fun loadEarlier() = extend(earlier = true)

    /** The page after the window (after "Load earlier" dropped it). */
    fun loadLater() = extend(earlier = false)

    private fun extend(earlier: Boolean) {
        val s = _state.value
        val thread = s.thread ?: return
        val index = if (earlier) (s.window.firstOrNull()?.index ?: return) - 1 else (s.window.lastOrNull()?.index ?: return) + 1
        if (index !in thread.pages.indices) return
        launchLoad(if (earlier) ScrollLoad.EARLIER else ScrollLoad.LATER) {
            val path = thread.pages[index].path
            var posts = archive.posts(threadId, path)
            if (posts.isEmpty()) {
                archive.loadPages(threadId, listOf(path), ArchiveRepository.Need.BODIES)
                posts = archive.posts(threadId, path)
            }
            if (posts.isEmpty()) throw ArchiveLimit()
            val page = parsed(index, path, posts)
            _state.update { st -> st.copy(window = grow(st.window, page, earlier), error = null) }
        }
    }

    /** The newest posts: reloads the thread's last page and shows the window ending there. */
    fun refresh() {
        val atEnd = _state.value.atEnd || _state.value.window.isEmpty()
        val top = topPost()
        open(if (atEnd) null else top, request = if (atEnd) ScrollRequest(last = true) else ScrollRequest(number = top), force = true, kind = ScrollLoad.REFRESH)
    }

    /** The first post of the part. */
    fun first() = open(1, ScrollRequest(number = 1))

    /** The newest post. */
    fun last() = open(null, ScrollRequest(last = true))

    fun scrollHandled() {
        _scrollTo.value = null
    }

    /** Shows post [number] of this thread: in the window if it is there, else by reopening around it. */
    private fun showPost(number: Int) {
        val posts = _state.value.posts
        if (posts.isNotEmpty() && number in posts.first().post.number..posts.last().post.number) {
            _scrollTo.value = ScrollRequest(number = number)
        } else {
            open(number, ScrollRequest(number = number))
        }
    }

    /** A quote attribution: scroll if the quoted post is loaded, else find it through the site. */
    fun openQuote(replyId: Long) {
        if (_state.value.posts.any { it.post.replyId == replyId }) {
            _scrollTo.value = ScrollRequest(replyId = replyId)
            return
        }
        viewModelScope.launch {
            try {
                val at = threadPages.locate(replyId)
                if (at.threadId == threadId) showPost(at.postNumber)
                else events.send(ThreadEvent.OpenThread(ThreadRoute(at.threadId.value, at.page, scrollToPost = at.postNumber)))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.send(ThreadEvent.Failed(e))
            }
        }
    }

    /** Routes a link from a post: this thread's posts in place, other Whirlpool pages in the app, the rest to the browser. */
    fun openLink(url: String) {
        viewModelScope.launch {
            try {
                handle(WhirlpoolLink.parse(url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.send(ThreadEvent.Failed(e))
            }
        }
    }

    private suspend fun handle(link: WhirlpoolLink) {
        when (link) {
            is WhirlpoolLink.Thread -> {
                val anchor = link.anchor
                when {
                    link.id != threadId -> events.send(
                        ThreadEvent.OpenThread(
                            ThreadRoute(
                                link.id.value, link.page ?: 1,
                                scrollToPost = anchor?.takeIf { !link.isReplyIdAnchor }?.toInt(),
                                scrollToReply = anchor?.takeIf { link.isReplyIdAnchor },
                            ),
                        ),
                    )
                    anchor != null && link.isReplyIdAnchor -> openQuote(anchor)
                    anchor != null -> showPost(anchor.toInt())
                    else -> showPost(((link.page ?: 1) - 1) * POSTS_PER_PAGE + 1)
                }
            }
            is WhirlpoolLink.Archive ->
                if (link.id == threadId) showPost(1) else events.send(ThreadEvent.OpenThread(ThreadRoute(link.id.value)))
            is WhirlpoolLink.Forum -> events.send(ThreadEvent.OpenForum(ForumRef(link.id, "", link.groupId)))
            is WhirlpoolLink.User -> events.send(ThreadEvent.OpenUser(link.id))
            is WhirlpoolLink.Reply -> openQuote(link.replyId)
            // Where whrl.pl redirects is unverified; if it cannot be followed, the browser can.
            is WhirlpoolLink.Short -> {
                val target = try {
                    threadPages.resolveShortLink(link.url)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                handle(target?.takeUnless { it is WhirlpoolLink.Short } ?: WhirlpoolLink.External(link.url))
            }
            is WhirlpoolLink.External -> events.send(ThreadEvent.Browser(link.url))
        }
    }

    // Find, over the loaded window.
    val findQuery: StateFlow<String?> = savedState.getStateFlow(FIND_KEY, null)

    fun setFindQuery(query: String?) {
        savedState[FIND_KEY] = query
    }

    /** Matching posts in the window, by reply id in order; null with no query. */
    val findMatches: StateFlow<ScrollFindMatches?> = combine(_state, findQuery) { s, query ->
        val words = TextQuery(query)
        if (words.isBlank) null else ScrollFindMatches(words, s.posts.filter { matchesFind(it, words) }.map { it.post.replyId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showMatch(replyId: Long) {
        _scrollTo.value = ScrollRequest(replyId = replyId)
    }

    // Watched actions, as in the paged reader.
    private suspend fun autoMarkRead() {
        if (route.fromWatched || !settingsRepo.settings.first().watchedAutoMarkAsRead) return
        if (watchedRepo.isWatched(threadId).first()) watchQueue.perform(WatchAction.MARK_READ, listOf(threadId), undoable = false)
    }

    fun perform(action: WatchAction) {
        viewModelScope.launch {
            val current = settingsRepo.settings.first()
            watchQueue.perform(action, listOf(threadId), undoable = current.watchUndo, announce = true)
            if (action == WatchAction.MARK_READ && route.fromWatched && current.watchedBackToList) events.send(ThreadEvent.Back)
        }
    }

    // Filters still open the browser until v1.8 step D.
    fun showUsersPosts(userId: Long) = browse(urls.thread(threadId, userId = userId).toString())
    fun showOpPosts() {
        val op = _state.value.posts.firstOrNull { it.post.isOp }?.post?.author?.id
        if (op != null) showUsersPosts(op) else browse(urls.thread(threadId).toString())
    }
    fun showModeratorPosts() = browse(urls.thread(threadId, moderatorsOnly = true).toString())
    fun showRepPosts() = browse(urls.thread(threadId, repsOnly = true).toString())

    private fun browse(url: String) {
        viewModelScope.launch { events.send(ThreadEvent.Browser(url)) }
    }

    fun openUser(userId: Long) {
        viewModelScope.launch { events.send(ThreadEvent.OpenUser(userId)) }
    }

    fun threadUrl(): String = urls.thread(threadId, topPost()?.let(::pageOfPost) ?: 1).toString()
    fun postUrl(post: Post): String = urls.thread(threadId, pageOfPost(post.number)).newBuilder().fragment("r${post.number}").build().toString()
    fun replyToThreadUrl(): String = urls.replyToThread(threadId).toString()
    fun replyToPostUrl(post: Post): String = urls.replyToPost(post.replyId).toString()
    fun editPostUrl(post: Post): String = urls.editPost(post.replyId).toString()
    fun reportPostUrl(post: Post): String = urls.reportPost(post.replyId, threadId, pageOfPost(post.number), post.number).toString()

    private suspend fun parsed(index: Int, path: String, posts: List<Post>): ScrollPage = withContext(parsing) {
        ScrollPage(index, path, posts.map { UiPost(it, PostBodyParser.parse(it.bodyHtml)) })
    }

    private fun launchLoad(kind: ScrollLoad, block: suspend () -> Unit) {
        loadJob?.cancel()
        _state.update { it.copy(loading = kind) }
        val job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e) }
            }
        }
        loadJob = job
        job.invokeOnCompletion { if (loadJob === job) _state.update { it.copy(loading = null) } }
    }

    companion object {
        /** Archive pages held at once (each up to 800 posts). */
        const val WINDOW = 3
        private const val ANCHOR_KEY = "scrollAnchor"
        private const val FIND_KEY = "scrollFind"
    }
}

/** Find's matches in the scroll reader's window. */
data class ScrollFindMatches(val query: TextQuery, val replyIds: List<Long>)

/** An earlier page could not be had: the daily cap, or Whirlpool asked to slow down. */
class ArchiveLimit : Exception("No more archive pages can be loaded today")
