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
    /** Set while the reader shows only some posts. */
    val filtered: Filtered? = null,
) {
    val posts: List<UiPost> get() = window.flatMap { it.posts }
    val canLoadEarlier: Boolean get() = (window.firstOrNull()?.index ?: 0) > 0
    val canLoadLater: Boolean get() = thread != null && (window.lastOrNull()?.index ?: Int.MAX_VALUE) < thread.pages.lastIndex
    /** The window ends at the thread's newest post. */
    val atEnd: Boolean get() = thread != null && window.isNotEmpty() && !canLoadLater
}

/**
 * The reader filtered by [filter]: the matching [posts] among archive pages [fromIndex] onwards (the window's pages, then
 * any earlier ones searched). [progress] is (pages done, pages to do) while "Search earlier pages" runs; [stoppedBy]
 * says why the last search ended early.
 */
data class Filtered(
    val filter: ReaderFilter,
    val posts: List<UiPost> = emptyList(),
    val fromIndex: Int,
    val progress: Pair<Int, Int>? = null,
    val stoppedBy: ArchiveRepository.Stop? = null,
    val searched: Boolean = false,
) {
    /** The first post number searched: archive page [fromIndex] starts there. */
    val fromPost: Int get() = fromIndex * au.howesthat.whirl.core.model.ArchivePage.POSTS_PER_PAGE + 1
}

