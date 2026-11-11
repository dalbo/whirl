package au.howesthat.whirl

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app has asked for the notification permission outside setup. Shared prefs on purpose: the backup rules
 * include only the DataStore files, so this is never restored, and a new phone (where Android restores settings and
 * key but not the permission, and setup is skipped) asks once (docs/plans/settings-backup.md, step 4).
 */
@Singleton
class NotificationPrompt @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val asked: Boolean get() = prefs.getBoolean(ASKED, false)

    fun markAsked() = prefs.edit { putBoolean(ASKED, true) }

    private companion object {
        const val PREFS = "prompts"
        const val ASKED = "notification_permission_asked"
    }
}
