package au.howesthat.whirl.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.database.ThreadDao
import au.howesthat.whirl.core.database.ThreadEntity
import au.howesthat.whirl.core.database.ThreadSourceEntity
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class WatchedRepositoryTest {

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MutableClock()
    // Sends run on a real scope, as they do in the app; tests wait on the database instead of virtual time.
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val api = WhirlpoolApi(WhirlpoolHttp.client("Whirl/test"), { "k" }, server.url("/api/"), RetryPolicy.NONE)
    private val scheduler = RecordingScheduler()
    private val repo = newRepository()

    /** Another repository over the same database: what the app has after its process was ended and restarted. */
    private fun newRepository() =
        WatchedRepository(api, db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), scheduler, clock, appScope).apply { undoWindow = 10.minutes }

    class RecordingScheduler : WatchSendScheduler {
        val delays = mutableListOf<Duration>()
        var cancelled = false
        override fun schedule(delay: Duration) {
            delays += delay
        }
        override fun cancel() {
            cancelled = true
        }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        db.close()
    }

    private fun fixture(name: String) = File(System.getProperty("whirl.fixtures"), "api/$name").readText()

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private suspend fun ids(unreadOnly: Boolean) = repo.watched(unreadOnly).first().value.map { it.id.value }

    /** The All and Unread lists, as a refresh of both asks for them. */
    private fun enqueueBothLists() {
        enqueue(fixture("api_watched_mode1.json"))
        enqueue(fixture("api_watched_mode0.json"))
    }

    private suspend fun loadBothLists() {
        enqueueBothLists()
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
        // Whirlpool is up and still lists the thread: the 500 was a refusal, not an outage.
        enqueue(fixture("api_watched_mode1.json"))
        enqueueBothLists()
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
    fun `with undo turned off the action is sent at once and still announced, without undo`() = runTest {
        loadBothLists()
        val applied = async(start = CoroutineStart.UNDISPATCHED) { repo.events.filterIsInstance<WatchEvent.Applied>().first() }
        enqueue(fixture("api_watched_mode1.json"))
        val p = repo.perform(WatchAction.MARK_READ, listOf(bike), undoable = false, announce = true)
        assertEquals(WatchEvent.Applied(p, undoable = false), applied.await())
        assertEquals("9yvx1663", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedread"))
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

    private val queue get() = db.pendingWatchDao()

    @Test
    fun `an action is stored until it is sent and the backstop is scheduled`() = runTest {
        loadBothLists()
        val p = repo.perform(WatchAction.MARK_READ, listOf(bike))
        assertEquals(listOf("MARK_READ"), queue.all().map { it.action })
        assertEquals(listOf(10.minutes), scheduler.delays)
        enqueue(fixture("api_watched_mode1.json").replace("\"UNREAD\":683", "\"UNREAD\":0"))
        repo.commit(p)
        server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(repo.awaitSend(p, 5.seconds))
        assertEquals(emptyList(), queue.all())
    }

    @Test
    fun `after the process ends a queued action is kept on the lists and sent when due`() = runTest {
        loadBothLists()
        repo.perform(WatchAction.UNWATCH, listOf(bike))
        appScope.cancel() // the in-app timer dies with the process

        val restarted = newRepository()
        enqueue(fixture("api_watched_mode1.json"))
        clock.now = clock.now.plusSeconds(3600)
        restarted.refresh(unreadOnly = false, force = true)
        server.takeRequest()
        assertFalse(bike.value in ids(unreadOnly = false), "a refresh does not bring the thread back")

        scheduler.delays.clear()
        restarted.resume()
        assertEquals(listOf(Duration.ZERO), scheduler.delays)
        enqueue(fixture("api_watched_mode1.json"))
        assertEquals(Drain.Empty, restarted.drain())
        assertEquals("9yvx1663", server.takeRequest().url.queryParameter("watchedremove"))
        assertEquals(emptyList(), queue.all())
    }

    @Test
    fun `a row cut off while sending is sent again`() = runTest {
        loadBothLists()
        val p = repo.perform(WatchAction.MARK_READ, listOf(bike))
        queue.claim(queue.all().single().id)
        assertFalse(repo.undo(p), "too late once it is being sent")
        queue.makeDue(queue.all().single().id, clock.millis())
        enqueue(fixture("api_watched_mode1.json"))
        assertEquals(Drain.Empty, newRepository().drain())
        assertEquals("9yvx1663", server.takeRequest().url.queryParameter("watchedread"))
    }

    @Test
    fun `an action on a thread waits for an earlier one on it, but not for other threads`() = runTest {
        loadBothLists()
        val nbn = ThreadId("3pxj858k")
        repo.perform(WatchAction.WATCH, listOf(nbn))
        // Still in Watch's Undo window: marking it read must not overtake the Watch.
        repo.perform(WatchAction.MARK_READ, listOf(nbn), undoable = false)
        enqueue(fixture("api_watched_mode1.json"))
        repo.perform(WatchAction.UNWATCH, listOf(bike), undoable = false)
        assertEquals("9yvx1663", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedremove"))
        val waiting = assertIs<Drain.Waiting>(repo.drain())
        assertEquals(10.minutes, waiting.wait)
        assertEquals(listOf("WATCH", "MARK_READ"), queue.all().map { it.action })
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a send that does not get through stays queued for a retry`() = runTest {
        loadBothLists()
        enqueue("", code = 429)
        val p = repo.perform(WatchAction.MARK_READ, listOf(bike), undoable = false)
        server.takeRequest(5, TimeUnit.SECONDS)!!
        assertTrue(repo.awaitSend(p, 200.milliseconds), "still queued counts as sent for a notification button")
        val row = queue.all().single()
        assertEquals(1, row.attempts)
        assertEquals("WAITING", row.state)
        assertEquals(Duration.ZERO, scheduler.delays.last())
        assertEquals(listOf("9q684xp3"), ids(unreadOnly = true), "still shown as read")

        enqueue(fixture("api_watched_mode1.json"))
        assertEquals(Drain.Empty, repo.drain())
        assertEquals(emptyList(), queue.all())
    }

    @Test
    fun `a send that keeps failing for a day is given up and rolled back`() = runTest {
        loadBothLists()
        repo.perform(WatchAction.MARK_READ, listOf(bike))
        val failed = async(start = CoroutineStart.UNDISPATCHED) { repo.events.filterIsInstance<WatchEvent.Failed>().first() }
        clock.now = clock.now.plusSeconds(25 * 3600)
        enqueue("", code = 429)
        enqueueBothLists()
        assertEquals(Drain.Empty, repo.drain())
        assertIs<WhirlpoolError.RateLimited>(failed.await().error)
        assertEquals(listOf("9yvx1663", "9q684xp3"), ids(unreadOnly = true))
    }

    @Test
    fun `mark read of a thread deleted since the list was fetched is sent again without it`() = runTest {
        loadBothLists()
        val gone = ThreadId("zzzz9999")
        db.threadDao().insertSource(listOf(ThreadSourceEntity("WATCHED", "unread", gone.value, 99)))
        val p = repo.perform(WatchAction.MARK_READ, listOf(bike, gone))
        enqueue(fixture("api_cmd_bogus_id_500.html"), code = 500)
        enqueue(fixture("api_watched_mode1.json"))
        enqueue(fixture("api_watched_mode1.json"))
        repo.commit(p)
        assertEquals("9yvx1663,zzzz9999", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedread"))
        assertEquals("1", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedmode"))
        assertEquals("9yvx1663", server.takeRequest(5, TimeUnit.SECONDS)!!.url.queryParameter("watchedread"))
        assertTrue(repo.awaitSend(p, 5.seconds))
        assertEquals(emptyList(), queue.all())
    }

    @Test
    fun `the notification check leaves out threads marked read but not yet sent`() = runTest {
        loadBothLists()
        repo.perform(WatchAction.MARK_READ, listOf(bike))
        enqueue(fixture("api_watched_mode0.json"))
        assertEquals(listOf("9q684xp3"), repo.fetchUnread().map { it.id.value })
    }

    @Test
    fun `signing out forgets queued actions`() = runTest {
        loadBothLists()
        repo.perform(WatchAction.UNWATCH, listOf(bike))
        repo.switchAccount(change = {}, clear = {})
        assertTrue(scheduler.cancelled)
        assertEquals(emptyList(), queue.all())
        assertEquals(Drain.Empty, repo.drain())
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun `a list replaced from Whirlpool and the actions not yet sent are written in one transaction`() = runTest {
        // Writes made outside a transaction are what observers (the lists, the notification check) can see half done.
        val outside = mutableListOf<String>()
        val real = db.threadDao()
        val dao = object : ThreadDao by real {
            fun check(name: String) {
                if (!db.inTransaction()) outside += name
            }
            override suspend fun markRead(ids: List<String>) = check("markRead").also { real.markRead(ids) }
            override suspend fun removeFromSource(source: String, ids: List<String>) =
                check("removeFromSource").also { real.removeFromSource(source, ids) }
            override suspend fun removeFromSourceKey(source: String, key: String, ids: List<String>) =
                check("removeFromSourceKey").also { real.removeFromSourceKey(source, key, ids) }
            override suspend fun insertSource(rows: List<ThreadSourceEntity>) = check("insertSource").also { real.insertSource(rows) }
            override suspend fun upsert(threads: List<ThreadEntity>) = check("upsert").also { real.upsert(threads) }
            override suspend fun deleteOrphans() = check("deleteOrphans").also { real.deleteOrphans() }
        }
        val repo = WatchedRepository(api, dao, db.fetchLogDao(), queue, RoomTransactor(db), AccountGeneration(), scheduler, clock, appScope)
            .apply { undoWindow = 10.minutes }
        enqueueBothLists()
        repo.refresh(unreadOnly = false)
        repo.refresh(unreadOnly = true)
        val unwatch = repo.perform(WatchAction.UNWATCH, listOf(bike))
        repo.perform(WatchAction.MARK_READ, listOf(ThreadId("9q684xp3")))
        repo.perform(WatchAction.WATCH, listOf(ThreadId("3pxj858k")))
        clock.now = clock.now.plusSeconds(11)
        enqueue(fixture("api_watched_mode1.json"))
        repo.refresh(unreadOnly = false, force = true)
        enqueue(fixture("api_watched_mode0.json"))
        repo.fetchUnread()
        assertTrue(repo.undo(unwatch))
        assertEquals(emptyList(), outside)
    }

    @Test
    fun `a refresh in flight when the account changes stores nothing`() = runTest {
        loadBothLists()
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                release.await(5, TimeUnit.SECONDS)
                return MockResponse.Builder().body(fixture("api_watched_mode1.json")).build()
            }
        }
        clock.now = clock.now.plusSeconds(11)
        val refresh = async(Dispatchers.IO) { repo.refresh(unreadOnly = false, force = true) }
        val unread = async(Dispatchers.IO) { repo.fetchUnread() }
        repeat(2) { server.takeRequest(5, TimeUnit.SECONDS)!! }
        repo.switchAccount(change = {}, clear = db::clearAccountData)
        release.countDown()
        refresh.await()
        assertEquals(emptyList(), unread.await(), "nothing to notify about for an account that has gone")
        assertEquals(emptyList(), ids(unreadOnly = false))
        assertEquals(emptyList(), ids(unreadOnly = true))
        assertNull(db.fetchLogDao().fetchedAt("watched:all"), "the next account's list is fetched at once")
    }

    /** Sends [action] on [bike] after a refresh that renamed it, and has Whirlpool refuse it. */
    private suspend fun failLate(action: WatchAction): WatchEvent.Failed = coroutineScope {
        loadBothLists()
        val failed = async(start = CoroutineStart.UNDISPATCHED) { repo.events.filterIsInstance<WatchEvent.Failed>().first() }
        val p = repo.perform(action, listOf(bike))
        clock.now = clock.now.plusSeconds(3600)
        val renamed = fixture("api_watched_mode1.json").replace("\"Bike Riding\"", "\"Bike Riding II\"")
        enqueue(renamed)
        repo.refresh(unreadOnly = false, force = true)
        assertFalse(bike.value in ids(unreadOnly = true), "still applied while it waits")

        enqueue(fixture("api_cmd_bogus_id_500.html"), code = 500) // refused
        enqueue(renamed) // Whirlpool is up and still lists it: give up
        enqueue("", code = 429) // the refresh after the failure does not get through
        repo.commit(p)
        failed.await().also {
            assertEquals(6, server.requestCount, "two lists, the refresh, the send, the check and the refresh after it")
            assertEquals(emptyList(), queue.all())
        }
    }

    @Test
    fun `a late failure puts the thread back on its lists without undoing a refresh made since`() = runTest {
        assertIs<WhirlpoolError.Maintenance>(failLate(WatchAction.MARK_READ).error)
        assertEquals("Bike Riding II", repo.watched(unreadOnly = false).first().value.single { it.id == bike }.title, "the details fetched since are kept")
        assertTrue(bike.value in ids(unreadOnly = true), "back on Unread, where it was")
    }

    @Test
    fun `a late failure of an unwatch brings back a thread no longer cached`() = runTest {
        failLate(WatchAction.UNWATCH)
        // Unwatched, the thread was on no list, so the refresh dropped it from the cache; the snapshot brings it back.
        assertEquals("Bike Riding", repo.watched(unreadOnly = false).first().value.single { it.id == bike }.title)
        assertTrue(bike.value in ids(unreadOnly = true))
    }
}
