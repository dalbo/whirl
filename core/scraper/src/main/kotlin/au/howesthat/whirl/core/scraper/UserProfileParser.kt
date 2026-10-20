package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.UserProfile
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError

/** `/user/<id>[?days=N]`: recent activity (a thread table) and the "About this user" key/value table. */
object UserProfileParser {

    private val NAME_AND_ID = Regex("""^(.*)\s+\(#(\d+)\)$""")

    fun parse(html: String): UserProfile {
        val doc = parseDocument(html)
        doc.requireSignedIn()
        val profile = doc.selectFirst("#userprofile") ?: throw WhirlpoolError.Drift("No #userprofile on '${doc.title()}'")
        val heading = doc.breadcrumb().lastOrNull()?.first ?: doc.title().substringBefore(" - ")
        val m = NAME_AND_ID.matchEntire(heading.squash())
            ?: throw WhirlpoolError.Drift("Cannot read user name and id from '$heading'")

        val aboutTable = profile.select("h2").firstOrNull { it.text().contains("About this user", ignoreCase = true) }
            ?.let { h2 -> generateSequence(h2.nextElementSibling()) { it.nextElementSibling() }.firstOrNull { it.tagName() == "table" } }
        val about = aboutTable?.select("tr")?.mapNotNull { tr ->
            val cells = tr.select("> td")
            if (cells.size < 2) return@mapNotNull null
            val label = cells[0].text().squash().removeSuffix(":").trim()
            label.takeIf(String::isNotEmpty)?.let { it to cells[1].text().squash() }
        }.orEmpty()

        val recent = ThreadListParser.threadTable(doc)?.let { ThreadListParser.parseRows(it).second }.orEmpty()
        return UserProfile(UserRef(m.groupValues[2].toLong(), m.groupValues[1]), about, recent)
    }
}
