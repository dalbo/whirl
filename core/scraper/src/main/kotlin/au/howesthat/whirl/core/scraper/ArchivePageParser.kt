package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.ArchivePage
import au.howesthat.whirl.core.model.ArchivePageRef
import au.howesthat.whirl.core.scraper.ThreadPageParser.posts
import au.howesthat.whirl.core.scraper.ThreadPageParser.threadId
import au.howesthat.whirl.core.scraper.ThreadPageParser.threadTitle
import org.jsoup.nodes.Document

/**
 * Archive pages (`/archive/<id>`, `/archive/<slug>-N`): up to 800 posts each, posts marked up as on thread pages.
 *
 * The page list is `#top_pagination li a` (the current page's `li` has class `current`, each link's `title` is the date
 * range); a thread on one page has none. Moderator-hidden posts are left out, and there is no moderator note.
 */
object ArchivePageParser {

    fun parse(html: String): ArchivePage {
        val doc = parseDocument(html)
        doc.requireSignedIn()
        val threadId = doc.threadId()
        val posts = doc.posts(threadId)
        val listed = doc.pageList()
        val (pages, current) = if (listed.isEmpty()) {
            listOf(ArchivePageRef(doc.canonicalPath() ?: "/archive/${threadId.value}", null)) to 0
        } else {
            listed.map { it.first } to listed.indexOfFirst { it.second }.coerceAtLeast(0)
        }
        val title = doc.threadTitle().replace(PAGE_SUFFIX, "")
        return ArchivePage(threadId, title, doc.breadcrumbForum(), pages, current, posts)
    }

    /** Later archive pages add " (page 2)" to the thread's title. */
    private val PAGE_SUFFIX = Regex("""\s*\(page \d+\)$""")

    /** `(page, isCurrent)` in order. */
    private fun Document.pageList(): List<Pair<ArchivePageRef, Boolean>> =
        select("ul#top_pagination li").mapNotNull { li ->
            val a = li.selectFirst("a[href]") ?: return@mapNotNull null
            val path = a.attr("href").substringAfter(FORUMS_BASE).takeIf { it.startsWith("/archive/") } ?: return@mapNotNull null
            ArchivePageRef(path, a.attr("title").squash().takeIf(String::isNotEmpty)) to li.hasClass("current")
        }

    private fun Document.canonicalPath(): String? =
        selectFirst("link[rel=canonical]")?.attr("href")?.substringAfter(FORUMS_BASE)?.takeIf { it.startsWith("/archive/") }
}
