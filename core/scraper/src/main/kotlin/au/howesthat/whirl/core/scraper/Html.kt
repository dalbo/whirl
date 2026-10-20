package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal const val FORUMS_BASE = "https://forums.whirlpool.net.au"
internal const val SITE_BASE = "https://whirlpool.net.au"

private val THREAD_HREF = Regex("""^(?:https?://forums\.whirlpool\.net\.au)?/(?:thread|archive)/([A-Za-z0-9]+)""")
private val FORUM_HREF = Regex("""^(?:https?://forums\.whirlpool\.net\.au)?/forum/(\d+)(?:\?(?:.*&)?g=(\d+))?""")
private val USER_HREF = Regex("""^(?:https?://forums\.whirlpool\.net\.au)?/user/(\d+)""")

internal fun parseDocument(html: String): Document = Jsoup.parse(html, "$FORUMS_BASE/")

/** Throws [WhirlpoolError.NotSignedIn] for the page Whirlpool serves for private content without a web login. */
internal fun Document.requireSignedIn() {
    if (title().trim().equals("Not signed in", ignoreCase = true)) throw WhirlpoolError.NotSignedIn()
}

internal fun threadIdFromHref(href: String?): ThreadId? =
    href?.let { THREAD_HREF.find(it.trim()) }?.groupValues?.get(1)?.let(::ThreadId)

/** `/forum/7?g=179` → (7, 179). The `?&g=` form the site emits is accepted too. */
internal fun forumFromHref(href: String?, title: String): ForumRef? =
    href?.let { FORUM_HREF.find(it.trim()) }?.let { m ->
        ForumRef(m.groupValues[1].toInt(), title, m.groupValues[2].takeIf(String::isNotEmpty)?.toInt())
    }

internal fun userFromLink(a: Element?): UserRef? {
    a ?: return null
    val id = USER_HREF.find(a.attr("href"))?.groupValues?.get(1)?.toLong() ?: return null
    return UserRef(id, a.text().trim())
}

/** "8,883", " 8" → 8883, 8. */
internal fun parseCount(text: String?): Int? = text?.filter(Char::isDigit)?.takeIf(String::isNotEmpty)?.toIntOrNull()

/** Text with whitespace collapsed and `&nbsp;` treated as a space. */
internal fun String.squash(): String = replace(' ', ' ').replace(Regex("\\s+"), " ").trim()

/** Breadcrumb entries `(name, href)` from the schema.org list; the href comes from `a` or `link[itemprop=item]`. */
internal fun Document.breadcrumb(): List<Pair<String, String>> =
    select("ul#breadcrumb > li").map { li ->
        val name = li.selectFirst("[itemprop=name]")?.text()?.squash() ?: li.text().squash()
        val href = li.selectFirst("[itemprop=item]")?.let { it.attr("href") }.orEmpty()
        name to href
    }

/** The forum (and group) a page belongs to, from the breadcrumb. */
internal fun Document.breadcrumbForum(): ForumRef? {
    val crumbs = breadcrumb()
    val forum = crumbs.firstNotNullOfOrNull { (name, href) -> forumFromHref(href, name)?.takeIf { it.groupId == null } }
        ?: return null
    val group = crumbs.firstNotNullOfOrNull { (name, href) -> forumFromHref(href, name)?.takeIf { it.groupId != null } }
    return forum.copy(groupId = group?.groupId)
}

/** Make a link from a post absolute: `//host/x` → https, `/wiki/` → the main site, other `/x` → the forums host. */
internal fun absoluteUrl(href: String): String = when {
    href.startsWith("//") -> "https:$href"
    href.startsWith("/wiki/") || href == "/wiki" -> SITE_BASE + href
    href.startsWith("/") -> FORUMS_BASE + href
    else -> href
}
