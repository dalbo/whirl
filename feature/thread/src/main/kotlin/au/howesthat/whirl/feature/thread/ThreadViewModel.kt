package au.howesthat.whirl.feature.thread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadPageRepository
import au.howesthat.whirl.core.data.ThreadPageRepository.Companion.LAST_PAGE
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.data.WhirlpoolLink
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.PostFilter
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadPage
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.scraper.PostBodyParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * A thread, opened at [page] (1-based, or -1 for the last page). [scrollToPost] is a post number, [scrollToReply] a
 * reply id; [userId] opens it filtered to one user's posts (`ux=`); [fromWatched] when opened from Watched;
 * [lastReadPost] the last post read when it was opened, for the "New posts" marker.
 */
@Serializable
data class ThreadRoute(
    val threadId: String,
    val page: Int = 1,
    val scrollToPost: Int? = null,
    val scrollToReply: Long? = null,
    val forumId: Int? = null,
    val userId: Long? = null,
    val fromWatched: Boolean = false,
    val lastReadPost: Int? = null,
) : NavKey

/** A post ready to draw: its body parsed into blocks. */
data class UiPost(val post: Post, val body: PostBody)

data class UiPage(val page: ThreadPage, val posts: List<UiPost>, val note: PostBody?, val updatedAt: Instant?)

/** A post to bring into view once its page is showing. */
data class ScrollTarget(val page: Int, val postNumber: Int? = null, val replyId: Long? = null)

