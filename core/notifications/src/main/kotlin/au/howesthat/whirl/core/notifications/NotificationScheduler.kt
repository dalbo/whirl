package au.howesthat.whirl.core.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import au.howesthat.whirl.core.data.ApplicationScope
import au.howesthat.whirl.core.data.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the check in step with the settings: scheduled while signed in with watched-thread alerts on, at the chosen
 * interval (at least 15 minutes), cancelled otherwise. Two clocks start it: [CheckAlarm], which fires during Doze, and
 * periodic work as the fallback, which WorkManager re-arms after a reboot (and whose run sets the alarm again).
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val notifier: WatchedNotifier,
    private val alarm: CheckAlarm,
    @ApplicationScope private val scope: CoroutineScope,
) {
    /** Called once from `Application.onCreate`. */
    fun start() {
        notifier.createChannel()
        notifier.keepInSync(scope)
        scope.launch {
            combine(settings.settings, settings.isSetupDone) { s, done -> if (done && s.watchedNotify) s.notifyIntervalMinutes else null }
                .distinctUntilChanged()
                .collect(::apply)
        }
    }

    /** [intervalMinutes] null cancels the check and the notification. */
    internal suspend fun apply(intervalMinutes: Int?) {
        val work = WorkManager.getInstance(context)
        if (intervalMinutes == null) {
            alarm.cancel()
            work.cancelUniqueWork(WORK_NAME)
            work.cancelUniqueWork(CheckAlarm.WORK_NAME)
            notifier.cancel()
            return
        }
        alarm.ensure(intervalMinutes)
        // The flex window puts each run in the last minutes of its period, not anywhere in it, so checks are about
        // one interval apart when the phone is awake. Doze and standby buckets still defer them.
        val request = PeriodicWorkRequestBuilder<WatchedCheckWorker>(intervalMillis(intervalMinutes), TimeUnit.MILLISECONDS, FLEX_MINUTES, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE keeps the running schedule and changes only the interval.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "watched-check"
        /** WorkManager's minimum flex. */
        const val FLEX_MINUTES = 5L
    }
}
