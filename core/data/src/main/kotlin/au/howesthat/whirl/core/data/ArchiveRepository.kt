package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.ArchiveDao
import au.howesthat.whirl.core.database.ArchiveIndexEntity
import au.howesthat.whirl.core.database.ArchivePageEntity
import au.howesthat.whirl.core.database.ArchivePostEntity
import au.howesthat.whirl.core.database.ArchiveThreadEntity
import au.howesthat.whirl.core.model.ArchivePage
import au.howesthat.whirl.core.model.ArchivePageRef
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.PostRole
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.WhirlpoolWeb
import au.howesthat.whirl.core.scraper.ArchivePageParser
import au.howesthat.whirl.core.scraper.WhirlpoolTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

/**
 * The archive reader's data (docs/plans/v1.8.md §3): `/archive/` pages of up to 800 posts, cached in Room.
 *
 * Only the open thread's part is kept: a multi-part thread's other parts number their posts from 1 again. Pages are
 * fetched only when the reader asks (opening, "Load earlier", "Search earlier pages"), one at a time and at least
 * [spacing] apart, with a daily cap per thread: Whirlpool forbids crawling.
 */
@Singleton
class ArchiveRepository internal constructor(
    private val web: WhirlpoolWeb,
    private val dao: ArchiveDao,
    private val threadPages: ThreadPageRepository,
    private val clock: Clock,
    private val parsing: CoroutineDispatcher,
    private val spacing: Duration,
) {
    @Inject constructor(
        web: WhirlpoolWeb,
        dao: ArchiveDao,
        threadPages: ThreadPageRepository,
        clock: Clock,
        @DefaultDispatcher parsing: CoroutineDispatcher,
    ) : this(web, dao, threadPages, clock, parsing, SPACING)

    /** The open thread's archive: its part's pages in order. */
    data class ArchiveThread(
        val threadId: ThreadId,
        val title: String,
        val forum: ForumRef?,
        val pages: List<ArchivePageRef>,
    ) {
        /** The page holding post [number]: exact, since posts the archive leaves out keep their numbers. */
        fun pageFor(number: Int): ArchivePageRef? =
            pages.getOrNull((number - 1) / ArchivePage.POSTS_PER_PAGE)?.takeIf { number >= 1 }

        fun indexOf(path: String): Int = pages.indexOfFirst { it.path == path }
    }

    /** Where [open] put the reader. [path] is cached; [number] is the post to show (null: the end). */
    data class Opened(val thread: ArchiveThread, val path: String, val number: Int?, val offline: Boolean)

    /**
     * Makes the archive page holding post [number] (null: the newest post) available and returns where it is.
     *
     * Online, the normal last page (`?p=-1`, small and usually cached) gives the newest post's number, so the right
     * archive page is fetched directly (`/archive/<slug>-K`); a multi-part thread, or a guess that 404s, goes through
     * the entry page's list. Offline, the cached page holding the post, or the newest cached page, will do.
     */
    suspend fun open(id: ThreadId, number: Int? = null, forumId: Int? = null): Opened {
        dao.forgetBefore(clock.millis() - FORGET_AFTER.inWholeMilliseconds)
        val cached = cachedThread(id)
        try {
            val lastPage = threadPages.load(id, ThreadPageRepository.LAST_PAGE, forumId)
            val tail = threadPages.page(id, lastPage).first()?.value
                ?: throw WhirlpoolError.Drift("Thread ${id.value} has no last page")
            val newest = tail.posts.maxOf { it.number }
            val target = (number ?: newest).coerceIn(1, newest)
            val path = cached?.pageFor(target)?.path
            // An earlier page rarely changes; the last one is current while it holds the newest post.
            val stored = path?.let { dao.page(id.value, it) }
            if (cached != null && stored != null && (path != cached.pages.last().path || stored.last >= newest)) {
                dao.touch(id.value, stored.path, clock.millis())
                return Opened(cached, stored.path, number, offline = false)
            }
            val page = when {
                path != null -> fetch(id, path)
                tail.parts.isNotEmpty() -> fetchVia(id, target)
                else -> {
                    val k = (target - 1) / ArchivePage.POSTS_PER_PAGE + 1
                    try {
                        fetch(id, if (k == 1) entryPath(id) else "${entryPath(id)}-$k")
                    } catch (e: WhirlpoolError.Api) {
                        if (e.httpCode != 404) throw e
                        fetchVia(id, target)
                    }
                }
            }.let { if (target in rangeOf(it)) it else fetchVia(id, target, start = it) }
            val thread = cachedThread(id)!!
            return Opened(thread, thread.pages[page.pageInPart - 1].path, number, offline = false)
        } catch (e: WhirlpoolError.Network) {
            cached ?: throw e
            val pages = dao.pages(id.value).ifEmpty { throw e }
            val page = number?.let { n -> pages.firstOrNull { n in it.first..it.last } } ?: pages.last()
            return Opened(cached, page.path, number, offline = true)
        }
    }

    /** The cached posts of [path], oldest first; empty when the page is not cached (or was evicted). */
    suspend fun posts(id: ThreadId, path: String): List<Post> {
        dao.touch(id.value, path, clock.millis())
        return dao.posts(id.value, path).map { it.toModel() }
    }

    /** What [loadPages] needs of each page: its bodies (reading, find) or only who wrote what (filters). */
    enum class Need { BODIES, INDEX }

    /** Why [loadPages] stopped early, if it did. */
    enum class Stop { DAILY_CAP, RATE_LIMITED }

    data class LoadResult(val fetched: Int, val stoppedBy: Stop?)

    /**
     * Fetches the pages at [paths] that lack what [need] asks for, one at a time, [spacing] apart, newest first if
     * given so. Stops at the daily cap or when Whirlpool rate limits; cancelling the caller stops between pages.
     */
    suspend fun loadPages(
        id: ThreadId,
        paths: List<String>,
        need: Need,
        onProgress: suspend (done: Int, total: Int) -> Unit = { _, _ -> },
    ): LoadResult {
        val have = when (need) {
            Need.BODIES -> dao.pages(id.value).map { it.path }.toSet()
            Need.INDEX -> dao.indexedPaths(id.value).toSet()
        }
        val todo = paths.filter { it !in have }
        var fetched = 0
        for ((i, path) in todo.withIndex()) {
            if (!takeFromDailyCap(id)) return LoadResult(fetched, Stop.DAILY_CAP)
            if (fetched > 0) delay(spacing)
            try {
                fetch(id, path)
            } catch (_: WhirlpoolError.RateLimited) {
                return LoadResult(fetched, Stop.RATE_LIMITED)
            }
            fetched++
            onProgress(i + 1, todo.size)
        }
        return LoadResult(fetched, null)
    }

    /** Posts picked out by [filter] among every page fetched so far (bodies not needed), oldest first. */
    suspend fun filter(id: ThreadId, filter: PostFilter): List<IndexedPost> = when (filter) {
        is PostFilter.User -> dao.byUser(id.value, filter.userId)
        PostFilter.Op -> dao.byOp(id.value)
        PostFilter.Moderators -> dao.byGroup(id.value, PostRole.MODERATOR_GROUPS.toList())
        PostFilter.Reps -> dao.byReps(id.value, PostRole.REP_GROUP)
    }.map { IndexedPost(it.replyId, it.replyNum, it.path, UserRef(it.userId, it.userName), PostRole.of(it.userGroup, it.repOf)) }

    /** Paths of the open part's pages whose posts are known to [filter]. */
    suspend fun indexedPaths(id: ThreadId): Set<String> = dao.indexedPaths(id.value).toSet()

    suspend fun thread(id: ThreadId): ArchiveThread? = cachedThread(id)

    /** Learns the list from [start] (or the entry page), then fetches the page holding [target] if [start] is not it. */
    private suspend fun fetchVia(id: ThreadId, target: Int, start: ArchivePage? = null): ArchivePage {
        val start = start ?: fetch(id, entryPath(id))
        val ref = cachedThread(id)!!.pageFor(target)
            ?: throw WhirlpoolError.Drift("Post $target is past the end of thread ${id.value}'s archive")
        if (ref.path == start.partPages[start.pageInPart - 1].path) return start
        return fetch(id, ref.path).also {
            if (target !in rangeOf(it)) throw WhirlpoolError.Drift("Post $target is not on ${ref.path}")
        }
    }

    /** The numbers a page covers: up to the next page's first number, or its own last post on the last page. */
    private fun rangeOf(page: ArchivePage): IntRange {
        val first = (page.pageInPart - 1) * ArchivePage.POSTS_PER_PAGE + 1
        val last = if (page.pageInPart == page.partPages.size) page.posts.maxOf { it.number } else first + ArchivePage.POSTS_PER_PAGE - 1
        return first..last
    }

    private suspend fun fetch(id: ThreadId, path: String): ArchivePage {
        val html = web.get(web.urls.archivePage(path))
        val page = parseOrDrift(parsing) { ArchivePageParser.parse(html) }
        store(id, page)
        return page
    }

    private suspend fun store(id: ThreadId, page: ArchivePage) {
        val now = clock.millis()
        val path = page.partPages[page.pageInPart - 1].path
        val old = dao.thread(id.value)
        val thread = ArchiveThreadEntity(
            threadId = id.value, title = page.title, forumId = page.forum?.id, forumTitle = page.forum?.title,
            groupId = page.forum?.groupId, pages = page.partPages.joinToString("\n") { "${it.path}|${it.label.orEmpty()}" },
            fetchedAt = now, searchedDay = old?.searchedDay ?: 0, searchedToday = old?.searchedToday ?: 0,
        )
        val range = rangeOf(page)
        dao.store(
            thread,
            ArchivePageEntity(id.value, path, range.first, range.last, fetchedAt = now, accessedAt = now),
            page.posts.map { it.toEntity(id, path) },
            page.posts.map { ArchiveIndexEntity(id.value, it.replyId, path, it.number, it.author.id, it.author.name, it.userGroup, it.repOf, it.isOp) },
            keep = KEEP_PAGES,
        )
    }

    private suspend fun cachedThread(id: ThreadId): ArchiveThread? = dao.thread(id.value)?.let { t ->
        ArchiveThread(
            ThreadId(t.threadId), t.title, t.forumId?.let { ForumRef(it, t.forumTitle.orEmpty(), t.groupId) },
            t.pages.split('\n').filter(String::isNotEmpty).map { line ->
                ArchivePageRef(line.substringBefore('|'), line.substringAfter('|', "").ifEmpty { null })
            },
        )
    }

    /** Counts one fetched page against today's cap (Sydney days); false when it is used up. */
    private suspend fun takeFromDailyCap(id: ThreadId): Boolean {
        val t = dao.thread(id.value) ?: return true
        val today = LocalDateTime.ofInstant(clock.instant(), WhirlpoolTime.SYDNEY).toLocalDate().toEpochDay()
        val used = if (t.searchedDay == today) t.searchedToday else 0
        if (used >= DAILY_CAP) return false
        dao.setSearched(id.value, today, used + 1)
        return true
    }

    private fun entryPath(id: ThreadId) = "/archive/${id.value}"

    companion object {
        /** Archive pages kept with their bodies (each up to 800 posts), besides the open thread's. */
        const val KEEP_PAGES = 12
        /** Earlier pages one thread may fetch in a day through [loadPages]. */
        const val DAILY_CAP = 60
        val SPACING = 1.seconds
        /** A thread's list and index are forgotten when it has not been opened for this long. */
        val FORGET_AFTER = 90.days
    }
}

