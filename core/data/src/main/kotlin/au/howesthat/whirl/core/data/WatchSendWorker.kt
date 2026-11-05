package au.howesthat.whirl.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration

/** Starts the backstop that sends queued watch actions when the app is not there to (see [WatchedRepository]). */
interface WatchSendScheduler {
    /**
     * Makes sure a send runs after [delay] once there is a network. Work already waiting or running is kept, never
     * replaced: replacing cancels a running send part way, which could send an action twice ([policyFor]).
     */
    suspend fun schedule(delay: Duration)

    fun cancel()

    /** For tests: schedules nothing. */
    object None : WatchSendScheduler {
        override suspend fun schedule(delay: Duration) = Unit
        override fun cancel() = Unit
    }
}

internal class WorkWatchSendScheduler @Inject constructor(@ApplicationContext private val context: Context) : WatchSendScheduler {
    override suspend fun schedule(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<WatchSendWorker>()
            .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        val workManager = WorkManager.getInstance(context)
        val states = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).first().map { it.state }
        workManager.enqueueUniqueWork(WORK_NAME, policyFor(states), request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "watch-send"
    }
}

/**
 * How a new run joins the unique work, given its runs' [states]. One still to start will send what is queued now, so it
 * is kept. A running one may be finishing having found nothing to send, so the new run is queued after it (KEEP would
 * drop it). So the chain is never longer than a running send and one waiting.
 */
internal fun policyFor(states: List<WorkInfo.State>): ExistingWorkPolicy =
    if (states.any { it == WorkInfo.State.ENQUEUED || it == WorkInfo.State.BLOCKED }) ExistingWorkPolicy.KEEP
    else ExistingWorkPolicy.APPEND_OR_REPLACE

@Module
@InstallIn(SingletonComponent::class)
internal abstract class WatchSendModule {
    @Binds
    abstract fun scheduler(impl: WorkWatchSendScheduler): WatchSendScheduler
}

/**
 * Sends the queued watch actions: after the app was closed or lost the network during an Undo window, or after a
 * send that did not get through. Rows still in their Undo window are waited for here, so one run empties the queue.
 */
@HiltWorker
class WatchSendWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val watched: WatchedRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        while (true) {
            when (val outcome = watched.drain()) {
                Drain.Empty -> return Result.success()
                Drain.Retry -> return Result.retry()
                is Drain.Waiting -> delay(outcome.wait)
            }
        }
    }
}
