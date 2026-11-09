package au.howesthat.whirl.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.PostRole
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.network.WhirlpoolWeb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArchiveRepositoryTest {

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MutableClock()
    private val client = WhirlpoolHttp.client("Whirl/test")
    private val web = WhirlpoolWeb(client, WhirlpoolUrls(server.url("/")), RetryPolicy.NONE)
    private val forums = ForumRepository(WhirlpoolApi(client, { null }, server.url("/api/"), RetryPolicy.NONE), db.forumDao(), db.fetchLogDao(), clock)
    private val threadPages = ThreadPageRepository(web, db.threadPageDao(), forums, clock, UnconfinedTestDispatcher())
    private val repo = ArchiveRepository(web, db.archiveDao(), threadPages, clock, UnconfinedTestDispatcher(), 1.seconds)

    @After
    fun tearDown() {
        server.close()
        db.close()
    }

    private fun html(name: String) = File(System.getProperty("whirl.fixtures"), "html/$name").readText()
    private fun enqueue(name: String) = server.enqueue(MockResponse.Builder().body(html(name)).build())
    private fun enqueue(code: Int) = server.enqueue(MockResponse.Builder().code(code).body("").build())
    private var taken = 0

    /** The requests since the last call (MockWebServer's count is cumulative). */
    private fun requests(): List<String?> = List(server.requestCount - taken) { server.takeRequest().target }.also { taken += it.size }

    private val rtx = ThreadId("97r5wzn3")
    private val sticky = ThreadId("3mnkq4v9")
    private val leaptel = ThreadId("3l10nlj5")

    @Test
    fun `opens the newest post by fetching its archive page directly`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        val opened = repo.open(rtx)

        // The last thread page ends at post 45797: archive page ceil(45797 / 800) = 58, fetched without the list.
        assertEquals(listOf("/thread/97r5wzn3?p=-1", "/archive/97r5wzn3-58"), requests())
        assertEquals("/archive/97r5wzn3-58", opened.path)
        assertNull(opened.number)
        assertFalse(opened.offline)
        assertEquals(58, opened.thread.pages.size)
        assertEquals("/archive/2747207", opened.thread.pages.first().path)
        val posts = repo.posts(rtx, opened.path)
        assertEquals(524, posts.size)
        assertEquals(45601, posts.first().number)
        assertTrue(posts.all { it.postedAt != null })
    }

    @Test
    fun `opening again with no newer post fetches nothing from the archive`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        repo.open(rtx)
        requests()

        // The thread page is still fresh, and the cached archive page already holds its newest post.
        val again = repo.open(rtx, number = 45700)
        assertEquals(emptyList(), requests())
        assertEquals("/archive/97r5wzn3-58", again.path)
        assertEquals(45700, again.number)
    }

    @Test
    fun `a guessed page that 404s goes through the entry page's list`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue(404)
        // The entry page, standing in: its list names page 58 as the current page.
        enqueue("archive_97r5wzn3_last.html")
        val opened = repo.open(rtx)
        assertEquals(listOf("/thread/97r5wzn3?p=-1", "/archive/97r5wzn3-58", "/archive/97r5wzn3"), requests())
        assertEquals("/archive/97r5wzn3-58", opened.path)
    }

    @Test
    fun `a multi-part thread opens through its entry page and keeps only its own part`() = runTest {
        enqueue("thread_sticky_3mnkq4v9.html")
        enqueue("archive_3mnkq4v9_chunks.html")
        val opened = repo.open(sticky, number = 1)
        assertEquals(listOf("/thread/3mnkq4v9?p=-1", "/archive/3mnkq4v9"), requests())
        // Posts restart at 1 in each part: the third part's 31 pages, starting at /archive/2690338.
        assertEquals(31, opened.thread.pages.size)
        assertEquals("/archive/2690338", opened.path)
        assertEquals("/archive/3mnkq4v9-31", opened.thread.pages.last().path)
        assertEquals("/archive/3mnkq4v9-2", opened.thread.pageFor(801)?.path)
    }

    @Test
    fun `offline, the cached page opens`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        repo.open(rtx)
        requests()

        server.close()
        clock.now = clock.now.plusSeconds(600) // the thread page is stale, so it is fetched, and fails
        val opened = repo.open(rtx, number = 45700)
        assertTrue(opened.offline)
        assertEquals("/archive/97r5wzn3-58", opened.path)
    }

    @Test
    fun `offline with nothing cached fails`() = runTest {
        server.close()
        assertFailsWith<WhirlpoolError.Network> { repo.open(rtx) }
    }

    @Test
    fun `filters read the index of every fetched page`() = runTest {
        enqueue("archive_3l10nlj5_reps.html")
        val result = repo.loadPages(leaptel, listOf("/archive/3l10nlj5"), ArchiveRepository.Need.INDEX)
        assertEquals(ArchiveRepository.LoadResult(1, null), result)

        val reps = repo.filter(leaptel, PostFilter.Reps)
        assertEquals(61, reps.size)
        assertTrue(reps.all { it.role == PostRole.REP })
        assertEquals(3, repo.filter(leaptel, PostFilter.Moderators).size)
        val matt = repo.filter(leaptel, PostFilter.User(27743))
        assertTrue(matt.isNotEmpty() && matt.all { it.author.name == "Matt E." })
        assertEquals(matt.map { it.number }.sorted(), matt.map { it.number })
        assertTrue(repo.filter(leaptel, PostFilter.Op).all { it.author.id == repo.filter(leaptel, PostFilter.Op).first().author.id })
        assertEquals(setOf("/archive/3l10nlj5"), repo.indexedPaths(leaptel))

        // Already indexed: nothing to fetch.
        assertEquals(ArchiveRepository.LoadResult(0, null), repo.loadPages(leaptel, listOf("/archive/3l10nlj5"), ArchiveRepository.Need.INDEX))
        assertEquals(listOf("/archive/3l10nlj5"), requests())
    }

    @Test
    fun `loading earlier pages stops at the daily cap and on rate limiting`() = runTest {
        enqueue("archive_3l10nlj5_reps.html")
        repo.loadPages(leaptel, listOf("/archive/3l10nlj5"), ArchiveRepository.Need.INDEX)
        requests()

        val today = LocalDate.now(clock).toEpochDay()
        db.archiveDao().setSearched(leaptel.value, today, ArchiveRepository.DAILY_CAP)
        val capped = repo.loadPages(leaptel, listOf("/archive/3l10nlj5-2", "/archive/3l10nlj5-3"), ArchiveRepository.Need.INDEX)
        assertEquals(ArchiveRepository.LoadResult(0, ArchiveRepository.Stop.DAILY_CAP), capped)
        assertEquals(emptyList(), requests())

        // A new day, and Whirlpool says slow down.
        clock.now = clock.now.plusSeconds(86_400)
        enqueue(429)
        val limited = repo.loadPages(leaptel, listOf("/archive/3l10nlj5-2", "/archive/3l10nlj5-3"), ArchiveRepository.Need.INDEX)
        assertEquals(ArchiveRepository.LoadResult(0, ArchiveRepository.Stop.RATE_LIMITED), limited)
        assertEquals(listOf("/archive/3l10nlj5-2"), requests())
    }

    @Test
    fun `pages are evicted by count, never the open thread's, and the index stays`() = runTest {
        enqueue("archive_3l10nlj5_reps.html")
        repo.loadPages(leaptel, listOf("/archive/3l10nlj5"), ArchiveRepository.Need.BODIES)
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        repo.open(rtx)

        db.archiveDao().evict(keep = 0, openThreadId = rtx.value)
        assertEquals(emptyList(), repo.posts(leaptel, "/archive/3l10nlj5"))
        assertEquals(524, repo.posts(rtx, "/archive/97r5wzn3-58").size)
        assertEquals(61, repo.filter(leaptel, PostFilter.Reps).size, "the index outlives the bodies")
    }

    @Test
    fun `a scan keeps only the matching posts' bodies and answers again from the cache`() = runTest {
        enqueue("archive_3l10nlj5_reps.html")
        val path = "/archive/3l10nlj5"
        val reps = repo.scan(leaptel, listOf(path), ArchiveRepository.ScanQuery.Filter(PostFilter.Reps))
        assertEquals(61, reps.matches.size)
        assertEquals(listOf(path), requests())
        // The page is not whole: the reader would fetch it, but the reps' posts are there to show.
        assertEquals(emptyList(), repo.posts(leaptel, path))
        val shown = repo.postsByIds(leaptel, reps.matches)
        assertEquals(61, shown.size)
        assertTrue(shown.all { it.role == PostRole.REP && it.bodyHtml.isNotEmpty() })

        // Again: the index and the kept bodies answer it.
        assertEquals(reps.matches, repo.scan(leaptel, listOf(path), ArchiveRepository.ScanQuery.Filter(PostFilter.Reps)).matches)
        assertEquals(emptyList(), requests())

        // Moderators' posts are in the index but their bodies were not kept: fetched again.
        enqueue("archive_3l10nlj5_reps.html")
        assertEquals(3, repo.scan(leaptel, listOf(path), ArchiveRepository.ScanQuery.Filter(PostFilter.Moderators)).matches.size)
        assertEquals(listOf(path), requests())

        // Reading the page fetches it whole.
        enqueue("archive_3l10nlj5_reps.html")
        repo.loadPages(leaptel, listOf(path), ArchiveRepository.Need.BODIES)
        assertEquals(listOf(path), requests())
        assertEquals(616, repo.posts(leaptel, path).size)
    }

    @Test
    fun `a text scan of a whole cached page fetches nothing`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        val opened = repo.open(rtx)
        requests()
        val found = repo.scan(rtx, listOf(opened.path), ArchiveRepository.ScanQuery.Text(au.howesthat.whirl.core.model.TextQuery("5090")))
        assertTrue(found.matches.isNotEmpty())
        assertEquals(emptyList(), requests())
        val posts = repo.postsByIds(rtx, found.matches)
        assertEquals(found.matches, posts.map { it.replyId })
    }
}
