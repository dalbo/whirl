package au.howesthat.whirl

import org.junit.Test
import java.time.LocalTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NightWindowTest {
    private fun t(s: String) = LocalTime.parse(s)

    @Test
    fun `window that wraps past midnight`() {
        assertTrue(isNight(t("23:00"), t("22:00"), t("07:00")))
        assertTrue(isNight(t("03:00"), t("22:00"), t("07:00")))
        assertFalse(isNight(t("07:00"), t("22:00"), t("07:00")))
        assertFalse(isNight(t("12:00"), t("22:00"), t("07:00")))
    }

    @Test
    fun `window within one day`() {
        assertTrue(isNight(t("01:00"), t("00:00"), t("06:00")))
        assertFalse(isNight(t("06:00"), t("00:00"), t("06:00")))
    }

    @Test
    fun `next edge`() {
        assertEquals(60_500.milliseconds, untilNextEdge(t("21:59"), t("22:00"), t("07:00")))
        assertEquals(9.hours + 500.milliseconds, untilNextEdge(t("22:00"), t("22:00"), t("07:00")), "at the start, the end is next")
        assertEquals(1.hours + 500.milliseconds, untilNextEdge(t("06:00"), t("22:00"), t("07:00")))
        assertEquals(15.hours + 500.milliseconds, untilNextEdge(t("07:00"), t("22:00"), t("07:00")))
        assertEquals(24.hours + 500.milliseconds, untilNextEdge(t("10:00"), t("10:00"), t("10:00")), "an empty window still wakes daily")
    }
}
