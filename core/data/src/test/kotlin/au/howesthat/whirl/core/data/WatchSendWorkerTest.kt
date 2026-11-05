package au.howesthat.whirl.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import au.howesthat.whirl.core.database.PendingWatchEntity
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertIs

@RunWith(RobolectricTestRunner::class)
class WatchSendWorkerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(context, WhirlDatabase::class.java).allowMainThreadQueries().build()
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val api = WhirlpoolApi(WhirlpoolHttp.client("Whirl/test"), { "k" }, server.url("/api/"), RetryPolicy.NONE)
    private val watched =
        WatchedRepository(api, db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), WatchSendScheduler.None, Clock.systemUTC(), appScope)

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            WatchSendWorker(appContext, workerParameters, watched)
    }

    private fun worker() = TestListenableWorkerBuilder<WatchSendWorker>(context).setWorkerFactory(factory).build()

    @After
    fun tearDown() {
        appScope.cancel()
        server.close()
        db.close()
    }

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse.Builder().code(code).body(body).build())

    @Test
    fun `sends what an earlier process left, and asks for a retry when it cannot`() = runTest {
        val queue = db.pendingWatchDao()
        val now = System.currentTimeMillis()
        queue.insert(PendingWatchEntity(action = "UNWATCH", threadIds = "9yvx1663", sendAfter = now, createdAt = now))
        // Due in a moment: the worker waits for it rather than leaving it.
        queue.insert(PendingWatchEntity(action = "MARK_READ", threadIds = "9q684xp3", sendAfter = now + 300, createdAt = now))

        enqueue("", code = 429)
        assertIs<ListenableWorker.Result.Retry>(worker().doWork())
        assertEquals(2, queue.all().size)

        val list = File(System.getProperty("whirl.fixtures"), "api/api_watched_mode1.json").readText()
        enqueue(list)
        enqueue(list)
        assertIs<ListenableWorker.Result.Success>(worker().doWork())
        assertEquals(emptyList(), queue.all())
        server.takeRequest()
        assertEquals("9yvx1663", server.takeRequest().url.queryParameter("watchedremove"))
        assertEquals("9q684xp3", server.takeRequest().url.queryParameter("watchedread"))
    }
}
