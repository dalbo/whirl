package au.howesthat.whirl.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import au.howesthat.whirl.core.model.WhirlpoolError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** The periodic watched-thread check. Transient failures retry with WorkManager's backoff; others wait for the next period. */
@HiltWorker
class WatchedCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: WatchedNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        notifier.check()
        Result.success()
    } catch (e: WhirlpoolError) {
        when (e) {
            is WhirlpoolError.Network, is WhirlpoolError.Maintenance, is WhirlpoolError.RateLimited ->
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            else -> Result.success()
        }
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
