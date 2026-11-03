package au.howesthat.whirl.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
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
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.time.Duration

/** Starts the backstop that sends queued watch actions when the app is not there to (see [WatchedRepository]). */
interface WatchSendScheduler {
    /**
     * Makes sure a send runs after [delay] once there is a network. Work already waiting or running is kept, never
     * replaced: replacing cancels a running send part way, which could send an action twice.
     */
    fun schedule(delay: Duration)

    fun cancel()

    /** For tests: schedules nothing. */
    object None : WatchSendScheduler {
        override fun schedule(delay: Duration) = Unit
        override fun cancel() = Unit
    }
}

internal class WorkWatchSendScheduler @Inject constructor(@ApplicationContext private val context: Context) : WatchSendScheduler {
    override fun schedule(delay: Duration) {
        val request = OneTimeWorkRequestBuilder<WatchSendWorker>()
            .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "watch-send"
    }
}

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
