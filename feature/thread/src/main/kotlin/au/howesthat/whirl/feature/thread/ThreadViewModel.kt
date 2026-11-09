package au.howesthat.whirl.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import au.howesthat.whirl.core.data.DefaultDispatcher
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadPageRepository
import au.howesthat.whirl.core.data.ThreadPageRepository.Companion.LAST_PAGE
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.data.WatchQueue
import au.howesthat.whirl.core.data.pageOfPost
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.data.WhirlpoolLink
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.searchText
import au.howesthat.whirl.core.model.ThreadPage
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.scraper.PostBodyParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * A thread, opened at [page] (1-based, or -1 for the last page, at its newest post). [scrollToPost] is a post number, [scrollToReply] a
 * reply id; [fromWatched] when opened from Watched; [lastReadPost] the last post read when it was opened, for the
 * "New posts" marker.
 */
@Serializable
data class ThreadRoute(
    val threadId: String,
    val page: Int = 1,
    val scrollToPost: Int? = null,
    val scrollToReply: Long? = null,
    val forumId: Int? = null,
    val fromWatched: Boolean = false,
    val lastReadPost: Int? = null,
    /** Scroll reader (true) or pages (false); null follows the "Read threads as one scroll" setting. */
    val scroll: Boolean? = null,
    /** A [ReaderFilter.code] the scroll reader opens with. */
    val filter: String? = null,
) : NavKey

/** A post ready to draw: its body parsed into blocks. */
data class UiPost(val post: Post, val body: PostBody)

data class UiPage(val page: ThreadPage, val posts: List<UiPost>, val note: PostBody?, val updatedAt: Instant?)

/**
 * The thread as a whole: the first page to show (null until known, from the cache or the first fetch), its page count
 * and title, the last failure for the page on screen (or for opening the thread), and whether a load is running.
 */
data class ThreadUiState(
    val startPage: Int? = null,
    val pageCount: Int = 1,
    val title: String? = null,
    val error: Throwable? = null,
    val refreshing: Boolean = false,
)

/**
 * A post to bring into view once its page is showing: by number, by reply id, or the page's last. [waitForFetch] is
 * for the post a watched thread opens at, which a cached page may predate: until a copy fetched at [fetched] or later
 * is on screen, a missing post scrolls to the post before it and the target stays, so the fetched page can finish.
 */
data class ScrollTarget(
    val page: Int,
    val postNumber: Int? = null,
    val replyId: Long? = null,
    val lastPost: Boolean = false,
    val waitForFetch: Boolean = false,
    val fetched: Instant? = null,
)

/** What a page does with a scroll target: scrolls to the post at [index] on the page (if any), then forgets the target if [done]. */
internal data class ScrollStep(val index: Int?, val done: Boolean)

internal fun ScrollTarget.step(posts: List<Post>, updatedAt: Instant?): ScrollStep {
    val index = if (lastPost) {
        posts.lastIndex
    } else {
        posts.indexOfFirst { (replyId != null && it.replyId == replyId) || (postNumber != null && it.number == postNumber) }
    }
    if (index >= 0) return ScrollStep(index, done = true)
    if (!waitForFetch || postNumber == null) return ScrollStep(null, done = true)
    val before = posts.indexOfLast { it.number < postNumber }.takeIf { it >= 0 }
    return ScrollStep(before, done = fetched != null && updatedAt != null && updatedAt >= fetched)
}

