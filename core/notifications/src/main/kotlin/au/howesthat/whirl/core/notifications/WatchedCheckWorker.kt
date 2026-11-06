package au.howesthat.whirl.core.notifications

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.model.WhirlpoolError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * The watched-thread check, started by [CheckAlarm] or the periodic fallback. A run too soon after the last check is
 * skipped ([tooSoon]) and not recorded. Transient failures retry with WorkManager's backoff; others wait for the next
 * alarm. Every run, including one Android stops part way, goes into [CheckHistory] and sets the alarm for one interval on.
 */
@HiltWorker
class WatchedCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifier: WatchedNotifier,
    private val history: CheckHistory,
    private val alarm: CheckAlarm,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val started = history.now()
        val s = settings.settings.first()
        // Retries are never skipped: their failed attempt is the last check.
        if (runAttemptCount == 0 && tooSoon(alarm.lastCheck(), started.toEpochMilli(), s.notifyIntervalMinutes)) {
            rearm()
            return Result.success()
        }
        val (outcome, result) = try {
            notifier.check() to Result.success()
        } catch (e: CancellationException) {
            // The stop reason is reported from Android 12.
            val reason = if (Build.VERSION.SDK_INT >= 31) stopReasonName(stopReason) else null
            withContext(NonCancellable) {
                history.record(started, runAttemptCount, CheckOutcome(CheckResult.STOPPED, detail = reason))
                rearm()
            }
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
        rearm()
        return result
    }

    /** Sets the alarm for an interval after the last check, unless the alerts were turned off meanwhile. */
    private suspend fun rearm() {
        val s = settings.settings.first()
        if (s.watchedNotify && settings.isSetupDone.first()) alarm.arm(s.notifyIntervalMinutes)
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
