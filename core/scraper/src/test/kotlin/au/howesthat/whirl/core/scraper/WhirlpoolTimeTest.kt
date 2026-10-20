package au.howesthat.whirl.core.scraper

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WhirlpoolTimeTest {

    // Tuesday 2026-10-20, 8 pm in Sydney (AEDT, +11:00): when the fixtures were captured.
    private val now = ZonedDateTime.of(2026, 10, 20, 20, 0, 0, 0, WhirlpoolTime.SYDNEY)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, WhirlpoolTime.SYDNEY)

    @Test
    fun absolute() {
        assertEquals(LocalDateTime.of(2025, 6, 23, 11, 11), WhirlpoolTime.parseAbsolute("2025-Jun-23, 11:11 am"))
        assertEquals(LocalDateTime.of(2025, 11, 1, 0, 34), WhirlpoolTime.parseAbsolute("2025-Nov-1, 12:34 am"))
        assertEquals(LocalDateTime.of(2026, 3, 14, 16, 46), WhirlpoolTime.parseAbsolute("2026-Mar-14, 4:46 pm"))
        assertEquals(LocalDateTime.of(2017, 12, 1, 0, 0), WhirlpoolTime.parseAbsolute("2017-Dec-1"))
        assertEquals(LocalDateTime.of(2008, 6, 24, 13, 15), WhirlpoolTime.parseAbsolute("2008-Jun-24, 1:15 pm AEST"))
        assertNull(WhirlpoolTime.parseAbsolute("moments later"))
    }

    @Test
    fun `day names relative to now`() {
        assertEquals(at(2026, 10, 20, 16, 47), WhirlpoolTime.parse("Today at 4:47 pm", now))
        // Same post as the API's LAST_DATE 2026-10-19T20:39:50+1000 (21:39 AEDT).
        assertEquals(at(2026, 10, 19, 21, 39), WhirlpoolTime.parse("Yesterday at 9:39 pm", now))
        assertEquals(
            ZonedDateTime.parse("2026-10-19T20:39:00+10:00").toInstant(),
            WhirlpoolTime.parse("Yesterday at 9:39 pm", now)!!.toInstant(),
        )
        // Weekday names cover the days before yesterday; "Sunday at 8:20 am" was shown alongside "2d".
        assertEquals(at(2026, 10, 18, 8, 20), WhirlpoolTime.parse("Sunday at 8:20 am", now))
        assertEquals(at(2026, 10, 12, 14, 33), WhirlpoolTime.parse("Monday at 2:33 pm", now))
    }

    @Test
    fun `relative ages`() {
        assertEquals(at(2026, 10, 20, 19, 27), WhirlpoolTime.parse("33 minutes ago", now))
        assertEquals(at(2026, 10, 20, 19, 27), WhirlpoolTime.parse("33m", now))
        assertEquals(at(2026, 10, 19, 22, 0), WhirlpoolTime.parse("22h", now))
        assertEquals(at(2026, 10, 18, 20, 0), WhirlpoolTime.parse("2d", now))
        assertEquals(at(2026, 7, 20, 20, 0), WhirlpoolTime.parse("3 months ago", now))
        assertNull(WhirlpoolTime.parse("whenever", now))
    }
}
