package au.howesthat.whirl.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import au.howesthat.whirl.core.database.CheckLogDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The check's clock while the phone dozes. JobScheduler holds periodic work until Doze's next maintenance window (an
 * hour, then longer), so a check that fell due mid-window ran only when the phone was next picked up. An
 * allow-while-idle alarm fires during Doze, and its receiver ([CheckAlarmReceiver]) starts the check as expedited work,
 * which Doze and battery saver do not hold back (Android 12+; before that it is ordinary work).
 *
 * The alarm is always set for one interval after the last check ([checkDueAt]), so setting it again, on every app start
 * and after every check, never moves it. The periodic work in [NotificationScheduler] stays as the fallback (after a
 * reboot, which clears alarms, or when Android rations them); a run that comes too soon after another is skipped
 * ([tooSoon]), so the two together check about once an interval.
 */
@Singleton
class CheckAlarm @Inject constructor(
    @ApplicationContext private val context: Context,
    private val log: CheckLogDao,
    private val clock: Clock,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The last check's start, in epoch milliseconds. */
    suspend fun lastCheck(): Long? = log.latestStartedAt()

    /** Sets the alarm for one interval after the last check. */
    suspend fun arm(intervalMinutes: Int) = armAt(checkDueAt(lastCheck(), clock.millis(), intervalMinutes), intervalMinutes)

    /**
     * [arm], unless an alarm for [intervalMinutes] is already waiting. For app starts: the alarm's own receiver starts
     * the app, and re-arming from the last check then would set it for a minute away instead of an interval.
     */
    suspend fun ensure(intervalMinutes: Int) {
        val pending = prefs.getLong(KEY_NEXT, 0) > clock.millis() &&
            prefs.getInt(KEY_INTERVAL, 0) == intervalMinutes &&
            prefs.getInt(KEY_BOOT, -1) == bootCount()
        if (!pending) arm(intervalMinutes)
    }

    /** Sets the alarm for [atMillis] (epoch milliseconds), replacing any other. */
    fun armAt(atMillis: Long, intervalMinutes: Int) {
        val delay = (atMillis - clock.millis()).coerceAtLeast(0)
        // Elapsed time, so a change of the wall clock does not move it. Inexact: Android may deliver it a little late.
        alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay, pending(intervalMinutes))
        prefs.edit { putLong(KEY_NEXT, atMillis).putInt(KEY_INTERVAL, intervalMinutes).putInt(KEY_BOOT, bootCount()) }
    }

    fun cancel() {
        alarms.cancel(pending(0))
        prefs.edit { clear() }
    }

    /** When the alarm is due, or null when none is set (a reboot clears alarms). */
    fun next(): Instant? = prefs.getLong(KEY_NEXT, 0).takeIf { it > 0 && prefs.getInt(KEY_BOOT, -1) == bootCount() }?.let(Instant::ofEpochMilli)

    /** Alarms do not survive a reboot; the boot count says whether the stored one is from this boot. */
    private fun bootCount(): Int = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    /** PendingIntents match without their extras, so [cancel] finds the alarm whatever its interval. */
    private fun pending(intervalMinutes: Int): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, CheckAlarmReceiver::class.java).setAction(ACTION_CHECK).putExtra(EXTRA_INTERVAL, intervalMinutes),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ACTION_CHECK = "au.howesthat.whirl.action.CHECK_WATCHED"
        const val EXTRA_INTERVAL = "au.howesthat.whirl.extra.INTERVAL_MINUTES"
        /** The unique one-off check an alarm starts. */
        const val WORK_NAME = "watched-check-alarm"
        private const val PREFS = "check_alarm"
        private const val KEY_NEXT = "next_at"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_BOOT = "boot_count"

        /** Starts one check now, as expedited work where Android supports it, unless one is already waiting. */
        fun enqueueCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<WatchedCheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                // Before Android 12 expedited work needs a foreground service, which Whirl does not declare.
                .apply { if (Build.VERSION.SDK_INT >= 31) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** The check interval in milliseconds (WorkManager's minimum is 15 minutes, and the alarm keeps to it too). */
fun intervalMillis(intervalMinutes: Int): Long = TimeUnit.MINUTES.toMillis(maxOf(intervalMinutes, 15).toLong())

/** When the next check is due: one interval after [last] (or from [now] if none), and never sooner than a minute away. */
fun checkDueAt(last: Long?, now: Long, intervalMinutes: Int): Long =
    maxOf(now + TimeUnit.MINUTES.toMillis(1), (last ?: now) + intervalMillis(intervalMinutes))

/**
 * A run [now] is too soon when the last check started less than an interval ago, less the periodic work's flex: the
 * periodic work and the alarm then both fell due, and one check is enough. An alarm is never early, so its runs pass.
 */
fun tooSoon(last: Long?, now: Long, intervalMinutes: Int): Boolean =
    last != null && now - last < intervalMillis(intervalMinutes) - TimeUnit.MINUTES.toMillis(NotificationScheduler.FLEX_MINUTES)
