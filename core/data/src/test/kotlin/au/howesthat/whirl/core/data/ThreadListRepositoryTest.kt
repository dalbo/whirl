package au.howesthat.whirl.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.ForumEntity
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.network.WhirlpoolWeb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadListRepositoryTest {

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MutableClock()
    private val client = WhirlpoolHttp.client("Whirl/test")
    private val api = WhirlpoolApi(client, { "test-key" }, server.url("/api/"), RetryPolicy.NONE)
    private val web = WhirlpoolWeb(client, WhirlpoolUrls(server.url("/")), RetryPolicy.NONE)
    private val forums = ForumRepository(api, db.forumDao(), db.fetchLogDao(), clock)

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun fixture(path: String) = File(System.getProperty("whirl.fixtures"), path).readText()
    private fun enqueue(path: String) = server.enqueue(MockResponse.Builder().body(fixture(path)).build())

    @Test
    fun `scraped forum pages are cached in memory per page and group`() = runTest {
        val repo = ScrapedListRepository(web, forums, clock)
        val p1 = ScrapedList.ForumPage(92, 1)
        assertNull(repo.observe(p1).first())
        enqueue("html/forum92_p1.html")
        assertTrue(repo.refresh(p1))
        assertEquals("/forum/92", server.takeRequest().target)
        val page = assertNotNull(repo.observe(p1).first()).value
        assertEquals(50, page.rows.size)
        assertFalse(repo.refresh(p1), "fresh")

        enqueue("html/forum7_g179.html")
        repo.refresh(ScrapedList.ForumPage(7, 2, groupId = 179))
        assertEquals("/forum/7?p=2&g=179", server.takeRequest().target)
        assertTrue(repo.observe(ScrapedList.ForumPage(7, 2, 179)).first()!!.value.groups.isNotEmpty())

        clock.now = clock.now.plus(Duration.ofMinutes(11))
        enqueue("html/forum92_p1.html")
        assertTrue(repo.refresh(p1), "stale after the 10 minute max age")
    }

    @Test
    fun `popular and search list view parse as thread tables`() = runTest {
        val repo = ScrapedListRepository(web, forums, clock)
        enqueue("html/popular.html")
        repo.refresh(ScrapedList.Popular)
        assertEquals("/forum/?action=popular_views", server.takeRequest().target)
        val popular = repo.observe(ScrapedList.Popular).first()!!.value
        assertEquals(36, popular.sections.size)
        assertEquals(183, popular.rows.size)

        enqueue("html/search_nbn_listview.html")
        val search = ScrapedList.Search("nbn")
        repo.refresh(search)
        assertEquals("/forum/?action=search&q=nbn&f=&fg=&w=t&o=list", server.takeRequest().target)
        assertEquals(100, repo.observe(search).first()!!.value.rows.size)
    }

    @Test
    fun `a forum page that needs a login marks the forum private`() = runTest {
        db.forumDao().replaceAll(listOf(ForumEntity(200, "Secret", "Whirlpool", 0)))
        assertFalse(200 in forums.privateIds.first())
        val repo = ScrapedListRepository(web, forums, clock)
        enqueue("html/forum1_pm_html.html")
        assertFailsWith<WhirlpoolError.NotSignedIn> { repo.refresh(ScrapedList.ForumPage(200, 1)) }
        assertTrue(200 in forums.privateIds.first())
    }

    @Test
    fun `known private forums are private before any page is fetched`() = runTest {
        db.forumDao().replaceAll(listOf(ForumEntity(35, "Feedback", "Whirlpool", 0), ForumEntity(92, "Choosing an ISP", "ISPs", 1)))
        assertEquals(setOf(1, 35, 85, 134), forums.privateIds.first())
        assertEquals(listOf(true, false), forums.forums.first().value.map { it.isPrivate })
    }

    @Test
    fun `a forum's API list is stored per forum`() = runTest {
        val repo = ForumThreadsRepository(api, db.threadDao(), db.fetchLogDao(), clock)
        enqueue("api/api_threads_forum92.json")
        repo.refresh(92)
        val url = server.takeRequest().url
        assertEquals("92", url.queryParameter("forumids"))
        assertEquals("100", url.queryParameter("threadcount"))
        assertEquals(15, repo.threads(92).first().value.size)
        assertEquals(0, repo.threads(35).first().value.size)
        assertNotNull(repo.threads(92).first().updatedAt)
    }

    @Test
    fun `recent uses the history length and refetches when it changes`() = runTest {
        val repo = RecentRepository(api, db.threadDao(), db.fetchLogDao(), clock)
        // get=recent has only been seen empty; this assumes the THREADS shape (HANDOFF "known unknowns").
        server.enqueue(MockResponse.Builder().body(fixture("api/api_threads_forum92.json").replace("\"THREADS\"", "\"RECENT\"")).build())
        assertTrue(repo.refresh(7))
        assertEquals("7", server.takeRequest().url.queryParameter("recentdays"))
        assertEquals(15, repo.threads(7).first().value.size)
        assertFalse(repo.refresh(7))

        enqueue("api/api_recent_14d.json")
        assertTrue(repo.refresh(14), "a different history length is a different list")
        assertEquals(0, repo.threads(14).first().value.size)
        assertNull(repo.threads(7).first().updatedAt, "the 7-day list is gone")
    }

    @Test
    fun `recent without a RECENT key is drift`() = runTest {
        val repo = RecentRepository(api, db.threadDao(), db.fetchLogDao(), clock)
        enqueue("api/api_whims.json")
        assertFailsWith<WhirlpoolError.Drift> { repo.refresh(7) }
    }
}
