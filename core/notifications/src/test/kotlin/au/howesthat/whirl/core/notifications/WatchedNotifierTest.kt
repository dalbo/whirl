package au.howesthat.whirl.core.notifications

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.data.NotifyStyle
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.database.NotifiedEntity
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchedNotifierTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(context, WhirlDatabase::class.java).allowMainThreadQueries().build()
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings = SettingsRepository(context)
    private val api = WhirlpoolApi(WhirlpoolHttp.client("Whirl/test"), settings::apiKey, server.url("/api/"), RetryPolicy.NONE)
    private val watched = WatchedRepository(api, db.threadDao(), db.fetchLogDao(), Clock.systemUTC(), appScope)
    private val notifier = WatchedNotifier(context, settings, watched, db.notifiedDao())
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() = runTest {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        settings.signIn("k", Account(216903, "d@llin"))
        settings.update { it.copy(watchedNotify = true, ignoreOwnRepliesWatched = false, notifyStyle = NotifyStyle.SUMMARY, notifyKeepUntilRead = false) }
    }

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        db.close()
    }

    private fun fixture(name: String) = File(System.getProperty("whirl.fixtures"), "api/$name").readText()

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun shown(): Notification? = shadowOf(manager).getNotification(WatchedNotifier.NOTIFICATION_ID)

    private val unreadTwo get() = fixture("api_watched_mode0.json")

    private fun thread(id: String): Notification? = shadowOf(manager).getNotification(id, WatchedNotifier.THREAD_NOTIFICATION_ID)

    private fun groupSummary(): Notification? = shadowOf(manager).getNotification(WatchedNotifier.GROUP_SUMMARY_ID)

    private fun lines(n: Notification) = n.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)!!.map { it.toString() }

    private fun alerts(n: Notification) = n.flags and Notification.FLAG_ONLY_ALERT_ONCE == 0

    private suspend fun cachedUnread() = watched.watched(unreadOnly = true).first().value

    @Test
    fun `new replies alert once, listing every unread thread`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        val url = server.takeRequest().url
        assertEquals("watched", url.queryParameter("get"))
        assertEquals("0", url.queryParameter("watchedmode"))

        val n = assertNotNull(shown())
        assertEquals(WatchedNotifier.CHANNEL_WATCHED, n.channelId)
        assertEquals("New watched thread replies", n.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("18210 new replies in 2 threads", n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        val lines = n.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)!!.map { it.toString() }
        assertEquals(listOf("Bike Riding (683)", "Electric Vehicles (17527)"), lines)
        assertEquals(0, n.flags and Notification.FLAG_ONLY_ALERT_ONCE, "alerts")
        assertEquals(listOf("Mark read", "Unwatch"), n.actions.map { it.title.toString() })
        assertEquals(setOf("9yvx1663", "9q684xp3"), db.notifiedDao().all().map { it.threadId }.toSet())

        // Same replies again: the notification is refreshed without alerting.
        enqueue(unreadTwo)
        notifier.check()
        assertTrue(assertNotNull(shown()).flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)

        // Dismissed, and nothing new: it stays dismissed.
        manager.cancelAll()
        enqueue(unreadTwo)
        notifier.check()
        assertNull(shown())
    }

    @Test
    fun `a reply newer than the one notified alerts again`() = runTest {
        fun millis(date: String) = OffsetDateTime.parse(date).toInstant().toEpochMilli()
        // Both already notified at their current last posts: no alert, and nothing showing to refresh.
        db.notifiedDao().put(
            listOf(NotifiedEntity("9yvx1663", millis("2026-10-20T17:23:15+10:00")), NotifiedEntity("9q684xp3", millis("2026-10-20T14:28:45+10:00"))),
        )
        enqueue(unreadTwo)
        notifier.check()
        assertNull(shown())

        // 9yvx1663 was notified for an earlier post.
        db.notifiedDao().put(listOf(NotifiedEntity("9yvx1663", millis("2026-10-20T17:00:00+10:00"))))
        enqueue(unreadTwo)
        notifier.check()
        assertEquals(0, assertNotNull(shown()).flags and Notification.FLAG_ONLY_ALERT_ONCE)
    }

    @Test
    fun `nothing unread cancels, and forgets what was notified`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        assertNotNull(shown())
        enqueue(fixture("api_recent_14d.json").replace("\"RECENT\"", "\"WATCHED\""))
        notifier.check()
        assertNull(shown())
        assertEquals(emptyList(), db.notifiedDao().all())
    }

    @Test
    fun `one thread is named in the title`() = runTest {
        // With "ignore my own replies", a thread whose last post is mine (muli's, here) is left out.
        settings.signIn("k", Account(456291, "muli"))
        settings.update { it.copy(ignoreOwnRepliesWatched = true) }
        enqueue(unreadTwo)
        notifier.check()
        val n = assertNotNull(shown())
        assertEquals(1, n.extras.getCharSequenceArray(NotificationCompat.EXTRA_TEXT_LINES)!!.size)
        assertEquals("Bike Riding", n.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("683 unread posts", n.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
    }

    @Test
    fun `alerts off cancels without calling the API`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        settings.update { it.copy(watchedNotify = false) }
        notifier.check()
        assertNull(shown())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `without permission nothing is posted`() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        notifier.check()
        assertNull(shown())
        assertEquals(0, server.requestCount)
        assertEquals(emptyList(), db.notifiedDao().all(), "they alert once allowed")

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        enqueue(unreadTwo)
        notifier.check()
        assertNotNull(shown())
    }

    @Test
    fun `the notification's Mark read button marks every listed thread read and removes it`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        val markRead = assertNotNull(shown()).actions.first()
        val intent = shadowOf(markRead.actionIntent).savedIntent
        val (action, ids) = assertNotNull(NotificationActionReceiver.parse(intent))
        assertEquals(WatchAction.MARK_READ, action)
        assertEquals(listOf(ThreadId("9yvx1663"), ThreadId("9q684xp3")), ids)

        server.takeRequest()
        enqueue(fixture("api_watched_mode1.json").replace("\"UNREAD\":683", "\"UNREAD\":0").replace("\"UNREAD\":17527", "\"UNREAD\":0"))
        assertTrue(notifier.act(action, ids))
        assertEquals("9yvx1663,9q684xp3", server.takeRequest().url.queryParameter("watchedread"))
        assertNull(shown())
        assertEquals(emptyList(), watched.watched(unreadOnly = true).first().value)
    }

    @Test
    fun `a failed button keeps the notification`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        enqueue(fixture("api_cmd_bogus_id_500.html"), code = 500)
        assertFalse(notifier.act(WatchAction.UNWATCH, listOf(ThreadId("9yvx1663"))))
        assertNotNull(shown())
        assertEquals(2, watched.watched(unreadOnly = true).first().value.size, "rolled back")
    }

    @Test
    fun `a tap removes the summary, unless it is kept until read`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        assertTrue(assertNotNull(shown()).flags and Notification.FLAG_AUTO_CANCEL != 0)
        settings.update { it.copy(notifyKeepUntilRead = true) }
        notifier.sync(cachedUnread())
        assertEquals(0, assertNotNull(shown()).flags and Notification.FLAG_AUTO_CANCEL)
    }

    @Test
    fun `one per thread, each thread gets its own notification and buttons, under a silent group summary`() = runTest {
        settings.update { it.copy(notifyStyle = NotifyStyle.PER_THREAD) }
        enqueue(unreadTwo)
        notifier.check()
        assertNull(shown())

        val bike = assertNotNull(thread("9yvx1663"))
        assertEquals("Bike Riding", bike.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("683 unread posts", bike.extras.getCharSequence(NotificationCompat.EXTRA_TEXT).toString())
        assertTrue(alerts(bike))
        assertTrue(bike.flags and Notification.FLAG_AUTO_CANCEL != 0, "a tap opens the thread and removes it")
        val (action, ids) = assertNotNull(NotificationActionReceiver.parse(shadowOf(bike.actions.first().actionIntent).savedIntent))
        assertEquals(WatchAction.MARK_READ, action)
        assertEquals(listOf(ThreadId("9yvx1663")), ids)
        val open = shadowOf(bike.contentIntent).savedIntent
        assertEquals("9yvx1663", open.getStringExtra(WatchedNotifier.EXTRA_THREAD_ID))
        assertNotNull(thread("9q684xp3"))

        val summary = assertNotNull(groupSummary())
        assertEquals(listOf("Bike Riding (683)", "Electric Vehicles (17527)"), lines(summary))
        assertFalse(alerts(summary))

        // Nothing new: a dismissed thread stays dismissed, and the summary lists only what is left.
        manager.cancel("9yvx1663", WatchedNotifier.THREAD_NOTIFICATION_ID)
        enqueue(unreadTwo)
        notifier.check()
        assertNull(thread("9yvx1663"))
        assertFalse(alerts(assertNotNull(thread("9q684xp3"))))
        assertEquals(listOf("Electric Vehicles (17527)"), lines(assertNotNull(groupSummary())))
    }

    @Test
    fun `one per thread, only the thread with a new reply alerts`() = runTest {
        settings.update { it.copy(notifyStyle = NotifyStyle.PER_THREAD) }
        db.notifiedDao().put(listOf(NotifiedEntity("9q684xp3", OffsetDateTime.parse("2026-10-20T14:28:45+10:00").toInstant().toEpochMilli())))
        enqueue(unreadTwo)
        notifier.check()
        assertTrue(alerts(assertNotNull(thread("9yvx1663"))))
        assertNull(thread("9q684xp3"), "already notified, and not showing")
    }

    @Test
    fun `sync follows the app's unread list without alerting or coming back once dismissed`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        val unread = cachedUnread()

        // Read in the app: the summary drops it, silently.
        notifier.sync(unread.filter { it.id.value == "9q684xp3" })
        val n = assertNotNull(shown())
        assertEquals("Electric Vehicles", n.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertFalse(alerts(n))

        // Dismissed: a sync does not bring it back.
        manager.cancelAll()
        notifier.sync(unread)
        assertNull(shown())

        // Everything read: gone.
        enqueue(unreadTwo)
        notifier.check()
        notifier.sync(emptyList())
        assertNull(shown())
    }

    @Test
    fun `sync drops a thread read in the app from the per-thread notifications`() = runTest {
        settings.update { it.copy(notifyStyle = NotifyStyle.PER_THREAD) }
        enqueue(unreadTwo)
        notifier.check()
        notifier.sync(cachedUnread().filter { it.id.value == "9q684xp3" })
        assertNull(thread("9yvx1663"))
        assertFalse(alerts(assertNotNull(thread("9q684xp3"))))
        notifier.sync(emptyList())
        assertNull(thread("9q684xp3"))
        assertNull(groupSummary())
    }

    @Test
    fun `changing the style carries a showing notification over`() = runTest {
        enqueue(unreadTwo)
        notifier.check()
        settings.update { it.copy(notifyStyle = NotifyStyle.PER_THREAD) }
        notifier.sync(cachedUnread())
        assertNull(shown())
        assertFalse(alerts(assertNotNull(thread("9yvx1663"))))
        assertNotNull(thread("9q684xp3"))

        settings.update { it.copy(notifyStyle = NotifyStyle.SUMMARY) }
        notifier.sync(cachedUnread())
        assertNull(thread("9yvx1663"))
        assertNull(groupSummary())
        assertEquals(2, lines(assertNotNull(shown())).size)
    }

    @Test
    fun `the worker retries network failures, gives up on the rest, and records every run`() = runTest {
        val history = CheckHistory(context, db.checkLogDao(), settings, Clock.systemUTC())
        settings.update { it.copy(notifyIntervalMinutes = 240) }
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                WatchedCheckWorker(appContext, workerParameters, notifier, history)
        }
        // WorkManager counts a scheduled run as attempt 0; the test builder starts at 1 unless told.
        fun worker(attempt: Int = 0) = TestListenableWorkerBuilder<WatchedCheckWorker>(context).setWorkerFactory(factory).setRunAttemptCount(attempt).build()
        enqueue(unreadTwo)
        val ok = worker().doWork()
        assertIs<ListenableWorker.Result.Success>(ok)
        assertNotNull(shown())

        server.close()
        assertIs<ListenableWorker.Result.Retry>(worker().doWork())
        assertIs<ListenableWorker.Result.Success>(worker(attempt = 3).doWork(), "the next period tries again")

        // Newest first.
        val records = history.records.first()
        assertEquals(listOf(CheckResult.NETWORK, CheckResult.NETWORK, CheckResult.OK), records.map { it.outcome.result })
        assertEquals(listOf(3, 0, 0), records.map { it.attempt })
        assertEquals(CheckOutcome(CheckResult.OK, unreadThreads = 2, newThreads = 2), records.last().outcome)
        assertEquals("Network", records.first().outcome.detail)
        assertEquals(240, records.first().intervalMinutes)
    }

    @Test
    fun `a check says why it fetched nothing`() = runTest {
        assertEquals(CheckOutcome(CheckResult.OK, unreadThreads = 2, newThreads = 2), run { enqueue(unreadTwo); notifier.check() })
        assertEquals(CheckOutcome(CheckResult.OK, unreadThreads = 2, newThreads = 0), run { enqueue(unreadTwo); notifier.check() })
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(CheckResult.BLOCKED, notifier.check().result)
        settings.update { it.copy(watchedNotify = false) }
        assertEquals(CheckResult.OFF, notifier.check().result)
    }

    @Test
    fun `the history keeps the most recent checks`() = runTest {
        val history = CheckHistory(context, db.checkLogDao(), settings, Clock.systemUTC())
        repeat(CheckHistory.KEEP + 5) { i -> history.record(Instant.ofEpochSecond(i * 3600L), attempt = 0, CheckOutcome(CheckResult.OK, unreadThreads = i)) }
        val records = history.records.first()
        assertEquals(CheckHistory.KEEP, records.size)
        assertEquals(CheckHistory.KEEP + 4, records.first().outcome.unreadThreads)
        assertEquals(5, records.last().outcome.unreadThreads)
    }
}
