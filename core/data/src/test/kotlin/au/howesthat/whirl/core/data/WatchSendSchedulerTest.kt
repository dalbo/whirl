package au.howesthat.whirl.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.test.runTest
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
    fun `a run still to start is kept, so the chain does not grow`() = runTest {
        val scheduler = WorkWatchSendScheduler(context)
        repeat(3) { scheduler.schedule(3.minutes) }
        scheduler.schedule(Duration.ZERO)
        val runs = WorkManager.getInstance(context).getWorkInfosForUniqueWork("watch-send").get()
        assertEquals(listOf(WorkInfo.State.ENQUEUED), runs.map { it.state })
    }

    @Test
    fun `a run that is going gets one queued after it, which is then kept`() {
        // A run finishing having found nothing to send would drop an action queued now (KEEP).
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policyFor(listOf(WorkInfo.State.RUNNING)))
        assertEquals(ExistingWorkPolicy.KEEP, policyFor(listOf(WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)))
        assertEquals(ExistingWorkPolicy.KEEP, policyFor(listOf(WorkInfo.State.ENQUEUED)))
        assertEquals(ExistingWorkPolicy.APPEND_OR_REPLACE, policyFor(listOf(WorkInfo.State.SUCCEEDED)))
    }
}
