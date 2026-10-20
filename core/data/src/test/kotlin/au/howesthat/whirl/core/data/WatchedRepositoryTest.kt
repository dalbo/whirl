package au.howesthat.whirl.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
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
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchedRepositoryTest {

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MutableClock()
    // Sends run on a real scope, as they do in the app; tests wait on the database instead of virtual time.
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val api = WhirlpoolApi(WhirlpoolHttp.client("Whirl/test"), { "k" }, server.url("/api/"), RetryPolicy.NONE)
    private val repo = WatchedRepository(api, db.threadDao(), db.fetchLogDao(), clock, appScope).apply { undoWindow = 10.minutes }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        db.close()
    }

    private fun fixture(name: String) = File(System.getProperty("whirl.fixtures"), "api/$name").readText()

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private suspend fun ids(unreadOnly: Boolean) = repo.watched(unreadOnly).first().value.map { it.id.value }

    private suspend fun loadBothLists() {
        enqueue(fixture("api_watched_mode1.json"))
        enqueue(fixture("api_watched_mode0.json"))
        repo.refresh(unreadOnly = false)
        repo.refresh(unreadOnly = true)
        repeat(2) { server.takeRequest() }
    }

    private val bike = ThreadId("9yvx1663")

    @Test
    fun `mark read shows at once and is sent with the list when the undo offer closes`() = runTest {
        loadBothLists()
        val p = repo.perform(WatchAction.MARK_READ, listOf(bike))
        assertEquals(listOf("9q684xp3"), ids(unreadOnly = true))
        assertEquals(0, repo.watched(unreadOnly = false).first().value.single { it.id == bike }.unreadCount)
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS), "nothing is sent during the undo window")

        enqueue(fixture("api_watched_mode1.json").replace("\"UNREAD\":683", "\"UNREAD\":0"))
        repo.commit(p)
        val url = server.takeRequest(5, TimeUnit.SECONDS)!!.url
        assertEquals("9yvx1663", url.queryParameter("watchedread"))
        assertEquals("watched", url.queryParameter("get"))
        assertEquals("1", url.queryParameter("watchedmode"))
        // Both lists come from the one answer.
        repo.watched(unreadOnly = true).first { it.value.map { t -> t.id.value } == listOf("9q684xp3") }
        assertEquals(19, ids(unreadOnly = false).size)
        assertFalse(repo.undo(p), "too late to undo once sent")
    }

    @Test
    fun `undo restores the lists and sends nothing`() = runTest {
        loadBothLists()
        val p = repo.perform(WatchAction.UNWATCH, listOf(bike))
        assertFalse(bike.value in ids(unreadOnly = false))
        assertFalse(bike in repo.watchedIds.first())
        assertTrue(repo.undo(p))
        assertEquals(listOf("9yvx1663", "9q684xp3"), ids(unreadOnly = true), "back in its place")
        assertEquals(19, ids(unreadOnly = false).size)
        assertEquals(683, repo.watched(unreadOnly = true).first().value.first().unreadCount)
        repo.commit(p)
        assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a failed send rolls back and reports`() = runTest {
        loadBothLists()
        val failed = async(start = CoroutineStart.UNDISPATCHED) { repo.events.filterIsInstance<WatchEvent.Failed>().first() }
        val p = repo.perform(WatchAction.MARK_READ, listOf(bike))
        assertEquals(listOf("9q684xp3"), ids(unreadOnly = true))
        enqueue(fixture("api_cmd_bogus_id_500.html"), code = 500)
        repo.commit(p)
        val event = failed.await()
        assertEquals(WatchAction.MARK_READ, event.action)
        assertIs<WhirlpoolError.Maintenance>(event.error)
        assertEquals(listOf("9yvx1663", "9q684xp3"), ids(unreadOnly = true))
        assertEquals(683, repo.watched(unreadOnly = true).first().value.first().unreadCount)
    }

    @Test
    fun `watch shows the thread as watched before Whirlpool confirms`() = runTest {
        loadBothLists()
        val nbn = ThreadId("3pxj858k")
        val applied = async(start = CoroutineStart.UNDISPATCHED) { repo.events.filterIsInstance<WatchEvent.Applied>().first() }
        val p = repo.perform(WatchAction.WATCH, listOf(nbn))
        assertEquals(p, applied.await().pending)
        assertTrue(repo.isWatched(nbn).first())

        enqueue(fixture("api_cmd_watchedadd_with_get.json"))
        repo.commit(p)
        assertEquals("3pxj858k", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedadd"))
        repo.watched(unreadOnly = false).first { it.value.size == 20 }
        val added = repo.watched(unreadOnly = false).first().value.single { it.id == nbn }
        assertEquals("Simple, ad-free NBN plan comparison tool I built", added.title)
        assertEquals(listOf("9yvx1663", "9q684xp3"), ids(unreadOnly = true), "a new watch comes back read")
    }

    @Test
    fun `a refresh during the undo window keeps the pending change`() = runTest {
        loadBothLists()
        repo.perform(WatchAction.UNWATCH, listOf(bike))
        enqueue(fixture("api_watched_mode1.json"))
        clock.now = clock.now.plusSeconds(11)
        assertTrue(repo.refresh(unreadOnly = false, force = true))
        assertFalse(bike.value in ids(unreadOnly = false))
    }

    @Test
    fun `without undo the action is sent at once and the window closes by itself`() = runTest {
        loadBothLists()
        enqueue(fixture("api_watched_mode1.json"))
        repo.perform(WatchAction.MARK_READ, listOf(bike), undoable = false)
        assertEquals("9yvx1663", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedread"))

        repo.undoWindow = 50.milliseconds
        enqueue(fixture("api_watched_mode1.json"))
        repo.perform(WatchAction.UNWATCH, listOf(ThreadId("9q684xp3")))
        assertEquals("9q684xp3", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedremove"))
    }
}
