package au.howesthat.whirl.core.notifications

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, WhirlDatabase::class.java).allowMainThreadQueries().build()
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings = SettingsRepository(context)
    private val watched = WatchedRepository(WhirlpoolApi(WhirlpoolHttp.client("t"), { null }), db.threadDao(), db.fetchLogDao(), Clock.systemUTC(), appScope)
    private val notifier = WatchedNotifier(context, settings, watched, db.notifiedDao())
    private val scheduler = NotificationScheduler(context, settings, notifier, appScope)

    @Before
    fun setUp() = WorkManagerTestInitHelper.initializeTestWorkManager(context)

    @After
    fun tearDown() {
        appScope.cancel()
        db.close()
    }

    private fun work(): List<WorkInfo> = WorkManager.getInstance(context).getWorkInfosForUniqueWork(NotificationScheduler.WORK_NAME).get()

    @Test
    fun `scheduled at the interval, never under 15 minutes, and cancelled`() {
        scheduler.apply(60)
        val info = work().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(TimeUnit.MINUTES.toMillis(60), info.periodicityInfo!!.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        assertEquals(TimeUnit.MINUTES.toMillis(NotificationScheduler.FLEX_MINUTES), info.periodicityInfo!!.flexIntervalMillis, "runs late in each period")

        scheduler.apply(240)
        val updated = work().single()
        assertEquals(info.id, updated.id, "the same work, updated in place")
        assertEquals(TimeUnit.MINUTES.toMillis(240), updated.periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(5)
        assertEquals(TimeUnit.MINUTES.toMillis(15), work().single().periodicityInfo!!.repeatIntervalMillis)

        scheduler.apply(null)
        assertTrue(work().all { it.state == WorkInfo.State.CANCELLED })
    }

    @Test
    fun `follows the settings`() = runTest {
        settings.signIn("k", Account(216903, "d@llin"))
        settings.setSetupDone()
        settings.update { it.copy(watchedNotify = true, notifyIntervalMinutes = 30) }
        scheduler.start()
        // The settings flow runs on a real scope, so wait in wall-clock time.
        suspend fun until(check: () -> Boolean) = withContext(Dispatchers.Default) { withTimeout(10_000) { while (!check()) delay(20) } }
        until { work().any { it.state == WorkInfo.State.ENQUEUED && it.periodicityInfo?.repeatIntervalMillis == TimeUnit.MINUTES.toMillis(30) } }
        settings.update { it.copy(watchedNotify = false) }
        until { work().all { it.state == WorkInfo.State.CANCELLED } }
    }
}
