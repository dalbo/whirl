package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.WhirlpoolError
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Every raw fixture goes through the parser for its page type, so a newly captured page cannot be left untested. */
class AllFixturesTest {

    @TestFactory
    fun `every raw fixture parses`(): List<DynamicTest> = Fixtures.allRawHtml().map { file ->
        DynamicTest.dynamicTest(file.name) {
            val html = file.readText()
            val name = file.name
            when {
                "Not signed in" in html.substringBefore("</title>") -> assertFailsWith<WhirlpoolError.NotSignedIn> {
                    if (name.startsWith("thread_")) ThreadPageParser.parse(html) else ThreadListParser.parse(html)
                }
                name.startsWith("thread_") -> assertTrue(ThreadPageParser.parse(html).posts.isNotEmpty())
                name.startsWith("forum_index") -> assertTrue(ForumIndexParser.parse(html).sections.isNotEmpty())
                name.startsWith("forum") || name == "popular.html" || name.endsWith("_listview.html") ->
                    assertTrue(ThreadListParser.parse(html).rows.isNotEmpty())
                name.startsWith("search_") -> SearchResultsParser.parse(html)
                name.startsWith("user_") -> UserProfileParser.parse(html)
                else -> error("No parser mapped for fixture $name; add one here")
            }
        }
    }
}
