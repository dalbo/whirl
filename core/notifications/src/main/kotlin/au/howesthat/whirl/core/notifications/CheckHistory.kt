package au.howesthat.whirl.core.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.WorkInfo
import androidx.work.WorkManager
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.database.CheckLogDao
import au.howesthat.whirl.core.database.CheckLogEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** How a background check ended. */
enum class CheckResult {
    /** Fetched the watched threads; [CheckOutcome.newThreads] above zero means it alerted. */
    OK,
    /** Signed out or watched-thread notifications off: nothing fetched. */
    OFF,
    /** Notifications not allowed: nothing fetched, so the threads still alert once they are. */
    BLOCKED,
    NETWORK,
    MAINTENANCE,
    RATE_LIMITED,
    /** Any other failure; [CheckOutcome.detail] names it. */
    ERROR,
    /** Android stopped the check part way; [CheckOutcome.detail] has the stop reason. */
    STOPPED,
}

data class CheckOutcome(
    val result: CheckResult,
    val unreadThreads: Int? = null,
    val newThreads: Int? = null,
    val detail: String? = null,
)

enum class NetworkKind { NONE, WIFI, MOBILE, ETHERNET, OTHER }

/** What can hold back or hide a check, read from the system. */
data class DeviceState(
    /** A `UsageStatsManager` bucket; null before Android 9. */
    val standbyBucket: Int?,
    val powerSave: Boolean,
    val deviceIdle: Boolean,
    val network: NetworkKind,
    /** False when Whirl is exempt from battery optimisation ("Unrestricted"). */
    val batteryOptimised: Boolean,
    /** App info › Battery › Restricted (Android 9+). */
    val backgroundRestricted: Boolean,
    /** Data Saver is on and Whirl is not allowed unrestricted data. */
    val dataSaver: Boolean,
    val notificationsAllowed: Boolean,
    val channelEnabled: Boolean,
)

/** One recorded check. */
data class CheckRecord(
    val startedAt: Instant,
    val durationMs: Long,
    /** 0 for a scheduled run, then 1, 2… for WorkManager's retries. */
    val attempt: Int,
    val intervalMinutes: Int,
    val outcome: CheckOutcome,
    val standbyBucket: Int?,
    val powerSave: Boolean,
    val deviceIdle: Boolean,
    val network: NetworkKind,
)

/**
 * The last [KEEP] background checks and the system state that decides when they run, for Settings › Check history:
 * a tester's report then shows whether a late notification was a check that ran late (Doze, standby bucket, battery
 * settings) or one that ran and found nothing new.
 */
@Singleton
class CheckHistory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CheckLogDao,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    val records: Flow<List<CheckRecord>> = dao.observe().map { rows -> rows.map { it.toRecord() } }

    /** The scheduled check, or null when none is scheduled. */
    val work: Flow<WorkInfo?>
        get() = WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(NotificationScheduler.WORK_NAME)
            .map { infos -> infos.firstOrNull { !it.state.isFinished } }

    fun now(): Instant = clock.instant()

    /** Records one check that started at [startedAt]. */
    suspend fun record(startedAt: Instant, attempt: Int, outcome: CheckOutcome) {
        val device = device()
        dao.add(
            CheckLogEntity(
                startedAt = startedAt.toEpochMilli(),
                durationMs = clock.millis() - startedAt.toEpochMilli(),
                attempt = attempt,
                intervalMinutes = settings.settings.first().notifyIntervalMinutes,
                result = outcome.result.name,
                detail = outcome.detail,
                unreadThreads = outcome.unreadThreads,
                newThreads = outcome.newThreads,
                standbyBucket = device.standbyBucket,
                powerSave = device.powerSave,
                deviceIdle = device.deviceIdle,
                network = device.network.name,
            ),
            KEEP,
        )
    }

    fun device(): DeviceState {
        val power = context.getSystemService(PowerManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val notifications = NotificationManagerCompat.from(context)
        return DeviceState(
            standbyBucket = if (Build.VERSION.SDK_INT >= 28) context.getSystemService(UsageStatsManager::class.java).appStandbyBucket else null,
            powerSave = power.isPowerSaveMode,
            deviceIdle = power.isDeviceIdleMode,
            network = network(connectivity),
            batteryOptimised = !power.isIgnoringBatteryOptimizations(context.packageName),
            backgroundRestricted = Build.VERSION.SDK_INT >= 28 && context.getSystemService(ActivityManager::class.java).isBackgroundRestricted,
            dataSaver = connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            notificationsAllowed = canPostNotifications(context),
            // Not created yet means nobody has turned it off.
            channelEnabled = notifications.getNotificationChannelCompat(WatchedNotifier.CHANNEL_WATCHED)
                ?.let { it.importance != NotificationManagerCompat.IMPORTANCE_NONE } ?: true,
        )
    }

    private fun network(connectivity: ConnectivityManager): NetworkKind {
        val caps = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return NetworkKind.NONE
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkKind.MOBILE
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.ETHERNET
            else -> NetworkKind.OTHER
        }
    }

    private fun CheckLogEntity.toRecord() = CheckRecord(
        startedAt = Instant.ofEpochMilli(startedAt),
        durationMs = durationMs,
        attempt = attempt,
        intervalMinutes = intervalMinutes,
        outcome = CheckOutcome(
            result = CheckResult.entries.firstOrNull { it.name == result } ?: CheckResult.ERROR,
            unreadThreads = unreadThreads,
            newThreads = newThreads,
            detail = detail,
        ),
        standbyBucket = standbyBucket,
        powerSave = powerSave,
        deviceIdle = deviceIdle,
        network = NetworkKind.entries.firstOrNull { it.name == network } ?: NetworkKind.OTHER,
    )

    companion object {
        const val KEEP = 20
    }
}

/** POST_NOTIFICATIONS granted (Android 13+) and notifications not turned off for the app. */
internal fun canPostNotifications(context: Context): Boolean =
    (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

/** A WorkManager stop reason as a short name, or null when the work was not stopped. */
fun stopReasonName(reason: Int): String? = when (reason) {
    WorkInfo.STOP_REASON_NOT_STOPPED -> null
    WorkInfo.STOP_REASON_CANCELLED_BY_APP -> "cancelled by app"
    WorkInfo.STOP_REASON_PREEMPT -> "preempted"
    WorkInfo.STOP_REASON_TIMEOUT -> "timed out"
    WorkInfo.STOP_REASON_DEVICE_STATE -> "device state"
    WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY -> "lost network"
    WorkInfo.STOP_REASON_QUOTA -> "quota"
    WorkInfo.STOP_REASON_BACKGROUND_RESTRICTION -> "background restriction"
    WorkInfo.STOP_REASON_APP_STANDBY -> "app standby"
    WorkInfo.STOP_REASON_USER -> "user"
    WorkInfo.STOP_REASON_SYSTEM_PROCESSING -> "system processing"
    else -> "reason $reason"
}

/** A `UsageStatsManager` standby bucket as a short name. */
fun standbyBucketName(bucket: Int?): String = when (bucket) {
    null -> "n/a"
    UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
    UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
    UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
    UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
    UsageStatsManager.STANDBY_BUCKET_RESTRICTED -> "restricted"
    5 -> "exempted"
    else -> "bucket $bucket"
}
