package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.SearchResult
import au.howesthat.whirl.core.model.WhirlpoolError

/**
 * The relevance view of `/search?q=&f=&fg=&w=&p=N`: 50 results per page and no pagination markup, so callers step
 * `p` until a page comes back empty. The list view is a thread table; use [ThreadListParser] for it.
 */
object SearchResultsParser {

    fun parse(html: String): List<SearchResult> {
        val doc = parseDocument(html)
        doc.requireSignedIn()
        val results = doc.selectFirst("#searchresults ol.results")
        if (results == null) {
            // A search with no hits renders only the search form.
            if (doc.selectFirst("form[action=/search] input[name=q]") != null) return emptyList()
            throw WhirlpoolError.Drift("No search results or search form on '${doc.title()}'")
        }
        return results.select("> li").mapIndexed { index, li ->
            val titleLink = li.selectFirst("div.title a[href]")
                ?: throw WhirlpoolError.Drift("Search result ${index + 1} has no title link")
            val detail = li.selectFirst("div.detail")
            // "Forum [» Group] 3 months ago → 3 days ago"
            val ages = detail?.ownText()?.squash()?.trimStart('»', ' ')?.split('→')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
            val forumLinks = detail?.select("a[href]")?.mapNotNull { forumFromHref(it.attr("href"), it.text().squash()) }.orEmpty()
            val forum = forumLinks.firstOrNull { it.groupId == null }
                ?.let { f -> f.copy(groupId = forumLinks.firstOrNull { it.id == f.id && it.groupId != null }?.groupId) }
            SearchResult(
                rank = li.attr("value").toIntOrNull() ?: (index + 1),
                threadId = threadIdFromHref(titleLink.attr("href"))
                    ?: throw WhirlpoolError.Drift("Search result link '${titleLink.attr("href")}' is not a thread"),
                title = titleLink.text().squash(),
                forum = forum,
                firstPostAge = ages.getOrNull(0),
                lastPostAge = ages.getOrNull(1),
                snippet = li.selectFirst("div.snippet")?.text()?.squash().orEmpty(),
            )
        }
    }
}
