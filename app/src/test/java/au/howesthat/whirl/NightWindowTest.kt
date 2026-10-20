package au.howesthat.whirl

import org.junit.Test
import java.time.LocalTime
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
}
