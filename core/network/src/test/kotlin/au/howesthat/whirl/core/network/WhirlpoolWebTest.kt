package au.howesthat.whirl.core.network

import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WhirlpoolError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WhirlpoolWebTest {

    private val urls = WhirlpoolUrls()

    @Test
    fun `url forms`() {
        val id = ThreadId("3pxj858k")
        assertEquals("https://forums.whirlpool.net.au/thread/3pxj858k", urls.thread(id).toString())
        assertEquals("https://forums.whirlpool.net.au/thread/3pxj858k?p=2", urls.thread(id, 2).toString())
        assertEquals("https://forums.whirlpool.net.au/thread/3pxj858k?p=-1", urls.thread(id, -1).toString())
        assertEquals("https://forums.whirlpool.net.au/thread/3pxj858k?ux=915415", urls.thread(id, userId = 915415).toString())
        assertEquals("https://forums.whirlpool.net.au/thread/3pxj858k?um=1", urls.thread(id, moderatorsOnly = true).toString())
        assertEquals("https://forums.whirlpool.net.au/archive/1000422", urls.archive(ThreadId("1000422")).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/92", urls.forum(92).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/92?p=2", urls.forum(92, 2).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/7?g=179", urls.forum(7, group = 179).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/?action=popular_views", urls.popular().toString())
        assertEquals("https://forums.whirlpool.net.au/forum/?action=replies&r=75543951", urls.locateReply(75543951).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/?action=reply&r=75543951", urls.replyToPost(75543951).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/?action=reply&t=3pxj858k", urls.replyToThread(id).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/?action=edit&e=75543951", urls.editPost(75543951).toString())
        assertEquals("https://forums.whirlpool.net.au/forum/?action=newthread&f=92", urls.newThread(92).toString())
        assertEquals("https://forums.whirlpool.net.au/user/30703?days=7", urls.user(30703, 7).toString())
        assertEquals(
            "https://forums.whirlpool.net.au/forum/?action=search&q=nbn&f=&fg=&w=t&o=list",
            urls.searchList("nbn").toString(),
        )
        assertEquals("https://forums.whirlpool.net.au/search?q=nbn%20plan&f=92&w=tb&p=2", urls.search("nbn plan", forum = 92, page = 2).toString())
    }

    @Test
    fun `fetches a page and detects a challenge`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val html = File(System.getProperty("whirl.fixtures"), "html/forum92_p1.html").readText()
            server.enqueue(MockResponse.Builder().body(html).build())
            server.enqueue(MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build())
            server.enqueue(MockResponse.Builder().code(404).build())
            val web = WhirlpoolWeb(WhirlpoolHttp.client("Whirl/test"), retry = RetryPolicy.NONE)
            assertEquals(html, web.get(server.url("/forum/92")))
            assertFailsWith<WhirlpoolError.Challenge> { web.get(server.url("/forum/92")) }
            assertEquals(404, assertFailsWith<WhirlpoolError.Api> { web.get(server.url("/thread/nope")) }.httpCode)
        }
    }

    @Test
    fun `resolve follows redirects`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse.Builder().code(302).addHeader("Location", "/thread/3pxj858k?p=2#r21").build())
            server.enqueue(MockResponse.Builder().body("<html></html>").build())
            val web = WhirlpoolWeb(WhirlpoolHttp.client("Whirl/test"), retry = RetryPolicy.NONE)
            val end = web.resolve(server.url("/AbCd"))
            assertEquals("/thread/3pxj858k", end.encodedPath)
            assertEquals("2", end.queryParameter("p"))
        }
    }
}