/** A post to bring into view: by reply id, by number (the first post at or after it, since hidden posts are missing), or the last. */
data class ScrollRequest(val replyId: Long? = null, val number: Int? = null, val last: Boolean = false, val top: Boolean = false)

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
    private var searchJob: Job? = null

    /** A filter to apply once the window is open (from the route, or restored after process death). */
    private var pendingFilter: ReaderFilter? = ReaderFilter.parse(savedState.get<String>(FILTER_KEY) ?: route.filter)

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
    /**
     * Opens the window at the archive page holding post [number] (null: the newest). A page cached whole shows at once;
     * the check for newer posts then runs, and new posts join the page on screen without moving the reader.
     */
    private fun open(number: Int?, request: ScrollRequest, force: Boolean = false, kind: ScrollLoad = ScrollLoad.OPEN) {
        launchLoad(kind) {
            // A refresh has its window on screen already.
            val cached = if (force) null else archive.openCached(threadId, number)
            if (cached != null) show(cached, request)
            val opened = try {
                archive.open(threadId, number, route.forumId, force)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Something is on screen: say what went wrong above it rather than replacing it.
                if (_state.value.window.isEmpty()) throw e
                _state.update { it.copy(error = e) }
                return@launchLoad
            }
            val showing = _state.value.window.firstOrNull { it.path == opened.path }
            val note = opened.moderatorNoteHtml?.let { withContext(parsing) { PostBodyParser.parse(it) } }
            when {
                showing == null -> show(opened, request)
                opened.changed -> {
                    val page = reparsed(showing, archive.posts(threadId, opened.path))
                    _state.update { s ->
                        s.copy(
                            thread = opened.thread, window = s.window.map { if (it.path == opened.path) page else it },
                            note = note ?: s.note, offline = opened.offline, error = null,
                        )
                    }
                }
                else -> _state.update { it.copy(thread = opened.thread, note = note ?: it.note, offline = opened.offline, error = null) }
            }
        }
    }

    /** Puts [opened]'s page alone in the window and brings [request]'s post into view. */
    private suspend fun show(opened: ArchiveRepository.Opened, request: ScrollRequest) {
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
        pendingFilter?.let {
            pendingFilter = null
            filterTo(it)
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

    /**
     * Reloads (the thread's last page is fetched again, so new posts appear at the end) and stays at the post at the top
     * of the screen; "Newest post" goes to the end.
     */
    fun refresh() {
        val top = topPost()
        open(top, request = top?.let { ScrollRequest(number = it) } ?: ScrollRequest(last = true), force = true, kind = ScrollLoad.REFRESH)
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

    /** Parsed bodies of filtered posts, so each search step parses only the new matches. */
    private val parsedMatches = HashMap<Long, UiPost>()

    /** Shows only the posts [filter] picks out, starting with the pages in the window (cached, so nothing is fetched). */
    fun filterTo(filter: ReaderFilter) {
        savedState[FILTER_KEY] = filter.code
        val s = _state.value
        val first = s.window.firstOrNull() ?: run { pendingFilter = filter; return }
        _state.update { it.copy(filtered = Filtered(filter, fromIndex = first.index)) }
        // The results start at their header, whatever the reader was about to scroll to.
        _scrollTo.value = ScrollRequest(top = true)
        search(filter, s.window.map { it.path }.reversed(), first.index, ids = emptyList())
    }

    /** Searches the archive pages before those already searched, newest first, one at a time. */
    fun searchEarlier() {
        val f = _state.value.filtered ?: return
        val thread = _state.value.thread ?: return
        if (f.fromIndex == 0 || f.progress != null) return
        val paths = (f.fromIndex - 1 downTo 0).map { thread.pages[it].path }
        search(f.filter, paths, f.fromIndex, ids = f.posts.map { it.post.replyId }, earlier = true)
    }

    /** Stops "Search earlier pages"; what it found so far stays. */
    fun stopSearch() {
        searchJob?.cancel()
        _state.update { s -> s.copy(filtered = s.filtered?.copy(progress = null)) }
    }

    /** Back to every post, at the post that was at the top of the filtered list (its page loaded if need be). */
    fun clearFilter() {
        searchJob?.cancel()
        savedState[FILTER_KEY] = null
        parsedMatches.clear()
        _state.update { it.copy(filtered = null) }
        topPost()?.let(::showPost)
    }

    private fun search(filter: ReaderFilter, paths: List<String>, startIndex: Int, ids: List<Long>, earlier: Boolean = false) {
        searchJob?.cancel()
        val query = filter.scanQuery(myUserId.value) ?: return
        searchJob = viewModelScope.launch {
            try {
                if (earlier) _state.update { s -> s.copy(filtered = s.filtered?.copy(progress = 0 to paths.size, stoppedBy = null)) }
                val result = archive.scan(threadId, paths, query) { done, total, matches ->
                    val shown = show(ids + matches)
                    _state.update { s ->
                        s.copy(
                            filtered = s.filtered?.copy(
                                posts = shown,
                                fromIndex = if (earlier) startIndex - done else startIndex,
                                progress = if (earlier && done < total) done to total else null,
                            ),
                        )
                    }
                }
                val shown = show(ids + result.matches)
                _state.update { s ->
                    s.copy(
                        filtered = s.filtered?.copy(
                            posts = shown,
                            fromIndex = if (earlier) startIndex - result.scanned else startIndex,
                            progress = null,
                            stoppedBy = result.stoppedBy,
                            searched = s.filtered.searched || earlier,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { s -> s.copy(filtered = s.filtered?.copy(progress = null), error = e) }
            }
        }
    }

    /** The posts with reply ids [ids], parsed (each once), in thread order. */
    private suspend fun show(ids: List<Long>): List<UiPost> {
        val missing = ids.filter { it !in parsedMatches }.distinct()
        if (missing.isNotEmpty()) {
            val posts = archive.postsByIds(threadId, missing)
            withContext(parsing) { posts.forEach { parsedMatches[it.replyId] = UiPost(it, PostBodyParser.parse(it.bodyHtml)) } }
        }
        return ids.distinct().mapNotNull { parsedMatches[it] }.sortedBy { it.post.number }
    }

    // The menu's filters.
    fun showUsersPosts(userId: Long, name: String) = filterTo(ReaderFilter.User(userId, name))
    fun showMyPosts() = filterTo(ReaderFilter.Mine)
    fun showOpPosts() = filterTo(ReaderFilter.Op)
    fun showModeratorPosts() = filterTo(ReaderFilter.Moderators)
    fun showRepPosts() = filterTo(ReaderFilter.Reps)

    /** Find's "Search earlier pages": the query becomes a filter over the whole thread. */
    fun searchThread(query: String) {
        if (query.isBlank()) return
        setFindQuery(null)
        filterTo(ReaderFilter.Text(query))
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

    /** [page] with [posts], parsing only the posts that are new or changed (a handful, after new posts arrive). */
    private suspend fun reparsed(page: ScrollPage, posts: List<Post>): ScrollPage = withContext(parsing) {
        val had = page.posts.associateBy { it.post.replyId }
        ScrollPage(page.index, page.path, posts.map { p -> had[p.replyId]?.takeIf { it.post == p } ?: UiPost(p, PostBodyParser.parse(p.bodyHtml)) })
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
        private const val FILTER_KEY = "scrollFilter"
    }
}

/** Find's matches in the scroll reader's window. */
data class ScrollFindMatches(val query: TextQuery, val replyIds: List<Long>)

/** An earlier page could not be had: the daily cap, or Whirlpool asked to slow down. */
class ArchiveLimit : Exception("No more archive pages can be loaded today")
