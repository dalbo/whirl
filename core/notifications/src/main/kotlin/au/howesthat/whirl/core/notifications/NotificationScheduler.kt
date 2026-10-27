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
 * Keeps the periodic check in step with the settings: scheduled while signed in with watched-thread alerts on, at
 * the chosen interval (WorkManager's minimum is 15 minutes), cancelled otherwise. WorkManager re-arms it after a reboot.
 */
@Singleton
class NotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val notifier: WatchedNotifier,
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
    internal fun apply(intervalMinutes: Int?) {
        val work = WorkManager.getInstance(context)
        if (intervalMinutes == null) {
            work.cancelUniqueWork(WORK_NAME)
            notifier.cancel()
            return
        }
        val request = PeriodicWorkRequestBuilder<WatchedCheckWorker>(maxOf(intervalMinutes, 15).toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UPDATE keeps the running schedule and changes only the interval.
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    companion object {
        const val WORK_NAME = "watched-check"
    }
}