/** Posts picked out in the app from the archive (replacing the site's signed-in `ux=`, `um=`, `ur=` views). */
sealed interface PostFilter {
    data class User(val userId: Long) : PostFilter
    data object Op : PostFilter
    data object Moderators : PostFilter
    data object Reps : PostFilter
}

/** A post found by a [PostFilter]: enough to list it and to open its page. */
data class IndexedPost(val replyId: Long, val number: Int, val path: String, val author: UserRef, val role: PostRole?)

private fun Post.toEntity(id: ThreadId, path: String) = ArchivePostEntity(
    threadId = id.value, path = path, replyId = replyId, replyNum = number, userId = author.id, userName = author.name,
    userGroup = userGroup, quip = quip, postCount = postCount, postedText = postedText,
    postedAt = postedAt?.atZone(WhirlpoolTime.SYDNEY)?.toInstant()?.toEpochMilli(), editedText = editedText,
    isOp = isOp, isDeleted = isDeleted, shortUrl = shortUrl, bodyHtml = bodyHtml, repOf = repOf,
)

private fun ArchivePostEntity.toModel() = Post(
    replyId = replyId, number = replyNum, author = UserRef(userId, userName), userGroup = userGroup, quip = quip,
    postCount = postCount, postedText = postedText,
    postedAt = postedAt?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), WhirlpoolTime.SYDNEY) },
    editedText = editedText, isOp = isOp, hiddenReason = null, isDeleted = isDeleted, shortUrl = shortUrl,
    bodyHtml = bodyHtml, repOf = repOf,
)
