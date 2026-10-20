package au.howesthat.whirl.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.ForumEntity
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.PostFilter
import au.howesthat.whirl.core.model.ThreadId
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
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadPageRepositoryTest {

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MutableClock()
    private val client = WhirlpoolHttp.client("Whirl/test")
    private val web = WhirlpoolWeb(client, WhirlpoolUrls(server.url("/")), RetryPolicy.NONE)
    // The API is never called here; the forum repository only needs it to exist.
    private val forums = ForumRepository(WhirlpoolApi(client, { null }, server.url("/api/"), RetryPolicy.NONE), db.forumDao(), db.fetchLogDao(), clock)
    private val repo = ThreadPageRepository(web, db.threadPageDao(), forums, clock)

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun enqueueHtml(name: String) = server.enqueue(
        MockResponse.Builder().body(File(System.getProperty("whirl.fixtures"), "html/$name").readText()).build(),
    )

    private val nbn = ThreadId("3pxj858k")

    @Test
    fun `a loaded page is cached with its posts and metadata`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        assertEquals(2, repo.load(nbn, 2))
        assertEquals("/thread/3pxj858k?p=2", server.takeRequest().target)

        val cached = assertNotNull(repo.page(nbn, 2).first())
        assertEquals(clock.instant(), cached.updatedAt)
        val page = cached.value
        assertEquals(13, page.pageCount)
        assertEquals(92, page.forum?.id)
        assertEquals((21..40).toList(), page.posts.map { it.number })
        assertEquals(3, page.posts.count { it.isEdited })
        assertEquals(5, page.posts.count { it.isOp })
        // Sydney local times survive the round trip through epoch milliseconds.
        page.posts.forEach { assertNotNull(it.postedAt) }
        assertEquals(13, repo.lastKnownPage(nbn))
    }

    @Test
    fun `fresh pages are not refetched, stale and forced ones are`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        repo.load(nbn, 2)
        clock.now = clock.now.plusSeconds(30)
        repo.load(nbn, 2)
        assertEquals(1, server.requestCount, "inside the max age")

        enqueueHtml("thread_3pxj858k_p2.html")
        repo.load(nbn, 2, force = true)
        assertEquals(2, server.requestCount, "a manual refresh after the 10 s throttle")

        clock.now = clock.now.plus(Duration.ofMinutes(2))
        enqueueHtml("thread_3pxj858k_p2.html")
        repo.load(nbn, 2)
        assertEquals(3, server.requestCount, "past the max age")
    }

    @Test
    fun `the last page resolves through p=-1, then from the cache`() = runTest {
        val id = ThreadId("97r5wzn3")
        assertNull(repo.lastKnownPage(id))
        enqueueHtml("thread_97r5wzn3_last.html")
        assertEquals(2290, repo.load(id, ThreadPageRepository.LAST_PAGE))
        assertEquals("/thread/97r5wzn3?p=-1", server.takeRequest().target)
        assertEquals(17, repo.page(id, 2290).first()?.value?.posts?.size)

        assertEquals(2290, repo.load(id, ThreadPageRepository.LAST_PAGE))
        assertEquals(1, server.requestCount, "a fresh cached last page is reused")
    }

    @Test
    fun `filters use the site's parameters and cache apart`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        repo.load(nbn, 1, PostFilter.ByUser(30703))
        assertEquals("/thread/3pxj858k?ux=30703", server.takeRequest().target)
        enqueueHtml("thread_3pxj858k_p2.html")
        repo.load(nbn, 1, PostFilter.Moderators)
        assertEquals("/thread/3pxj858k?um=1", server.takeRequest().target)
        enqueueHtml("thread_3pxj858k_p2.html")
        repo.load(nbn, 1, PostFilter.Reps)
        assertEquals("/thread/3pxj858k?ur=1", server.takeRequest().target)
        // The fixture is page 2, so each filter's copy is stored under the page the server reported.
        assertNotNull(repo.page(nbn, 2, PostFilter.Moderators).first())
        assertNull(repo.page(nbn, 2).first())
    }

    @Test
    fun `a page that needs a web login marks its forum private`() = runTest {
        db.forumDao().replaceAll(listOf(ForumEntity(35, "Feedback", "Whirlpool", 0)))
        enqueueHtml("thread_private_feedback_3nzn4p83.html")
        assertFailsWith<WhirlpoolError.NotSignedIn> { repo.load(ThreadId("3nzn4p83"), 1, forumId = 35) }
        assertTrue(forums.forums.first().value.single().isPrivate)
    }

    @Test
    fun `an unreadable page is drift`() = runTest {
        server.enqueue(MockResponse.Builder().body("<html><head><title>Whirlpool</title></head><body><p>new layout</p></body></html>").build())
        assertFailsWith<WhirlpoolError.Drift> { repo.load(nbn, 1) }
        server.enqueue(MockResponse.Builder().body("""<html><body><div class="replyblock" data-replyid="x"></div></body></html>""").build())
        assertFailsWith<WhirlpoolError.Drift> { repo.load(nbn, 1, force = true) }
    }

    @Test
    fun `posted times are stored as Sydney local time`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        repo.load(nbn, 2)
        val first = repo.page(nbn, 2).first()!!.value.posts.first()
        val parsed = au.howesthat.whirl.core.scraper.WhirlpoolTime.parseAbsolute(first.postedText!!)
        assertEquals<LocalDateTime?>(parsed, first.postedAt)
    }
}
