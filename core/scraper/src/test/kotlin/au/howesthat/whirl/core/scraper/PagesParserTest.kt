package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SearchResultsParserTest {

    @Test
    fun `relevance view`() {
        val results = SearchResultsParser.parse(Fixtures.html("search_nbn.html"))
        assertEquals(50, results.size)
        assertEquals((1..50).toList(), results.map { it.rank })
        val first = results.first()
        assertEquals(ThreadId("9xvvym14"), first.threadId)
        assertEquals("NBN Novice", first.title)
        assertEquals(ForumRef(92, "Choosing an ISP"), first.forum)
        assertEquals("3 months ago", first.firstPostAge)
        assertEquals("3 days ago", first.lastPostAge)
        assertTrue(first.snippet.startsWith("A friend is moving to an Apartment"))
        assertTrue(results.all { it.forum != null })
        // Two results (32, 36) have no snippet at all.
        assertEquals(48, results.count { it.snippet.isNotEmpty() })

        val grouped = results.single { it.rank == 36 }
        assertEquals(ForumRef(102, "Vocus Group", groupId = 65), grouped.forum)
        assertEquals("a month ago", grouped.firstPostAge)
        assertEquals("3 weeks ago", grouped.lastPostAge)
        assertEquals("", grouped.snippet)
    }

    @Test
    fun `second page and forum-scoped searches`() {
        assertEquals(50, SearchResultsParser.parse(Fixtures.html("search_nbn_p2.html")).size)
        val scoped = SearchResultsParser.parse(Fixtures.html("search_forum92_title_only.html"))
        assertEquals(50, scoped.size)
        assertTrue(scoped.all { it.forum?.id == 92 }, "f=92 limits results to forum 92")
    }

    @Test
    fun `no results is an empty list, not drift`() {
        assertEquals(emptyList(), SearchResultsParser.parse(Fixtures.html("search_noresults.html")))
    }

    @Test
    fun `a thread page is drift`() {
        assertFailsWith<WhirlpoolError.Drift> { SearchResultsParser.parse(Fixtures.html("thread_3pxj858k_p2.html")) }
    }
}

class ForumIndexParserTest {

    @Test
    fun `fifty public forums with their latest threads`() {
        val index = ForumIndexParser.parse(Fixtures.html("forum_index.html"))
        assertEquals(7, index.sections.size)
        assertEquals("Computers", index.sections.first().title)
        val forums = index.sections.flatMap { it.forums }
        assertEquals(50, forums.size)
        assertEquals(50, forums.map { it.forum.id }.toSet().size)
        assertTrue(forums.none { it.forum.id in setOf(1, 35, 85, 134) }, "private forums are not on the index")

        val desktops = forums.first()
        assertEquals(ForumRef(7, "Desktops"), desktops.forum)
        assertEquals(3, desktops.latest.size)
        assertEquals(ThreadId("97r5wzn3"), desktops.latest[0].id)
        assertEquals("Geforce RTX Discussion", desktops.latest[0].title)
        assertEquals("20 mins ago", desktops.latest[0].ago)
        assertEquals(null, desktops.latest[1].ago)
    }
}

class UserProfileParserTest {

    @Test
    fun `profile with no recent activity`() {
        val profile = UserProfileParser.parse(Fixtures.html("user_30703.html"))
        assertEquals(UserRef(30703, "Tiedan"), profile.user)
        assertEquals(
            listOf(
                "Status" to "Whirlpool Enthusiast",
                "Network hardware" to "Linksys · EA4500",
                "City" to "Victoria",
                "Joined" to "Saturday, 16 August 2003",
                "Post count" to "903",
            ),
            profile.about,
        )
        assertEquals(emptyList(), profile.recentActivity)
    }
}