/** One-off navigation out of the thread. */
sealed interface ThreadEvent {
    data class OpenThread(val route: ThreadRoute) : ThreadEvent
    data class OpenForum(val forum: ForumRef) : ThreadEvent
    data class OpenUser(val userId: Long) : ThreadEvent
    data class Browser(val url: String) : ThreadEvent
    data class Failed(val error: Throwable) : ThreadEvent
    /** Back to the list ("back to list after marking read"). */
    data object Back : ThreadEvent
    /** A filter from the paged reader: the thread switches to the scroll reader, which filters in the app. */
    data class ReadFiltered(val filter: ReaderFilter) : ThreadEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted private val route: ThreadRoute,
    private val repo: ThreadPageRepository,
    private val settingsRepo: SettingsRepository,
    private val watchedRepo: WatchedRepository,
    private val watchQueue: WatchQueue,
    private val savedState: SavedStateHandle,
    @DefaultDispatcher private val parsing: CoroutineDispatcher,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: ThreadRoute): ThreadViewModel
    }

    val threadId = ThreadId(route.threadId)
    private val urls = WhirlpoolUrls()

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val myUserId: StateFlow<Long?> = settingsRepo.account.map { it?.userId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    /** Set when the pager should move (a quote on another page, "last page" turning out to be later). */
    private val _jumpTo = MutableStateFlow<Int?>(null)
    val jumpTo: StateFlow<Int?> = _jumpTo.asStateFlow()

    private val _scrollTarget = MutableStateFlow(
        when {
            route.scrollToReply != null -> ScrollTarget(route.page, replyId = route.scrollToReply)
            route.scrollToPost != null -> ScrollTarget(route.page, postNumber = route.scrollToPost, waitForFetch = route.fromWatched)
            else -> null
        },
    )
    val scrollTarget: StateFlow<ScrollTarget?> = _scrollTarget.asStateFlow()

    /**
     * Opening at the last page, or "Last page" from the menu, lands on the newest post, as Whirlpool's `?p=-1#bottom`
     * does. Which page that is is known only once `?p=-1` resolves (the cached guess may be stale).
     */
    private var toLastPost = route.page == LAST_PAGE && route.scrollToReply == null && route.scrollToPost == null

    private val currentPage = MutableStateFlow<Int?>(null)

    private val events = Channel<ThreadEvent>(Channel.BUFFERED)
    val navigation: Flow<ThreadEvent> = events.receiveAsFlow()

    private var loadJob: Job? = null

    /** Watched as far as the cached lists know; opened from Watched counts until they say otherwise. */
    val isWatched: StateFlow<Boolean> = watchedRepo.isWatched(threadId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, route.fromWatched)

    /**
     * The first unread post (a post number), fixed as the thread opened so marking it read leaves the marker where
     * it was, as reloading the page would on Whirlpool. Null when nothing is unread or the thread is not watched.
     */
    private val _firstUnread = MutableStateFlow(route.lastReadPost?.let { it + 1 })
    val firstUnread: StateFlow<Int?> = _firstUnread.asStateFlow()

    init {
        open(route.page)
        viewModelScope.launch {
            watchedRepo.ensureKnown()
            // Opened from elsewhere (Recent, a link): the cached watched lists know the position, until marked read below.
            if (_firstUnread.value == null) _firstUnread.value = watchedRepo.lastReadPost(threadId)?.let { it + 1 }
            autoMarkRead()
        }
    }

    /** Find on page: null while the bar is closed, else what is typed. Saved, so it survives process death. */
    val findQuery: StateFlow<String?> = savedState.getStateFlow(FIND_KEY, null)

    fun setFindQuery(query: String?) {
        savedState[FIND_KEY] = query
    }

    /**
     * The posts on the page on screen that match the find query, by reply id in page order: those with every word in
     * their text or the poster's name. Empty with no query.
     */
    val findMatches: StateFlow<FindMatches?> = combine(currentPage, findQuery) { page, query -> page to query }
        .flatMapLatest { (page, query) ->
            val words = TextQuery(query)
            if (page == null || words.isBlank) {
                flowOf(null)
            } else {
                page(page).map { ui ->
                    FindMatches(page, words, ui?.posts.orEmpty().filter { matchesFind(it, words) }.map { it.post.replyId })
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Scrolls to a find match on [page]. */
    fun showMatch(page: Int, replyId: Long) {
        _scrollTarget.value = ScrollTarget(page, replyId = replyId)
    }

    /** The page holding the first unread post. */
    fun firstUnreadPage(): Int? = _firstUnread.value?.let { pageOfPost(it) }

    /** Shows the first unread post. */
    fun goToFirstUnread() {
        val post = _firstUnread.value ?: return
        val page = pageOfPost(post)
        _scrollTarget.value = ScrollTarget(page, postNumber = post)
        _jumpTo.value = page.coerceAtMost(_state.value.pageCount)
    }

    /**
     * "Mark as read when opened" for a watched thread opened from anywhere but Watched (which marks it as the row is
     * tapped, also covering threads that open in the browser): Recent, Popular, search, links.
     */
    private suspend fun autoMarkRead() {
        if (route.fromWatched || !settingsRepo.settings.first().watchedAutoMarkAsRead) return
        if (watchedRepo.isWatched(threadId).first()) watchQueue.perform(WatchAction.MARK_READ, listOf(threadId), undoable = false)
    }

    /**
     * Watch, mark read or unwatch (with Undo unless it is turned off); marking read can return to the list (2.14.12
     * `pref_watchedbacktolist`).
     */
    fun perform(action: WatchAction) {
        viewModelScope.launch {
            val current = settingsRepo.settings.first()
            watchQueue.perform(action, listOf(threadId), undoable = current.watchUndo, announce = true)
            if (action == WatchAction.MARK_READ && route.fromWatched && current.watchedBackToList) events.send(ThreadEvent.Back)
        }
    }

    private val pages = ConcurrentHashMap<Int, Flow<UiPage?>>()

    /**
     * Pages with their bodies parsed off the main thread. One shared flow per page, so the pager and find on page
     * parse each page once; it stops (and forgets the page) 5 s after the page leaves the screen.
     */
    fun page(page: Int): Flow<UiPage?> = pages.getOrPut(page) {
        repo.page(threadId, page).map { cached ->
            cached?.let { c ->
                UiPage(
                    page = c.value,
                    posts = c.value.posts.map { UiPost(it, PostBodyParser.parse(it.bodyHtml)) },
                    note = c.value.moderatorNoteHtml?.let(PostBodyParser::parse),
                    updatedAt = c.updatedAt,
                )
            }
        }.flowOn(parsing).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000, replayExpirationMillis = 0), replay = 1)
    }

    /** Resolves the start page (cache first, then the network) and loads it. */
    private fun open(page: Int) {
        viewModelScope.launch {
            val known = repo.lastKnownPage(threadId)
            if (page == LAST_PAGE) {
                known?.let {
                    start(it, it)
                    if (toLastPost) _scrollTarget.value = ScrollTarget(it, lastPost = true)
                }
            } else {
                start(page, maxOf(page, known ?: 1))
            }
            // Offline, the fetch below fails; the cached page still knows the thread's title.
            _state.value.startPage?.let { start ->
                repo.page(threadId, start).first()?.value?.title?.let { title -> _state.update { it.copy(title = title) } }
            }
            // Watched says there are posts the cached page does not have: fetch it even if it is fresh.
            val target = _scrollTarget.value
            val missing = target?.waitForFetch == true && target.postNumber != null &&
                repo.page(threadId, target.page).first()?.value?.posts?.none { it.number == target.postNumber } != false
            load(page, force = missing)
        }
    }

    private fun start(page: Int, count: Int) {
        _state.update { it.copy(pageCount = count, startPage = it.startPage ?: page) }
        currentPage.value = currentPage.value ?: page
    }

    /** Called when the pager settles on a page. */
    fun showPage(page: Int) {
        if (currentPage.value == page) return
        currentPage.value = page
        _state.update { it.copy(error = null) }
        load(page, force = false)
    }

    fun refresh() {
        load(currentPage.value ?: route.page, force = true)
    }

    private fun load(page: Int, force: Boolean) {
        loadJob?.cancel()
        _state.update { it.copy(refreshing = true) }
        val job = viewModelScope.launch {
            try {
                val resolved = repo.load(threadId, page, route.forumId, force)
                val cached = repo.page(threadId, resolved).first()
                settleTarget(resolved, cached?.updatedAt)
                val loaded = cached?.value
                if (loaded != null) _state.update { it.copy(pageCount = loaded.pageCount, title = loaded.title) }
                if (page == LAST_PAGE && toLastPost) {
                    toLastPost = false
                    _scrollTarget.value = ScrollTarget(resolved, lastPost = true)
                }
                if (_state.value.startPage == null) {
                    start(resolved, loaded?.pageCount ?: resolved)
                } else if (page == LAST_PAGE && resolved != currentPage.value) {
                    // The cached "last page" was stale: move to the real one.
                    currentPage.value = resolved
                    _jumpTo.value = resolved
                }
                _state.update { it.copy(error = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Offline: the cached copy is all there is.
                if (page != LAST_PAGE) settleTarget(page, repo.page(threadId, page).first()?.updatedAt)
                _state.update { it.copy(error = e) }
            }
        }
        loadJob = job
        // Only the newest load clears the indicator, not one it cancelled.
        job.invokeOnCompletion { if (loadJob === job) _state.update { it.copy(refreshing = false) } }
    }

    fun jumpHandled() {
        _jumpTo.value = null
    }

    fun scrollHandled() {
        _scrollTarget.value = null
    }

    /** A load of [page] ended: a target waiting for it is decided by the copy fetched at [at] (null: nothing cached). */
    private fun settleTarget(page: Int, at: Instant?) {
        _scrollTarget.update { t ->
            if (t != null && t.waitForFetch && t.fetched == null && t.page == page) t.copy(fetched = at ?: Instant.EPOCH) else t
        }
    }

    /** The reader scrolled: a target still waiting for the fetched page would move the posts under them. */
    fun userScrolled() {
        _scrollTarget.update { t -> t?.takeUnless { it.waitForFetch } }
    }

    fun goToPage(page: Int) {
        _jumpTo.value = page.coerceIn(1, _state.value.pageCount)
    }

    /** Loads `?p=-1`; [load] then moves the pager to the page it resolves to, at its newest post. */
    fun lastPage() {
        toLastPost = true
        load(LAST_PAGE, force = false)
    }

    /**
     * The filters (posts by a user, the OP, moderators, reps) switch to the scroll reader, which filters the thread's
     * archive pages in the app: Whirlpool shows its own filtered views only to signed-in users, and Whirl has no web
     * session.
     */
    private fun readFiltered(filter: ReaderFilter) {
        viewModelScope.launch { events.send(ThreadEvent.ReadFiltered(filter)) }
    }

    fun showUsersPosts(userId: Long, name: String) = readFiltered(ReaderFilter.User(userId, name))
    fun showMyPosts() = readFiltered(ReaderFilter.Mine)
    fun showOpPosts() = readFiltered(ReaderFilter.Op)
    fun showModeratorPosts() = readFiltered(ReaderFilter.Moderators)
    fun showRepPosts() = readFiltered(ReaderFilter.Reps)

    fun openUser(userId: Long) {
        viewModelScope.launch { events.send(ThreadEvent.OpenUser(userId)) }
    }

    /** Another part of a multi-part thread. */
    fun openPart(id: ThreadId) {
        viewModelScope.launch { showPost(id, 1, null) }
    }

    /** A quote attribution: scroll if the quoted post is on [onPage], else find its page through the site. */
    fun openQuote(replyId: Long, onPage: UiPage?) {
        if (onPage?.posts?.any { it.post.replyId == replyId } == true) {
            _scrollTarget.value = ScrollTarget(onPage.page.page, replyId = replyId)
            return
        }
        locate(replyId)
    }

    private fun locate(replyId: Long) {
        viewModelScope.launch {
            try {
                val at = repo.locate(replyId)
                showPost(at.threadId, at.page, ScrollTarget(at.page, postNumber = at.postNumber))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.send(ThreadEvent.Failed(e))
            }
        }
    }

    /** Moves within this thread when possible, otherwise opens the other thread. */
    private suspend fun showPost(id: ThreadId, page: Int, target: ScrollTarget?) {
        if (id == threadId) {
            _state.update { it.copy(pageCount = maxOf(it.pageCount, page)) }
            _scrollTarget.value = target
            _jumpTo.value = page
        } else {
            events.send(
                ThreadEvent.OpenThread(ThreadRoute(id.value, page, scrollToPost = target?.postNumber, scrollToReply = target?.replyId)),
            )
        }
    }

    /** Routes a link from a post: Whirlpool pages in the app, everything else to the browser. */
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
                val page = link.page ?: 1
                val target = link.anchor?.let {
                    if (link.isReplyIdAnchor) ScrollTarget(page, replyId = it) else ScrollTarget(page, postNumber = it.toInt())
                }
                showPost(link.id, page, target)
            }
            // The archive is the whole thread on one page; the paged view shows the same posts.
            is WhirlpoolLink.Archive -> events.send(ThreadEvent.OpenThread(ThreadRoute(link.id.value)))
            is WhirlpoolLink.Forum -> events.send(ThreadEvent.OpenForum(ForumRef(link.id, "", link.groupId)))
            is WhirlpoolLink.User -> events.send(ThreadEvent.OpenUser(link.id))
            is WhirlpoolLink.Reply -> locate(link.replyId)
            // Where whrl.pl redirects is unverified; if it cannot be followed, the browser can.
            is WhirlpoolLink.Short -> {
                val target = try {
                    repo.resolveShortLink(link.url)
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

    private val _topPost = MutableStateFlow<Int?>(null)

    /** The post at the top of the page on screen, for switching to the scroll reader. */
    fun setTopPost(page: Int, number: Int) {
        if (page == currentPage.value) _topPost.value = number
    }

    fun topPost(): Int? = _topPost.value

    fun threadUrl(page: Int? = currentPage.value): String = urls.thread(threadId, page ?: 1).toString()
    fun postUrl(post: Post, page: Int): String = urls.thread(threadId, page).newBuilder().fragment("r${post.number}").build().toString()
    fun replyToThreadUrl(): String = urls.replyToThread(threadId).toString()
    fun replyToPostUrl(post: Post): String = urls.replyToPost(post.replyId).toString()
    fun editPostUrl(post: Post): String = urls.editPost(post.replyId).toString()

    /** Whirlpool's "alert moderators" form. */
    fun reportPostUrl(post: Post): String =
        urls.reportPost(post.replyId, threadId, pageOfPost(post.number), post.number).toString()
}

/** Find on page's matches: the reply ids of the matching posts on [page], for [query]. */
data class FindMatches(val page: Int, val query: TextQuery, val replyIds: List<Long>)

/** A post matches when every word is in its text (as find can mark it) or the poster's name. */
internal fun matchesFind(post: UiPost, query: TextQuery): Boolean = query.matches(post.post.author.name, post.body.searchText())

private const val FIND_KEY = "findOnPage"
