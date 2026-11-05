package au.howesthat.whirl.feature.thread

import au.howesthat.whirl.core.scraper.ThreadPageParser
import org.junit.Test
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals

class ScrollStepTest {
    // Page 2: posts 21 to 40.
    private val posts = ThreadPageParser.parse(File(System.getProperty("whirl.fixtures"), "html/thread_3pxj858k_p2.html").readText()).posts
    private val cachedAt = Instant.parse("2026-11-05T00:00:00Z")
    private val fetchedAt = cachedAt.plusSeconds(90)

    @Test
    fun `a post on the page is scrolled to and the target is done`() {
        assertEquals(ScrollStep(9, done = true), ScrollTarget(2, postNumber = 30).step(posts, cachedAt))
        assertEquals(ScrollStep(posts.lastIndex, done = true), ScrollTarget(2, lastPost = true).step(posts, cachedAt))
        assertEquals(ScrollStep(3, done = true), ScrollTarget(2, replyId = posts[3].replyId).step(posts, cachedAt))
    }

    @Test
    fun `a post missing from a link's page is given up`() {
        assertEquals(ScrollStep(null, done = true), ScrollTarget(2, postNumber = 41).step(posts, cachedAt))
    }

    @Test
    fun `the first unread post missing from the cached page waits for the fetched one`() {
        // The cached copy ends at post 30; Watched says post 31 is the first unread.
        val cached = posts.filter { it.number <= 30 }
        val waiting = ScrollTarget(2, postNumber = 31, waitForFetch = true)
        assertEquals(ScrollStep(9, done = false), waiting.step(cached, cachedAt), "the last read post meanwhile")
        // The fetch is still on its way, or has stored the page but the screen still has the older copy.
        assertEquals(ScrollStep(9, done = false), waiting.copy(fetched = fetchedAt).step(cached, cachedAt))
        // The fetched copy has it.
        assertEquals(ScrollStep(10, done = true), waiting.copy(fetched = fetchedAt).step(posts, fetchedAt))
    }

    @Test
    fun `a first unread post the fetched page lacks ends at the post before it`() {
        // A stale position (the posts were deleted, or the fetch failed and only the cache is left).
        val waiting = ScrollTarget(2, postNumber = 45, waitForFetch = true, fetched = fetchedAt)
        assertEquals(ScrollStep(posts.lastIndex, done = true), waiting.step(posts, fetchedAt))
        assertEquals(ScrollStep(null, done = true), waiting.step(emptyList(), fetchedAt))
    }
}
