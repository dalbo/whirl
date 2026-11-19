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
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.searchText
import au.howesthat.whirl.core.scraper.PostBodyParser
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.WhirlpoolWeb
import au.howesthat.whirl.core.scraper.ArchivePageParser
import au.howesthat.whirl.core.scraper.WhirlpoolTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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
    data class Opened(
        val thread: ArchiveThread,
        val path: String,
        val number: Int?,
        val offline: Boolean,
        /** From the normal last page: the archive has none. */
        val moderatorNoteHtml: String? = null,
        /** The page at [path] was fetched or had new posts added: what a reader showing it from the cache must update. */
        val changed: Boolean = true,
    )

    /**
     * The archive page holding post [number] (null: the newest cached), from the cache alone, for showing at once
     * while [open] checks for newer posts; null when it is not cached whole.
     */
    suspend fun openCached(id: ThreadId, number: Int? = null): Opened? {
        val thread = cachedThread(id) ?: return null
        val pages = dao.pages(id.value).filter { it.complete && thread.indexOf(it.path) >= 0 }.ifEmpty { return null }
        val page = if (number == null) pages.maxBy { it.first } else pages.firstOrNull { number in it.first..it.last } ?: return null
        val note = threadPages.lastKnownPage(id)?.let { threadPages.page(id, it).first()?.value?.moderatorNoteHtml }
        return Opened(thread, page.path, number, offline = false, note, changed = false)
    }

    /**
     * Makes the archive page holding post [number] (null: the newest post) available and returns where it is.
     *
     * Online, the normal last page (`?p=-1`, small and usually cached) gives the newest post's number, so the right
     * archive page is fetched directly (`/archive/<slug>-K`); a multi-part thread, or a guess that 404s, goes through
     * the entry page's list. Offline, the cached page holding the post, or the newest cached page, will do.
     */
    suspend fun open(id: ThreadId, number: Int? = null, forumId: Int? = null, force: Boolean = false): Opened {
        dao.forgetBefore(clock.millis() - FORGET_AFTER.inWholeMilliseconds)
        val cached = cachedThread(id)
        try {
            val lastPage = threadPages.load(id, ThreadPageRepository.LAST_PAGE, forumId, force)
            val tail = threadPages.page(id, lastPage).first()?.value
                ?: throw WhirlpoolError.Drift("Thread ${id.value} has no last page")
            val newest = tail.posts.maxOf { it.number }
            val target = (number ?: newest).coerceIn(1, newest)
            val path = cached?.pageFor(target)?.path
            // An earlier page rarely changes; the last one is current while it holds the newest post.
            val stored = path?.let { dao.page(id.value, it) }?.takeIf { it.complete }
            if (cached != null && stored != null && (path != cached.pages.last().path || stored.last >= newest)) {
                dao.touch(id.value, stored.path, clock.millis())
                return Opened(cached, stored.path, number, offline = false, tail.moderatorNoteHtml, changed = false)
            }
            // The cached last page is a few posts behind: the thread's last page has them, so add them rather than
            // fetching the whole archive page again (about 230 KB for a handful of posts).
            if (cached != null && stored != null && appendable(cached, stored, tail.posts, newest)) {
                append(id, stored, tail.posts)
                return Opened(cached, stored.path, number, offline = false, tail.moderatorNoteHtml, changed = true)
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
            return Opened(thread, thread.pages[page.pageInPart - 1].path, number, offline = false, tail.moderatorNoteHtml)
        } catch (e: WhirlpoolError.Network) {
            cached ?: throw e
            val pages = dao.pages(id.value).filter { it.complete }.ifEmpty { throw e }
            val page = number?.let { n -> pages.firstOrNull { n in it.first..it.last } } ?: pages.last()
            return Opened(cached, page.path, number, offline = true)
        }
    }

    /**
     * Whether [newest] can be reached by adding [tailPosts] (the thread's last page) to the cached last archive page
     * [stored]: few enough new posts, all on that page, and still within the same archive page.
     */
    private fun appendable(thread: ArchiveThread, stored: ArchivePageEntity, tailPosts: List<Post>, newest: Int): Boolean {
        if (stored.path != thread.pages.last().path || newest <= stored.last) return false
        if (newest - stored.last > APPEND_MAX) return false
        // A post past this archive page starts a new one, and the list with it: fetch.
        if (newest > thread.pages.size * ArchivePage.POSTS_PER_PAGE) return false
        val first = tailPosts.minOfOrNull { it.number } ?: return false
        return first <= stored.last + 1
    }

    /** Adds [tailPosts] newer than [stored]'s last post to it, their times made absolute as the archive writes them. */
    private suspend fun append(id: ThreadId, stored: ArchivePageEntity, tailPosts: List<Post>) {
        val now = java.time.ZonedDateTime.now(clock).withZoneSameInstant(WhirlpoolTime.SYDNEY)
        // Hidden posts are left out, as the archive leaves them out.
        val added = tailPosts.filter { it.number > stored.last && !it.isHidden }.map { post ->
            val at = post.postedAt ?: post.postedText?.let { WhirlpoolTime.parse(it, now)?.toLocalDateTime() }
            post.copy(postedAt = at, postedText = at?.let(WhirlpoolTime::format) ?: post.postedText)
        }
        val newest = tailPosts.maxOf { it.number }
        dao.append(
            stored.copy(last = maxOf(stored.last, newest), fetchedAt = clock.millis(), accessedAt = clock.millis()),
            added.map { it.toEntity(id, stored.path) },
            added.map { ArchiveIndexEntity(id.value, it.replyId, stored.path, it.number, it.author.id, it.author.name, it.userGroup, it.repOf, it.isOp) },
        )
    }

    /** The cached posts of [path], oldest first; empty unless the whole page is cached. */
    suspend fun posts(id: ThreadId, path: String): List<Post> {
        if (dao.page(id.value, path)?.complete != true) return emptyList()
        dao.touch(id.value, path, clock.millis())
        return dao.posts(id.value, path).map { it.toModel() }
    }

    /** Cached posts by reply id (whole pages or kept by [scan]), oldest first. */
    suspend fun postsByIds(id: ThreadId, replyIds: List<Long>): List<Post> =
        replyIds.chunked(500).flatMap { dao.postsByIds(id.value, it) }.map { it.toModel() }.sortedBy { it.number }

    /** What [scan] looks for. */
    sealed interface ScanQuery {
        fun matches(post: Post): Boolean

        data class Filter(val filter: PostFilter) : ScanQuery {
            override fun matches(post: Post) = filter.matches(post.author.id, post.isOp, post.role)
        }

        data class Text(val query: TextQuery) : ScanQuery {
            override fun matches(post: Post) = query.matches(post.author.name, PostBodyParser.parse(post.bodyHtml).searchText())
        }
    }

    /** What [scan] found: the matching posts' reply ids in thread order, the pages scanned, and why it stopped early. */
    data class ScanResult(val matches: List<Long>, val scanned: Int, val stoppedBy: Stop?)

    /**
     * Looks through the pages at [paths] (newest first, say) for posts matching [query]. A page is read from the cache
     * when it is whole, or, for a filter, when the index and the kept bodies already answer it; otherwise it is fetched
     * (one at a time, [spacing] apart, counting against the daily cap), its index stored and only the matching posts'
     * bodies kept. [onProgress] gets the pages done, the total and the matches so far.
     */
    suspend fun scan(
        id: ThreadId,
        paths: List<String>,
        query: ScanQuery,
        onProgress: suspend (done: Int, total: Int, matches: List<Long>) -> Unit = { _, _, _ -> },
    ): ScanResult {
        val found = sortedMapOf<Int, Long>()
        var fetched = 0
        for ((i, path) in paths.withIndex()) {
            val cached = fromCache(id, path, query)
            if (cached != null) {
                cached.forEach { (n, reply) -> found[n] = reply }
            } else {
                if (!takeFromDailyCap(id)) return ScanResult(found.values.toList(), i, Stop.DAILY_CAP)
                if (fetched > 0) delay(spacing)
                val page = try {
                    parse(web.get(web.urls.archivePage(path)))
                } catch (_: WhirlpoolError.RateLimited) {
                    return ScanResult(found.values.toList(), i, Stop.RATE_LIMITED)
                }
                fetched++
                val kept = withContext(parsing) { page.posts.filter(query::matches) }
                storeScanned(id, page, kept)
                kept.forEach { found[it.number] = it.replyId }
            }
            onProgress(i + 1, paths.size, found.values.toList())
        }
        return ScanResult(found.values.toList(), paths.size, null)
    }

    /** [query]'s matches on [path] from the cache, or null when the page must be fetched. */
    private suspend fun fromCache(id: ThreadId, path: String, query: ScanQuery): List<Pair<Int, Long>>? {
        val page = dao.page(id.value, path)
        if (page?.complete == true) {
            return withContext(parsing) { dao.posts(id.value, path).map { it.toModel() }.filter(query::matches) }
                .map { it.number to it.replyId }
        }
        if (query !is ScanQuery.Filter) return null
        val index = dao.index(id.value, path).ifEmpty { return null }
        val rows = index.filter { query.filter.matches(it.userId, it.isOp, PostRole.of(it.userGroup, it.repOf)) }
        // Bodies are needed to show the posts: a page scanned for another filter may not have kept these.
        if (rows.isNotEmpty() && !dao.keptIds(id.value, path).toSet().containsAll(rows.map { it.replyId })) return null
        return rows.map { it.replyNum to it.replyId }
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
            Need.BODIES -> dao.pages(id.value).filter { it.complete }.map { it.path }.toSet()
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
        val page = parse(web.get(web.urls.archivePage(path)))
        store(id, page)
        return page
    }

    private suspend fun parse(html: String): ArchivePage = parseOrDrift(parsing) { ArchivePageParser.parse(html) }

    private suspend fun threadRow(id: ThreadId, page: ArchivePage, now: Long): ArchiveThreadEntity {
        val old = dao.thread(id.value)
        return ArchiveThreadEntity(
            threadId = id.value, title = page.title, forumId = page.forum?.id, forumTitle = page.forum?.title,
            groupId = page.forum?.groupId, pages = page.partPages.joinToString("\n") { "${it.path}|${it.label.orEmpty()}" },
            fetchedAt = now, searchedDay = old?.searchedDay ?: 0, searchedToday = old?.searchedToday ?: 0,
        )
    }

    private fun pageRow(id: ThreadId, page: ArchivePage, now: Long): ArchivePageEntity {
        val range = rangeOf(page)
        return ArchivePageEntity(id.value, page.partPages[page.pageInPart - 1].path, range.first, range.last, fetchedAt = now, accessedAt = now)
    }

    private fun indexRows(id: ThreadId, page: ArchivePage, path: String) =
        page.posts.map { ArchiveIndexEntity(id.value, it.replyId, path, it.number, it.author.id, it.author.name, it.userGroup, it.repOf, it.isOp) }

    private suspend fun store(id: ThreadId, page: ArchivePage) {
        val now = clock.millis()
        val row = pageRow(id, page, now)
        dao.store(threadRow(id, page, now), row, page.posts.map { it.toEntity(id, row.path) }, indexRows(id, page, row.path), keep = KEEP_PAGES)
    }

    private suspend fun storeScanned(id: ThreadId, page: ArchivePage, kept: List<Post>) {
        val now = clock.millis()
        val row = pageRow(id, page, now)
        dao.storeScanned(threadRow(id, page, now), row, kept.map { it.toEntity(id, row.path) }, indexRows(id, page, row.path), keep = KEEP_PAGES)
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
        /** New posts added from the thread's last page rather than refetching the archive page: one thread page. */
        const val APPEND_MAX = POSTS_PER_PAGE
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
    fun matches(userId: Long, isOp: Boolean, role: PostRole?): Boolean

    data class User(val userId: Long) : PostFilter {
        override fun matches(userId: Long, isOp: Boolean, role: PostRole?) = userId == this.userId
    }

    data object Op : PostFilter {
        override fun matches(userId: Long, isOp: Boolean, role: PostRole?) = isOp
    }

    data object Moderators : PostFilter {
        override fun matches(userId: Long, isOp: Boolean, role: PostRole?) = role == PostRole.MODERATOR
    }

    data object Reps : PostFilter {
        override fun matches(userId: Long, isOp: Boolean, role: PostRole?) = role == PostRole.REP
    }
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
