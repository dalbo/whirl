package au.howesthat.whirl.core.notifications

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import au.howesthat.whirl.core.model.WhirlpoolError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * The periodic watched-thread check. Transient failures retry with WorkManager's backoff; others wait for the next
 * period. Every run, including one Android stops part way, goes into [CheckHistory].
 */
@HiltWorker
class WatchedCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: WatchedNotifier,
    private val history: CheckHistory,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val started = history.now()
        val (outcome, result) = try {
            notifier.check() to Result.success()
        } catch (e: CancellationException) {
            // The stop reason is reported from Android 12.
            val reason = if (Build.VERSION.SDK_INT >= 31) stopReasonName(stopReason) else null
            withContext(NonCancellable) { history.record(started, runAttemptCount, CheckOutcome(CheckResult.STOPPED, detail = reason)) }
            throw e
        } catch (e: WhirlpoolError) {
            val transient = when (e) {
                is WhirlpoolError.Network -> CheckResult.NETWORK
                is WhirlpoolError.Maintenance -> CheckResult.MAINTENANCE
                is WhirlpoolError.RateLimited -> CheckResult.RATE_LIMITED
                else -> null
            }
            val retry = transient != null && runAttemptCount < MAX_RETRIES
            CheckOutcome(transient ?: CheckResult.ERROR, detail = e::class.simpleName) to (if (retry) Result.retry() else Result.success())
        } catch (e: Exception) {
            CheckOutcome(CheckResult.ERROR, detail = e::class.simpleName) to Result.failure()
        }
        history.record(started, runAttemptCount, outcome)
        return result
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
