package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadPage
import au.howesthat.whirl.core.model.ThreadPart
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Thread pages (`/thread/<id>?p=N`, `?p=-1` for the last page) and whole-thread archive pages (`/archive/<id>`).
 *
 * Page numbers come from the inline script (`var thread_page = 2; var thread_pages = 13;`), which the server renders;
 * the visible pagination is built by JavaScript and absent from the raw HTML.
 */
object ThreadPageParser {

    private fun Document.scripts(): String = select("script:not([src])").joinToString("\n") { it.data() }

    fun parse(html: String): ThreadPage {
        val doc = parseDocument(html)
        doc.requireSignedIn()
        val scripts = doc.scripts()
        fun Regex.int() = find(scripts)?.groupValues?.get(1)?.toInt()

        val crumbs = doc.breadcrumb()
        val threadId = doc.threadId(scripts)
        val posts = doc.posts(threadId)

        val page = THREAD_PAGE_VAR.int() ?: PAGES_CURRENT.int() ?: 1
        val pageCount = THREAD_PAGES_VAR.int() ?: PAGES_TOTAL.int() ?: 1

        return ThreadPage(
            threadId = threadId,
            title = doc.threadTitle(crumbs),
            forum = doc.breadcrumbForum(),
            page = page,
            pageCount = maxOf(pageCount, page),
            moderatorNoteHtml = doc.selectFirst("#top_notebar_public")?.html()?.trim()?.takeIf(String::isNotEmpty),
            parts = parts(doc),
            posts = posts,
        )
    }

    internal fun Document.threadId(scripts: String = scripts()): ThreadId =
        breadcrumb().lastOrNull()?.second?.let(::threadIdFromHref)
            ?: THREAD_ID_VAR.find(scripts)?.groupValues?.get(1)?.let(::ThreadId)
            ?: threadIdFromHref(selectFirst("link[rel=canonical]")?.attr("href")?.substringAfter(FORUMS_BASE))
            ?: throw WhirlpoolError.Drift("Cannot find the thread id on '${title()}'")

    internal fun Document.threadTitle(crumbs: List<Pair<String, String>> = breadcrumb()): String =
        crumbs.lastOrNull()?.first?.takeIf { threadIdFromHref(crumbs.last().second) != null }
            ?: title().substringBefore(" - ").squash()

    /** Every post on the page; none at all is drift, since a thread always has its first post. */
    internal fun Document.posts(threadId: ThreadId): List<Post> =
        select("div.replyblock[data-replyid]").map(::parsePost)
            .ifEmpty { throw WhirlpoolError.Drift("No posts found in thread ${threadId.value}") }

    private fun parts(doc: Document): List<ThreadPart> =
        doc.selectFirst("div.notebar.parts")?.select("a[href]")?.mapNotNull { a ->
            val id = threadIdFromHref(a.attr("href")) ?: return@mapNotNull null
            ThreadPart(parseCount(a.text()) ?: return@mapNotNull null, id, a.hasClass("current"))
        }.orEmpty()

    private val HIDDEN_REASON = Regex("""\(([^)]*)\)""")
    private val SHORT_URL = Regex("""https?://whrl\.pl/\w+""")
    private val THREAD_ID_VAR = Regex("""\bvar\s+thread_id\s*=\s*"([A-Za-z0-9]+)"""")
    private val THREAD_PAGE_VAR = intVar("thread_page")
    private val THREAD_PAGES_VAR = intVar("thread_pages")
    private val PAGES_CURRENT = pagesField("current")
    private val PAGES_TOTAL = pagesField("total")

    /** `var thread_page = 2;` */
    private fun intVar(name: String) = Regex("""\bvar\s+$name\s*=\s*(\d+)\s*;""")

    /** `var pages = { current: 2, total: 13 }`, the form some pages use instead. */
    private fun pagesField(name: String) = Regex("""\bvar\s+pages\s*=\s*\{[^}]*\b$name\s*:\s*(\d+)""")

    private fun parsePost(block: Element): Post {
        val replyId = block.attr("data-replyid").toLong()
        val author = UserRef(
            id = block.attr("data-userid").toLong(),
            name = block.attr("data-uname").ifEmpty { block.selectFirst(".bu_name")?.text().orEmpty() }.squash(),
        )
        val number = block.attr("data-replynum").toInt()

        // A post hidden by a moderator is a bare notice: user, time, and "This post was hidden by a moderator (reason)".
        block.selectFirst(".hiddenpostnotice")?.let { notice ->
            val postedText = notice.selectFirst(".replytools")?.text()?.squash()?.takeIf(String::isNotEmpty)
            val noticeText = notice.selectFirst(".replytext")?.text()?.squash().orEmpty()
            return Post(
                replyId = replyId, number = number, author = author,
                userGroup = null, quip = null, postCount = null,
                postedText = postedText, postedAt = postedText?.let(WhirlpoolTime::parseAbsolute), editedText = null,
                isOp = false,
                hiddenReason = HIDDEN_REASON.find(noticeText)?.groupValues?.get(1)?.trim() ?: noticeText.ifEmpty { "hidden" },
                isDeleted = false, shortUrl = null, bodyHtml = "",
            )
        }

        // Desktop and mobile tool bars both carry .date and .shortcode; prefer the desktop one.
        val date = block.selectFirst(".replytools-inner .date") ?: block.selectFirst(".date")
        val edited = date?.selectFirst(".edited")?.text()?.squash()?.removePrefix("edited")?.trim()
        // "posted" is a link on thread pages and plain text on archive pages, where the date sits in a span.
        val postedText = date?.clone()?.apply { select(".edited").remove() }?.text()?.squash()
            ?.removePrefix("posted")?.trim()?.takeIf(String::isNotEmpty)
        val body = block.selectFirst(".replytext")
            ?: throw WhirlpoolError.Drift("Post $replyId has no body")
        val isOp = body.selectFirst("div.op") != null
        val bodyHtml = body.clone().apply { select("div.op, div.edited").remove() }.html().trim()
        val shortUrl = block.selectFirst(".shortcode a[href*=whrl.pl]")?.attr("href")
            ?: block.selectFirst(".shortcode")?.text()?.let { SHORT_URL.find(it)?.value }

        return Post(
            replyId = replyId,
            number = number,
            author = author,
            userGroup = block.selectFirst(".usergroup")?.text()?.squash()?.takeIf(String::isNotEmpty),
            quip = block.selectFirst(".quip")?.text()?.squash()?.takeIf(String::isNotEmpty),
            postCount = parseCount(block.selectFirst(".replycount")?.text()),
            postedText = postedText,
            postedAt = postedText?.let(WhirlpoolTime::parseAbsolute),
            editedText = edited?.takeIf(String::isNotEmpty),
            isOp = isOp,
            hiddenReason = null,
            // 2.14.12 treated a post without a date as deleted; no deleted post has been captured yet.
            isDeleted = date == null,
            shortUrl = shortUrl,
            bodyHtml = bodyHtml,
            repOf = block.selectFirst(".username .reptag")?.text()?.squash()?.takeIf(String::isNotEmpty),
        )
    }
}