/** One-off navigation out of the thread. */
sealed interface ThreadEvent {
    data class OpenThread(val route: ThreadRoute) : ThreadEvent
    data class OpenForum(val forum: ForumRef) : ThreadEvent
    data class OpenUser(val userId: Long) : ThreadEvent
    data class Browser(val url: String) : ThreadEvent
    data class Failed(val error: Throwable) : ThreadEvent
    /** Back to the list ("back to list after marking read"). */
    data object Back : ThreadEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted private val route: ThreadRoute,
    private val repo: ThreadPageRepository,
    private val settingsRepo: SettingsRepository,
    private val watchedRepo: WatchedRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: ThreadRoute): ThreadViewModel
    }

    val threadId = ThreadId(route.threadId)
    private val urls = WhirlpoolUrls()

    val settings: StateFlow<Settings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val myUserId: StateFlow<Long?> = settingsRepo.account.map { it?.userId }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _filter = MutableStateFlow<PostFilter>(route.userId?.let(PostFilter::ByUser) ?: PostFilter.All)
    val filter: StateFlow<PostFilter> = _filter.asStateFlow()

    /** The first page to show; null until it is known (from the cache or the first fetch). */
    private val _startPage = MutableStateFlow<Int?>(null)
    val startPage: StateFlow<Int?> = _startPage.asStateFlow()

    private val _pageCount = MutableStateFlow(1)
    val pageCount: StateFlow<Int> = _pageCount.asStateFlow()

    private val _title = MutableStateFlow<String?>(null)
    val title: StateFlow<String?> = _title.asStateFlow()

    /** Set when the pager should move (a quote on another page, "last page" turning out to be later). */
    private val _jumpTo = MutableStateFlow<Int?>(null)
    val jumpTo: StateFlow<Int?> = _jumpTo.asStateFlow()

    private val _scrollTarget = MutableStateFlow(
        when {
            route.scrollToReply != null -> ScrollTarget(route.page, replyId = route.scrollToReply)
            route.scrollToPost != null -> ScrollTarget(route.page, postNumber = route.scrollToPost)
            else -> null
        },
    )
    val scrollTarget: StateFlow<ScrollTarget?> = _scrollTarget.asStateFlow()

    private val currentPage = MutableStateFlow<Int?>(null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    /** The last failure for the page on screen (or for opening the thread). */
    val error: StateFlow<Throwable?> = _error.asStateFlow()

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

    /** The page holding the first unread post, in the unfiltered thread. */
    fun firstUnreadPage(): Int? = _firstUnread.value?.let { pageOfPost(it) }

    /** Shows the first unread post; only offered without a filter, where pages follow post numbers. */
    fun goToFirstUnread() {
        val post = _firstUnread.value ?: return
        if (_filter.value != PostFilter.All) return
        val page = pageOfPost(post)
        _scrollTarget.value = ScrollTarget(page, postNumber = post)
        _jumpTo.value = page.coerceAtMost(_pageCount.value)
    }

    /**
     * "Mark as read when opened" for a watched thread opened from anywhere but Watched (which marks it as the row is
     * tapped, also covering threads that open in the browser): Recent, Popular, search, links.
     */
    private suspend fun autoMarkRead() {
        if (route.fromWatched || !settingsRepo.settings.first().watchedAutoMarkAsRead) return
        if (watchedRepo.isWatched(threadId).first()) watchedRepo.perform(WatchAction.MARK_READ, listOf(threadId), undoable = false)
    }

    /** Watch, mark read or unwatch (with Undo); marking read can return to the list (2.14.12 `pref_watchedbacktolist`). */
    fun perform(action: WatchAction) {
        viewModelScope.launch {
            watchedRepo.perform(action, listOf(threadId))
            if (action == WatchAction.MARK_READ && route.fromWatched && settings.value.watchedBackToList) events.send(ThreadEvent.Back)
        }
    }

    /** Pages of the current filter; bodies are parsed off the main thread. */
    fun page(page: Int): Flow<UiPage?> = _filter.flatMapLatest { filter ->
        repo.page(threadId, page, filter).map { cached ->
            cached?.let { c ->
                UiPage(
                    page = c.value,
                    posts = c.value.posts.map { UiPost(it, PostBodyParser.parse(it.bodyHtml)) },
                    note = c.value.moderatorNoteHtml?.let(PostBodyParser::parse),
                    updatedAt = c.updatedAt,
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    /** Resolves the start page (cache first, then the network) and loads it. */
    private fun open(page: Int) {
        _startPage.value = null
        currentPage.value = null
        viewModelScope.launch {
            val filter = _filter.value
            val known = repo.lastKnownPage(threadId, filter)
            if (page == LAST_PAGE) {
                known?.let { start(it, it) }
            } else {
                start(page, maxOf(page, known ?: 1))
            }
            // Offline, the fetch below fails; the cached page still knows the thread's title.
            _startPage.value?.let { start -> repo.page(threadId, start, filter).first()?.value?.title?.let { _title.value = it } }
            load(page, force = false)
        }
    }

    private fun start(page: Int, count: Int) {
        _pageCount.value = count
        if (_startPage.value == null) _startPage.value = page
        currentPage.value = currentPage.value ?: page
    }

    /** Called when the pager settles on a page. */
    fun showPage(page: Int) {
        if (currentPage.value == page) return
        currentPage.value = page
        _error.value = null
        load(page, force = false)
    }

    fun refresh() {
        load(currentPage.value ?: route.page, force = true)
    }

    private fun load(page: Int, force: Boolean) {
        loadJob?.cancel()
        _refreshing.value = true
        val job = viewModelScope.launch {
            try {
                val filter = _filter.value
                val resolved = repo.load(threadId, page, filter, route.forumId, force)
                val loaded = repo.page(threadId, resolved, filter).first()?.value
                if (loaded != null) {
                    _pageCount.value = loaded.pageCount
                    _title.value = loaded.title
                }
                if (_startPage.value == null) {
                    start(resolved, loaded?.pageCount ?: resolved)
                } else if (page == LAST_PAGE && resolved != currentPage.value) {
                    // The cached "last page" was stale: move to the real one.
                    currentPage.value = resolved
                    _jumpTo.value = resolved
                }
                _error.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e
            }
        }
        loadJob = job
        // Only the newest load clears the indicator, not one it cancelled.
        job.invokeOnCompletion { if (loadJob === job) _refreshing.value = false }
    }

    fun jumpHandled() {
        _jumpTo.value = null
    }

    fun scrollHandled() {
        _scrollTarget.value = null
    }

    fun goToPage(page: Int) {
        _jumpTo.value = page.coerceIn(1, _pageCount.value)
    }

    /** Loads `?p=-1`; [load] then moves the pager to the page it resolves to. */
    fun lastPage() {
        load(LAST_PAGE, force = false)
    }

    private val _opUserId = MutableStateFlow<Long?>(null)
    /** The thread starter, once "Posts by OP" has found them. */
    val opUserId: StateFlow<Long?> = _opUserId.asStateFlow()

    /** "Posts by OP": the thread starter wrote the first post on page 1, which is fetched if it is not cached. */
    fun showOpPosts() {
        _opUserId.value?.let { setFilter(PostFilter.ByUser(it)); return }
        viewModelScope.launch {
            try {
                repo.load(threadId, 1, PostFilter.All, route.forumId)
                val op = repo.page(threadId, 1).first()?.value?.posts?.firstOrNull()?.author?.id ?: return@launch
                _opUserId.value = op
                setFilter(PostFilter.ByUser(op))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                events.send(ThreadEvent.Failed(e))
            }
        }
    }

    /**
     * Whirlpool shows filtered views (one user's posts, moderators', reps') only to signed-in users ("You must be
     * registered and signed in", checked 2026-10-23), and Whirl has no web session, so they open in the browser, which
     * has the user's login. All posts reloads in the app.
     */
    fun setFilter(filter: PostFilter) {
        if (filter != PostFilter.All) {
            viewModelScope.launch { events.send(ThreadEvent.Browser(filterUrl(filter))) }
            return
        }
        if (_filter.value == filter) return
        _filter.value = filter
        _scrollTarget.value = null
        open(1)
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
        if (id == threadId && _filter.value == PostFilter.All) {
            if (page > _pageCount.value) _pageCount.value = page
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

    fun threadUrl(page: Int? = currentPage.value): String = urls.thread(threadId, page ?: 1).toString()
    fun filterUrl(filter: PostFilter): String = urls.thread(
        threadId,
        userId = (filter as? PostFilter.ByUser)?.userId,
        moderatorsOnly = filter == PostFilter.Moderators,
        repsOnly = filter == PostFilter.Reps,
    ).toString()
    fun postUrl(post: Post, page: Int): String = urls.thread(threadId, page).newBuilder().fragment("r${post.number}").build().toString()
    fun replyToThreadUrl(): String = urls.replyToThread(threadId).toString()
    fun replyToPostUrl(post: Post): String = urls.replyToPost(post.replyId).toString()
    fun editPostUrl(post: Post): String = urls.editPost(post.replyId).toString()

    /** Whirlpool's "alert moderators" form; its page is the post's page in the unfiltered thread. */
    fun reportPostUrl(post: Post): String =
        urls.reportPost(post.replyId, threadId, pageOfPost(post.number), post.number).toString()
}

/** Whirlpool shows 20 posts per thread page. */
private const val POSTS_PER_PAGE = 20

private fun pageOfPost(number: Int) = (number - 1) / POSTS_PER_PAGE + 1
