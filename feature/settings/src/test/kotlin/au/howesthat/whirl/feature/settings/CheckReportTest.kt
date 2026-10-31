package au.howesthat.whirl.feature.settings

import android.app.usage.UsageStatsManager
import au.howesthat.whirl.core.notifications.CheckOutcome
import au.howesthat.whirl.core.notifications.CheckRecord
import au.howesthat.whirl.core.notifications.CheckResult
import au.howesthat.whirl.core.notifications.DeviceState
import au.howesthat.whirl.core.notifications.NetworkKind
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals

class CheckReportTest {

    private fun record(at: String, outcome: CheckOutcome, bucket: Int? = UsageStatsManager.STANDBY_BUCKET_ACTIVE, attempt: Int = 0, idle: Boolean = false) =
        CheckRecord(Instant.parse(at), 1200, attempt, 60, outcome, bucket, powerSave = false, deviceIdle = idle, network = NetworkKind.WIFI)

    private val device = DeviceState(
        standbyBucket = UsageStatsManager.STANDBY_BUCKET_RARE,
        powerSave = false,
        deviceIdle = false,
        network = NetworkKind.WIFI,
        batteryOptimised = true,
        backgroundRestricted = false,
        dataSaver = false,
        notificationsAllowed = true,
        channelEnabled = false,
    )

    private val records = listOf(
        record("2026-10-31T09:10:00Z", CheckOutcome(CheckResult.NETWORK, detail = "Network"), attempt = 1),
        record("2026-10-31T09:02:00Z", CheckOutcome(CheckResult.OK, unreadThreads = 2, newThreads = 1), bucket = UsageStatsManager.STANDBY_BUCKET_RARE, idle = true),
        record("2026-10-31T01:00:00Z", CheckOutcome(CheckResult.OK, unreadThreads = 0, newThreads = 0)),
    )

    @Test
    fun `gaps are measured from the check before`() {
        assertEquals(listOf(Duration.ofMinutes(8), Duration.ofMinutes(482), null), gaps(records))
        assertEquals("8m", shortDuration(Duration.ofMinutes(8)))
        assertEquals("8h02m", shortDuration(Duration.ofMinutes(482)))
    }

    @Test
    fun `the report lists the state and every check`() {
        val state = CheckHistoryState(records, scheduled = true, intervalMinutes = 60, nextRun = Instant.parse("2026-10-31T10:05:00Z"), lastStop = "app standby", device = device)
        val text = checkReport(state, app = "Whirl 1.4.2", device = "Android 16 (API 36), Google Pixel 9", zone = ZoneOffset.UTC)
        assertEquals(
            """
            Whirl 1.4.2 check history
            Android 16 (API 36), Google Pixel 9
            Checks every 60m, next Sat 31 Oct 10:05
            Last stopped by Android: app standby
            Notifications allowed, channel OFF
            Battery optimised, standby rare, battery saver off, data saver off

            Sat 31 Oct 09:10  +8m  network (Network)  retry 1  active  wifi  1.2s
            Sat 31 Oct 09:02  +8h02m  ok 2 unread 1 new (alerted)  rare  wifi  idle  1.2s
            Sat 31 Oct 01:00  -  ok 0 unread 0 new  active  wifi  1.2s
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun `an empty history says so`() {
        val state = CheckHistoryState(emptyList(), scheduled = false, intervalMinutes = 60, nextRun = null, lastStop = null, device = device)
        val text = checkReport(state, app = "Whirl", device = "Android", zone = ZoneOffset.UTC)
        assertEquals(true, "Checks not scheduled" in text && text.endsWith("No checks recorded yet"))
    }
}
