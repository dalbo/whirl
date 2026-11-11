package au.howesthat.whirl.feature.settings

import org.junit.Test
import kotlin.test.assertEquals

/** Matches the backup rules: xml/ (API 26–27), xml-v28/ (28–30), data_extraction_rules.xml (31+). */
class BackupNoteTest {
    @Test
    fun `what is backed up, by Android version and screen lock`() {
        assertEquals(BackupNote.NO_KEY_ON_THIS_VERSION, backupNote(26, hasScreenLock = true))
        assertEquals(BackupNote.NO_KEY_ON_THIS_VERSION, backupNote(27, hasScreenLock = false))
        assertEquals(BackupNote.ALL, backupNote(28, hasScreenLock = true))
        assertEquals(BackupNote.NO_KEY_WITHOUT_LOCK, backupNote(30, hasScreenLock = false))
        assertEquals(BackupNote.ALL, backupNote(31, hasScreenLock = true))
        assertEquals(BackupNote.NOTHING_WITHOUT_LOCK, backupNote(31, hasScreenLock = false))
        assertEquals(BackupNote.NOTHING_WITHOUT_LOCK, backupNote(37, hasScreenLock = false))
    }
}
