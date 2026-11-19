package au.howesthat.whirl.feature.settings

import au.howesthat.whirl.core.data.SETTING_KEYS
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class BackupLabelsTest {
    @Test
    fun `every setting has a label for an import's preview`() {
        assertEquals(SETTING_KEYS.toSet(), SETTING_LABELS.keys, "a new setting needs a row in SETTING_LABELS")
    }

    @Test
    fun `the export is named by date`() {
        assertEquals("whirl-settings-2026-11-19.json", settingsFileName(LocalDate.of(2026, 11, 19)))
    }
}
