package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.ForumIndex
import au.howesthat.whirl.core.model.ForumIndexEntry
import au.howesthat.whirl.core.model.ForumIndexSection
import au.howesthat.whirl.core.model.LatestThread
import au.howesthat.whirl.core.model.WhirlpoolError

/** The public forum index (`/`): sections of forums, each with its three most recently active threads. */
object ForumIndexParser {

    fun parse(html: String): ForumIndex {
        val doc = parseDocument(html)
        doc.requireSignedIn()
        val sections = doc.select("#forumindex h3").map { h3 ->
            val table = generateSequence(h3.nextElementSibling()) { it.nextElementSibling() }
                .takeWhile { it.tagName() != "h3" }
                .firstOrNull { it.tagName() == "table" }
            val forums = table?.select("tbody > tr")?.mapNotNull { tr ->
                val a = tr.selectFirst("td.title div.title a[href]") ?: return@mapNotNull null
                val forum = forumFromHref(a.attr("href"), a.text().squash()) ?: return@mapNotNull null
                val latest = tr.select("td.threads > div").mapNotNull { div ->
                    val link = div.selectFirst("a[href]") ?: return@mapNotNull null
                    val id = threadIdFromHref(link.attr("href")) ?: return@mapNotNull null
                    LatestThread(id, link.text().squash(), div.ownText().squash().removeSurrounding("(", ")").takeIf(String::isNotEmpty))
                }
                ForumIndexEntry(forum, latest)
            }.orEmpty()
            ForumIndexSection(h3.text().squash(), forums)
        }
        if (sections.sumOf { it.forums.size } == 0) throw WhirlpoolError.Drift("Forum index has no forums")
        return ForumIndex(sections)
    }
}
