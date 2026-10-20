package au.howesthat.whirl.feature.threadlist

import au.howesthat.whirl.core.data.ScrapedList
import au.howesthat.whirl.core.network.WhirlpoolUrls
import org.junit.Test
import kotlin.test.assertEquals

/** The URLs search produces; fetching is covered by ScrapedListRepository's tests. */
class SearchUrlTest {
    private val urls = WhirlpoolUrls()

    @Test
    fun `scoped and unscoped list view searches`() {
        assertEquals(
            "https://forums.whirlpool.net.au/forum/?action=search&q=nbn&f=7&fg=179&w=t&o=list&p=2",
            ScrapedList.Search("nbn", 7, 179, page = 2).url(urls).toString(),
        )
        assertEquals(
            "https://forums.whirlpool.net.au/forum/?action=search&q=fibre%20to%20the%20node&f=&fg=&w=t&o=list",
            ScrapedList.Search("fibre to the node").url(urls).toString(),
        )
    }

    @Test
    fun `search route defaults`() {
        // Opened from the Forums screen: no query, no scope.
        val route = SearchRoute()
        assertEquals("", route.query)
        assertEquals(null, route.forumId)
    }
}
