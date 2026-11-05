package au.howesthat.whirl.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class WatchSendSchedulerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
    }

    @Test
    fun `a send scheduled while one is waiting or running is queued after it, not dropped`() {
        val scheduler = WorkWatchSendScheduler(context)
        scheduler.schedule(3.minutes)
        // An action queued while that run is going, perhaps after it found the queue empty.
        scheduler.schedule(Duration.ZERO)
        val runs = WorkManager.getInstance(context).getWorkInfosForUniqueWork("watch-send").get()
        assertEquals(listOf(WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED), runs.map { it.state }.sortedBy { it.ordinal })
    }
}
