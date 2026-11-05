package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.PostEntity
import au.howesthat.whirl.core.database.ThreadPageDao
import au.howesthat.whirl.core.database.ThreadPageEntity
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadPage
import au.howesthat.whirl.core.model.ThreadPart
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.WhirlpoolWeb
import au.howesthat.whirl.core.scraper.ThreadPageParser
import au.howesthat.whirl.core.scraper.WhirlpoolTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Whirlpool shows 20 posts per thread page. */
const val POSTS_PER_PAGE = 20

/** The thread page holding post [number] (1-based). */
fun pageOfPost(number: Int): Int = (number - 1) / POSTS_PER_PAGE + 1

/**
 * Thread pages, scraped from `/thread/<id>?p=N` and cached in Room (the last [KEEP_PAGES] pages opened).
 * Screens observe [page] and call [load]; the cache renders at once and is refetched when older than
 * [MaxAge.THREAD_PAGE].
 */
@Singleton
class ThreadPageRepository @Inject constructor(
    private val web: WhirlpoolWeb,
    private val dao: ThreadPageDao,
    private val forums: ForumRepository,
    private val clock: Clock,
    @DefaultDispatcher private val parsing: CoroutineDispatcher,
) {
    // Pages carry their own fetched_at, so no shared fetch log.
    private val refresher = Refresher(log = null, clock = clock)

    /**
     * The cached page, or null until it has been loaded once. Emits again only when the page is stored again: Room
     * re-runs the query on every write to `thread_pages` (opening any page touches it, storing one evicts others), and
     * the screen would re-read and re-parse every page on screen each time.
     */
    fun page(id: ThreadId, page: Int): Flow<Cached<ThreadPage>?> =
        dao.observePage(id.value, page)
            // Only the access time changes when a page is touched.
            .distinctUntilChangedBy { it?.copy(accessedAt = 0) }
            // The page row is rewritten in the same transaction as its posts, so reading the posts when it changes
            // never sees a half-stored page (combining two flows did, briefly showing an empty page).
            .map { meta ->
                meta?.let { Cached(it.toModel(dao.posts(id.value, page)), Instant.ofEpochMilli(it.fetchedAt)) }
            }

    /** The thread's page count as last seen, to show its last page from the cache before `?p=-1` returns. */
    suspend fun lastKnownPage(id: ThreadId): Int? = dao.lastKnownPageCount(id.value)

    /**
     * Makes [page] (or [LAST_PAGE]) of the thread available through [page] and returns the page number it resolved to.
     * Fetches unless the cached copy is fresh. A page that needs a web login throws [WhirlpoolError.NotSignedIn] and,
     * when [forumId] is known, marks that forum private; an unreadable page throws [WhirlpoolError.Drift].
     */
    suspend fun load(
        id: ThreadId,
        page: Int,
        forumId: Int? = null,
        force: Boolean = false,
    ): Int {
        require(page >= 1 || page == LAST_PAGE) { "page must be >= 1 or LAST_PAGE" }
        var loaded = page
        refresher.refresh("thread:${id.value}:$page", MaxAge.THREAD_PAGE, force, lastFetched = {
            // For the last page, a fresh cached copy of the last known page will do.
            val known = if (page == LAST_PAGE) dao.lastKnownPageCount(id.value) else page
            known?.let { loaded = it }
            known?.let { dao.fetchedAt(id.value, it) }
        }) {
            val parsed = fetch(id, page, forumId)
            store(id, parsed)
            loaded = parsed.page
        }
        if (loaded != LAST_PAGE) dao.touch(id.value, loaded, clock.millis())
        return loaded
    }

    /**
     * Finds the page holding reply [replyId] through the site's reply locator (`/forum/?action=replies&r=`, which
     * redirects to the thread page), caches that page and returns where the post is.
     */
    suspend fun locate(replyId: Long): Located {
        val html = web.get(web.urls.locateReply(replyId))
        val page = parseOrDrift(parsing) { ThreadPageParser.parse(html) }
        store(page.threadId, page)
        val post = page.posts.firstOrNull { it.replyId == replyId }
            ?: throw WhirlpoolError.Drift("Reply $replyId is not on the page it redirected to")
        return Located(page.threadId, page.page, post.number)
    }

    /** Where a post lives. */
    data class Located(val threadId: ThreadId, val page: Int, val postNumber: Int)

    /** Follows a whrl.pl short link and classifies where it ends. */
    suspend fun resolveShortLink(url: String): WhirlpoolLink {
        val target = web.resolve(url.toHttpUrl())
        return WhirlpoolLink.parse(target.toString())
    }

    private suspend fun fetch(id: ThreadId, page: Int, forumId: Int?): ThreadPage {
        val html = web.get(web.urls.thread(id, page = page))
        return try {
            parseOrDrift(parsing) { ThreadPageParser.parse(html) }
        } catch (e: WhirlpoolError.NotSignedIn) {
            forumId?.let { forums.markPrivate(it) }
            throw e
        }
    }

    private suspend fun store(id: ThreadId, page: ThreadPage) {
        val now = clock.millis()
        val meta = ThreadPageEntity(
            threadId = id.value,
            page = page.page,
            pageCount = page.pageCount,
            title = page.title,
            forumId = page.forum?.id,
            forumTitle = page.forum?.title,
            groupId = page.forum?.groupId,
            moderatorNoteHtml = page.moderatorNoteHtml,
            parts = page.parts.joinToString(";") { "${it.number}:${it.threadId.value}:${if (it.isCurrent) 1 else 0}" },
            fetchedAt = now,
            accessedAt = now,
        )
        dao.store(meta, page.posts.map { it.toEntity(id, page.page) }, keep = KEEP_PAGES)
    }

    companion object {
        /** `?p=-1`: the server resolves it to the real last page. */
        const val LAST_PAGE = -1
        const val KEEP_PAGES = 50
    }
}

private fun Post.toEntity(id: ThreadId, page: Int) = PostEntity(
    threadId = id.value, page = page, replyId = replyId, replyNum = number,
    userId = author.id, userName = author.name, userGroup = userGroup, quip = quip, postCount = postCount,
    postedText = postedText, postedAt = postedAt?.atZone(WhirlpoolTime.SYDNEY)?.toInstant()?.toEpochMilli(),
    editedText = editedText, isOp = isOp, hiddenReason = hiddenReason, isDeleted = isDeleted, shortUrl = shortUrl,
    bodyHtml = bodyHtml,
)

private fun ThreadPageEntity.toModel(posts: List<PostEntity>) = ThreadPage(
    threadId = ThreadId(threadId),
    title = title,
    forum = forumId?.let { ForumRef(it, forumTitle.orEmpty(), groupId) },
    page = page,
    pageCount = pageCount,
    moderatorNoteHtml = moderatorNoteHtml,
    parts = parts.split(';').filter(String::isNotEmpty).mapNotNull { part ->
        val (number, partId, current) = part.split(':').takeIf { it.size == 3 } ?: return@mapNotNull null
        ThreadPart(number.toInt(), ThreadId(partId), current == "1")
    },
    posts = posts.map {
        Post(
            replyId = it.replyId, number = it.replyNum, author = UserRef(it.userId, it.userName),
            userGroup = it.userGroup, quip = it.quip, postCount = it.postCount, postedText = it.postedText,
            postedAt = it.postedAt?.let { ms -> LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), WhirlpoolTime.SYDNEY) },
            editedText = it.editedText, isOp = it.isOp, hiddenReason = it.hiddenReason, isDeleted = it.isDeleted,
            shortUrl = it.shortUrl, bodyHtml = it.bodyHtml,
        )
    },
)
