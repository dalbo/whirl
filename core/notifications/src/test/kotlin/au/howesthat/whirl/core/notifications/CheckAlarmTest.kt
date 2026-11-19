package au.howesthat.whirl.core.notifications

import android.app.AlarmManager
import android.app.Application
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.CheckLogEntity
import au.howesthat.whirl.core.database.WhirlDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock tests can move. */
class TestClock(var now: Instant = Instant.parse("2026-11-05T00:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?) = this
    override fun instant(): Instant = now
    fun advanceMinutes(minutes: Long) { now = now.plusSeconds(minutes * 60) }
}

private fun min(m: Long) = TimeUnit.MINUTES.toMillis(m)

class CheckTimingTest {
    private val t = 1_800_000_000_000

    @Test
    fun `the next check is an interval after the last, at least 15 minutes, and never in the past`() {
        assertEquals(t + min(30), checkDueAt(last = t, now = t + min(2), intervalMinutes = 30))
        assertEquals(t + min(15), checkDueAt(last = t, now = t, intervalMinutes = 5))
        assertEquals(t + min(30), checkDueAt(last = null, now = t, intervalMinutes = 30), "no check yet: an interval from now")
        assertEquals(t + min(181), checkDueAt(last = t, now = t + min(180), intervalMinutes = 30), "overdue: a minute from now")
    }

    @Test
    fun `a run is too soon within an interval less the flex of the last check`() {
        assertFalse(tooSoon(last = null, now = t, intervalMinutes = 30))
        assertTrue(tooSoon(last = t, now = t + min(3), intervalMinutes = 30), "the alarm and the fallback both fell due")
        assertTrue(tooSoon(last = t, now = t + min(24), intervalMinutes = 30))
        assertFalse(tooSoon(last = t, now = t + min(25), intervalMinutes = 30), "the fallback's flex window")
        assertFalse(tooSoon(last = t, now = t + min(30), intervalMinutes = 30), "the alarm, on time")
        assertFalse(tooSoon(last = t, now = t + min(72), intervalMinutes = 30), "late")
    }
}

@RunWith(RobolectricTestRunner::class)
class CheckAlarmTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, WhirlDatabase::class.java).allowMainThreadQueries().build()
    private val clock = TestClock()
    private val alarm = CheckAlarm(context, db.checkLogDao(), clock)
    private val shadow = shadowOf(context.getSystemService(AlarmManager::class.java))

    @After
    fun tearDown() = db.close()

    private suspend fun checkedAt(at: Instant) =
        db.checkLogDao().add(CheckLogEntity(startedAt = at.toEpochMilli(), durationMs = 300, attempt = 0, intervalMinutes = 30, result = "OK", detail = null, unreadThreads = 0, newThreads = 0, standbyBucket = 10, powerSave = false, deviceIdle = false, network = "WIFI"), 20)

    /** Minutes from now until the scheduled alarm. */
    private fun alarmInMinutes(): Long {
        val next = assertNotNull(shadow.peekNextScheduledAlarm())
        assertEquals(AlarmManager.ELAPSED_REALTIME_WAKEUP, next.getType())
        assertTrue(next.isAllowWhileIdle, "fires during Doze")
        return TimeUnit.MILLISECONDS.toMinutes(next.triggerAtMs - SystemClock.elapsedRealtime())
    }

    @Test
    fun `armed an interval after the last check, kept on app start, cancelled`() = runTest {
        checkedAt(clock.now)
        clock.advanceMinutes(10)
        alarm.arm(30)
        assertEquals(20, alarmInMinutes())
        assertEquals(clock.now.plusSeconds(20 * 60), alarm.next())

        // The alarm's own receiver starts the app: ensure must not move the alarm it set.
        clock.advanceMinutes(5)
        alarm.armAt(clock.now.toEpochMilli() + min(30), 30)
        alarm.ensure(30)
        assertEquals(30, alarmInMinutes())

        // A new interval re-arms from the last check.
        alarm.ensure(60)
        assertEquals(45, alarmInMinutes())

        alarm.cancel()
        assertNull(shadow.peekNextScheduledAlarm())
        assertNull(alarm.next())
    }

    @Test
    fun `ensure arms when the stored alarm is past`() = runTest {
        alarm.armAt(clock.now.toEpochMilli() + min(30), 30)
        clock.advanceMinutes(40)
        alarm.ensure(30)
        assertEquals(30, alarmInMinutes(), "no check yet: an interval from now")
    }
}
