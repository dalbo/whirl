package au.howesthat.whirl.core.network

import au.howesthat.whirl.core.model.ApiUser
import au.howesthat.whirl.core.model.Forum
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class WhirlpoolApiTest {

    private val server = MockWebServer()
    private val key = "1234-5678-9012-34"
    private lateinit var api: WhirlpoolApi

    private fun fixture(name: String) =
        File(System.getProperty("whirl.fixtures"), "api/$name").readText()

    private fun enqueue(name: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(fixture(name)).build())

    @BeforeEach
    fun setUp() {
        server.start()
        api = WhirlpoolApi(
            client = WhirlpoolHttp.client("Whirl/test"),
            apiKey = { key },
            baseUrl = server.url("/api/"),
            retry = RetryPolicy(maxRetries = 2, baseDelay = 10.milliseconds),
        )
    }

    @AfterEach
    fun tearDown() = server.close()

    @Test
    fun `user call sends key, json output, get and user agent`() = runTest {
        enqueue("api_user.json")
        assertEquals(ApiUser(216903, "d@llin"), api.user())
        val request = server.takeRequest()
        val url = request.url
        assertEquals(key, url.queryParameter("key"))
        assertEquals("json", url.queryParameter("output"))
        assertEquals("user", url.queryParameter("get"))
        assertEquals("Whirl/test", request.headers["User-Agent"])
    }

    @Test
    fun `bad key is a 403 with an error envelope`() = runTest {
        enqueue("api_badkey.json", code = 403)
        val error = assertFailsWith<WhirlpoolError.BadKey> { api.user() }
        assertEquals("Not authenticated. Bad API key?", error.message)
        assertFalse(error.toString().contains(key), "errors must never carry the key")
    }

    @Test
    fun `missing key fails before any request`() = runTest {
        val noKey = WhirlpoolApi(WhirlpoolHttp.client("t"), { null }, server.url("/api/"))
        assertFailsWith<WhirlpoolError.BadKey> { noKey.forums() }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun forums() = runTest {
        enqueue("api_forum.json")
        val forums = api.forums()
        assertEquals(54, forums.size)
        assertEquals(8, forums.map { it.section }.toSet().size)
        assertTrue(Forum(92, "Choosing an ISP", "ISPs") in forums)
        assertTrue(forums.map { it.id }.containsAll(listOf(1, 35, 85, 134)))
    }

    @Test
    fun `news titles are not escaped and dates keep their offset`() = runTest {
        enqueue("api_news.json")
        val news = api.news()
        assertEquals(30, news.size)
        assertEquals(121304, news.first().id)
        assertEquals(OffsetDateTime.parse("2026-10-02T12:56:05+10:00"), news.first().date)
        assertTrue(news.any { it.title == "Optus & Nokia hit 3.5Gbps in 6G trial in Australia" })
    }

    @Test
    fun `threads for a forum, unescaped, with boolean unread`() = runTest {
        enqueue("api_threads_forum92.json")
        val threads = api.threads(forumIds = listOf(92), count = 100)
        val url = server.takeRequest().url
        assertEquals("92", url.queryParameter("forumids"))
        assertEquals("100", url.queryParameter("threadcount"))
        assertEquals(15, threads.size)
        val t = threads.single { it.id == ThreadId("3rvvvlwj") }
        assertEquals("nbntracker.au – NBN price comparison & matchmaker", t.title)
        assertEquals(UserRef(535231, "kzrl"), t.lastPoster)
        // The HTML list showed this post as "Yesterday at 9:39 pm" (AEDT).
        assertEquals(OffsetDateTime.parse("2026-10-19T20:39:50+10:00"), t.lastDate)
        assertNull(t.group)
        assertFalse(t.isUnread)
    }

    @Test
    fun `site-wide latest threads`() = runTest {
        enqueue("api_threads_default.json")
        val threads = api.threads()
        assertNull(server.takeRequest().url.queryParameter("forumids"))
        assertEquals(30, threads.size)
        assertEquals("Cricket", threads.first().group)

        enqueue("api_threads_count100.json")
        val hundred = api.threads(count = 100)
        assertEquals(100, hundred.size)
        assertTrue(hundred.any { it.title == "external 5.25\" fdd via usb?" })
        assertTrue(hundred.none { "&amp;" in it.title || "&quot;" in it.title })
    }

    @Test
    fun `watched unread only`() = runTest {
        enqueue("api_watched_mode0.json")
        val watched = api.watched(unreadOnly = true)
        assertEquals("0", server.takeRequest().url.queryParameter("watchedmode"))
        assertEquals(2, watched.size)
        val bike = watched.first()
        assertEquals(ThreadId("9yvx1663"), bike.id)
        assertEquals(683, bike.unreadCount)
        assertEquals(51737, bike.lastReadPost)
        assertEquals(2587, bike.lastPage)
        assertEquals((bike.lastReadPost + 19) / 20, bike.lastPage, "LASTPAGE is the page holding LASTREAD")
    }

    @Test
    fun `watched all`() = runTest {
        enqueue("api_watched_mode1.json")
        assertEquals(19, api.watched(unreadOnly = false).size)
        assertEquals("1", server.takeRequest().url.queryParameter("watchedmode"))
    }

    @Test
    fun `recent is empty for this account`() = runTest {
        enqueue("api_recent_14d.json")
        assertEquals(emptyList(), api.recent(days = 14))
        assertEquals("14", server.takeRequest().url.queryParameter("recentdays"))
    }

    @Test
    fun `a bare envelope for a requested key is drift (how removed whims look)`() = runTest {
        enqueue("api_whims.json")
        assertFailsWith<WhirlpoolError.Drift> { api.watched(unreadOnly = true) }
    }

    @Test
    fun `commands send comma-separated ids and no get`() = runTest {
        enqueue("api_cmd_watchedread.json")
        api.markRead(listOf(ThreadId("9yvx1663"), ThreadId("9q684xp3")))
        val url = server.takeRequest().url
        assertEquals("9yvx1663,9q684xp3", url.queryParameter("watchedread"))
        assertNull(url.queryParameter("get"))
    }

    @Test
    fun `each command answers with the bare envelope`() = runTest {
        enqueue("api_cmd_watchedadd.json")
        enqueue("api_cmd_watchedread.json")
        enqueue("api_cmd_watchedremove.json")
        val id = listOf(ThreadId("3pxj858k"))
        api.watch(id)
        api.markRead(id)
        api.unwatch(id)
        assertEquals("3pxj858k", server.takeRequest().url.queryParameter("watchedadd"))
        assertEquals("3pxj858k", server.takeRequest().url.queryParameter("watchedread"))
        assertEquals("3pxj858k", server.takeRequest().url.queryParameter("watchedremove"))
    }

    @Test
    fun `a newly watched thread comes back read to the end`() = runTest {
        enqueue("api_cmd_after_add.json")
        val added = api.watched(unreadOnly = false).single { it.id == ThreadId("3pxj858k") }
        assertEquals(0, added.unreadCount)
        assertEquals(added.replies + 1, added.lastReadPost)
        assertEquals(13, added.lastPage)
    }

    @Test
    fun `an unknown thread id is an HTML 500 from the API`() = runTest {
        // Captured with watchedadd=zzzzzzzz (and watchedremove, identical): HTML "Something broke!" sent as application/json.
        repeat(3) { enqueue("api_cmd_bogus_id_500.html", code = 500) }
        val error = assertFailsWith<WhirlpoolError.Maintenance> { api.watch(listOf(ThreadId("zzzzzzzz"))) }
        assertEquals(500, error.httpCode)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `alert text is published`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"ALERT":"Maintenance at 2am","AUTH":{"NAME":"d@llin","ID":216903},"DOCS":"","ERROR":"","USER":{"ID":216903,"NAME":"d@llin"}}""").build())
        api.user()
        assertEquals("Maintenance at 2am", api.alert.value)
    }

    @Test
    fun `api error text is shown verbatim`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"ALERT":"","AUTH":{"NAME":"d@llin","ID":216903},"DOCS":"","ERROR":"Too many threads requested"}""").build())
        assertEquals("Too many threads requested", assertFailsWith<WhirlpoolError.Api> { api.forums() }.message)
    }

    @Test
    fun `cloudflare challenge is detected and not retried`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").body("<html><title>Just a moment...</title></html>").build())
        assertFailsWith<WhirlpoolError.Challenge> { api.forums() }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `challenge page without the header is detected too`() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("<!DOCTYPE html><html><head><title>Just a moment...</title>").build())
        assertFailsWith<WhirlpoolError.Challenge> { api.forums() }
    }

    @Test
    fun `server errors are retried with backoff`() = runTest {
        server.enqueue(MockResponse.Builder().code(503).build())
        enqueue("api_forum.json")
        assertEquals(54, api.forums().size)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `rate limiting gives up after the retry budget`() = runTest {
        repeat(3) { server.enqueue(MockResponse.Builder().code(509).addHeader("Retry-After", "0").build()) }
        assertFailsWith<WhirlpoolError.RateLimited> { api.forums() }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `garbage is drift`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html>not json</html>").build())
        assertFailsWith<WhirlpoolError.Drift> { api.forums() }
    }

    @Test
    fun `connection failure is a network error`() = runTest {
        val url = server.url("/api/")
        server.close()
        val offline = WhirlpoolApi(WhirlpoolHttp.client("t"), { key }, url, RetryPolicy.NONE)
        val error = assertFailsWith<WhirlpoolError.Network> { offline.forums() }
        assertFalse(error.toString().contains(key))
    }

    @Test
    fun `api date format`() {
        assertEquals(OffsetDateTime.parse("2026-10-19T20:39:50+10:00"), WhirlpoolApi.parseApiDate("2026-10-19T20:39:50+1000"))
        assertFailsWith<WhirlpoolError.Drift> { WhirlpoolApi.parseApiDate("2026-10-19 20:39") }
    }
}
