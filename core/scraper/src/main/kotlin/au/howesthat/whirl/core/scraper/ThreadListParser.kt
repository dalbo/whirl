package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.ForumGroup
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.RowKind
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadListPage
import au.howesthat.whirl.core.model.ThreadRow
import au.howesthat.whirl.core.model.WhirlpoolError
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Thread tables: forum pages (`/forum/92?p=2&g=G`), Popular (`/forum/?action=popular_views`), search list view
 * (`/forum/?action=search&o=list`) and the recent-activity table on user profiles. Rows are always `.read` on these
 * pages because there is no web session, so unread state never comes from here.
 */
object ThreadListParser {

    fun parse(html: String): ThreadListPage {
        val doc = parseDocument(html)
        doc.requireSignedIn()
        val table = threadTable(doc) ?: throw WhirlpoolError.Drift("No thread table on page '${doc.title()}'")
        val (sections, rows) = parseRows(table)
        val (page, pageCount) = pagination(doc)
        return ThreadListPage(
            forum = doc.breadcrumbForum(),
            page = page,
            pageCount = pageCount,
            groups = groups(doc),
            sections = sections,
            rows = rows,
        )
    }

    internal fun threadTable(doc: Document): Element? = doc.selectFirst("#threads table")

    /** Returns the section headers and the rows of a thread table, in page order. */
    internal fun parseRows(table: Element): Pair<List<ForumRef>, List<ThreadRow>> {
        val sections = mutableListOf<ForumRef>()
        val rows = mutableListOf<ThreadRow>()
        var section: ForumRef? = null
        for (tr in table.select("tbody > tr")) {
            if (tr.hasClass("section")) {
                val a = tr.selectFirst("td.title a.title")
                // "No recent activity" on a user page is a section row without a forum link.
                section = a?.let { forumFromHref(it.attr("href"), it.text().squash()) }
                section?.let(sections::add)
                continue
            }
            val id = tr.id().removePrefix("tr").takeIf { tr.id().startsWith("tr") && it.isNotEmpty() } ?: continue
            rows += parseRow(tr, ThreadId(id), section)
        }
        return sections to rows
    }

    private fun parseRow(tr: Element, id: ThreadId, section: ForumRef?): ThreadRow {
        val titleCell = tr.selectFirst("td.title")
            ?: throw WhirlpoolError.Drift("Thread row ${id.value} has no title cell")
        val titleLink = titleCell.selectFirst("a.title")
            ?: throw WhirlpoolError.Drift("Thread row ${id.value} has no title link")
        val kind = when {
            tr.hasClass("pointer") -> RowKind.MOVED
            tr.hasClass("deleted") -> RowKind.DELETED
            else -> RowKind.THREAD
        }
        val noteSpan = titleCell.children().firstOrNull { it.tagName() == "span" && !it.hasClass("pages") }
        val note = noteSpan?.text()?.squash()?.trimStart('–', '-', '«', ' ')?.takeIf(String::isNotEmpty)
        val first = tr.selectFirst("td.oldest")
        val last = tr.selectFirst("td.newest.collapse4 > span") ?: tr.selectFirst("td.newest > span")
        return ThreadRow(
            id = id,
            kind = kind,
            title = titleLink.text().squash(),
            linkId = threadIdFromHref(titleLink.attr("href")) ?: id,
            note = if (kind == RowKind.THREAD) null else note,
            redirectId = if (kind == RowKind.DELETED) threadIdFromHref(noteSpan?.selectFirst("a")?.attr("href")) else null,
            isSticky = tr.hasClass("sticky"),
            isClosed = tr.hasClass("closed"),
            pageCount = titleCell.selectFirst("span.pages[data-pages]")?.attr("data-pages")?.toIntOrNull() ?: 1,
            replies = parseCount(tr.selectFirst("td.reps")?.attr("title")),
            views = parseCount(tr.selectFirst("td.reads")?.attr("title")),
            firstPoster = userFromLink(first?.selectFirst("a[href^=/user/]")),
            firstPostedText = first?.ownText()?.squash()?.takeIf(String::isNotEmpty),
            lastPoster = userFromLink(last?.selectFirst("a[href^=/user/]")),
            lastPostedText = last?.ownText()?.squash()?.takeIf(String::isNotEmpty),
            section = section,
            group = titleCell.selectFirst("a.group")?.let { forumFromHref(it.attr("href"), it.text().squash()) },
        )
    }

    /** Current page and page count from the footer pagination and its overflow `select[name=p]`. */
    private fun pagination(doc: Document): Pair<Int, Int> {
        val numbers = doc.select("div.footbar ul.pagination li a").mapNotNull { parseCount(it.text()) } +
            doc.select("select[name=p] option").mapNotNull { it.attr("value").toIntOrNull() }
        val current = doc.selectFirst("div.footbar ul.pagination li.current a")?.let { parseCount(it.text()) }
            ?: doc.selectFirst("select[name=p] option[selected]")?.attr("value")?.toIntOrNull()
            ?: 1
        return current to maxOf(numbers.maxOrNull() ?: 1, current)
    }

    private fun groups(doc: Document): List<ForumGroup> =
        doc.select("select[name=g] option").mapNotNull { option ->
            val id = option.attr("value").toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            ForumGroup(id, option.text().squash())
        }
}
