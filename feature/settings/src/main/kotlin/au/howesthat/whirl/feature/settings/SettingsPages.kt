package au.howesthat.whirl.feature.settings

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The pages the Settings list opens, in its order. */
@Serializable
enum class SettingsPage { ACCOUNT, GENERAL, NOTIFICATIONS, THREADS, RECENT, WATCHED, BACKUP }

/** One page of Settings. */
@Serializable
data class SettingsPageRoute(val page: SettingsPage) : NavKey

/** What the Backup page says about this phone (docs/plans/settings-backup.md; the rules are in `:app`'s res/xml). */
enum class BackupNote {
    /** Settings, favourites and the key are backed up (the key end-to-end encrypted). */
    ALL,
    /** Android 12+ with no screen lock: cloud backup needs end-to-end encryption, so nothing goes up. */
    NOTHING_WITHOUT_LOCK,
    /** Android 9–11 with no screen lock: settings and favourites go up, the key does not. */
    NO_KEY_WITHOUT_LOCK,
    /** Android 8: settings and favourites go up, the key never (it cannot be required to be encrypted). */
    NO_KEY_ON_THIS_VERSION,
}

fun backupNote(sdk: Int, hasScreenLock: Boolean): BackupNote = when {
    sdk < 28 -> BackupNote.NO_KEY_ON_THIS_VERSION
    hasScreenLock -> BackupNote.ALL
    sdk >= 31 -> BackupNote.NOTHING_WITHOUT_LOCK
    else -> BackupNote.NO_KEY_WITHOUT_LOCK
}
