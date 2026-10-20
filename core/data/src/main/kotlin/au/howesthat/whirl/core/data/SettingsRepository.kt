package au.howesthat.whirl.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

// Lives in files/datastore/, which backup_rules.xml and data_extraction_rules.xml exclude: the API key never leaves the device.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Settings and the API key, in Preferences DataStore. The key is never logged; [apiKey] exists for the network layer
 * and nothing else should read it.
 */
@Singleton
class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.dataStore)

    private object Keys {
        val apiKey = stringPreferencesKey("api_key")
        val userId = longPreferencesKey("user_id")
        val userName = stringPreferencesKey("user_name")
        val setupDone = booleanPreferencesKey("setup_done")
        val homepage = stringPreferencesKey("homepage")
        val theme = stringPreferencesKey("theme")
        val nightStart = stringPreferencesKey("night_start")
        val nightEnd = stringPreferencesKey("night_end")
        val postFontSize = stringPreferencesKey("post_font_size")
        val watchedNotify = booleanPreferencesKey("watched_notify")
        val notifyInterval = intPreferencesKey("notify_interval")
        val openThreadsInBrowser = booleanPreferencesKey("open_threads_in_browser")
        val loadPublicAtBottom = booleanPreferencesKey("load_public_at_bottom")
        val loadPrivateAtBottom = booleanPreferencesKey("load_private_at_bottom")
        val recentDays = intPreferencesKey("recent_days")
        val loadRecentAtTop = booleanPreferencesKey("load_recent_at_top")
        val ignoreOwnRecent = booleanPreferencesKey("ignore_own_replies_recent")
        val loadWatchedAtTop = booleanPreferencesKey("load_watched_at_top")
        val ignoreOwnWatched = booleanPreferencesKey("ignore_own_replies_watched")
        val watchedAutoMarkRead = booleanPreferencesKey("watched_auto_mark_read")
        val watchedBackToList = booleanPreferencesKey("watched_back_to_list")
    }

    val settings: Flow<Settings> = store.data.map { p ->
        val d = Settings()
        Settings(
            homepage = p[Keys.homepage].toEnum(d.homepage),
            theme = p[Keys.theme].toEnum(d.theme),
            nightStart = p[Keys.nightStart]?.let(LocalTime::parse) ?: d.nightStart,
            nightEnd = p[Keys.nightEnd]?.let(LocalTime::parse) ?: d.nightEnd,
            postFontSize = p[Keys.postFontSize].toEnum(d.postFontSize),
            watchedNotify = p[Keys.watchedNotify] ?: d.watchedNotify,
            notifyIntervalMinutes = p[Keys.notifyInterval] ?: d.notifyIntervalMinutes,
            openThreadsInBrowser = p[Keys.openThreadsInBrowser] ?: d.openThreadsInBrowser,
            loadPublicAtBottom = p[Keys.loadPublicAtBottom] ?: d.loadPublicAtBottom,
            loadPrivateAtBottom = p[Keys.loadPrivateAtBottom] ?: d.loadPrivateAtBottom,
            recentHistoryDays = p[Keys.recentDays] ?: d.recentHistoryDays,
            loadRecentAtTop = p[Keys.loadRecentAtTop] ?: d.loadRecentAtTop,
            ignoreOwnRepliesRecent = p[Keys.ignoreOwnRecent] ?: d.ignoreOwnRepliesRecent,
            loadWatchedAtTop = p[Keys.loadWatchedAtTop] ?: d.loadWatchedAtTop,
            ignoreOwnRepliesWatched = p[Keys.ignoreOwnWatched] ?: d.ignoreOwnRepliesWatched,
            watchedAutoMarkAsRead = p[Keys.watchedAutoMarkRead] ?: d.watchedAutoMarkAsRead,
            watchedBackToList = p[Keys.watchedBackToList] ?: d.watchedBackToList,
        )
    }

    /** The signed-in account, or null before setup / after log out. */
    val account: Flow<Account?> = store.data.map { p ->
        val id = p[Keys.userId]
        if (p[Keys.apiKey] != null && id != null) Account(id, p[Keys.userName].orEmpty()) else null
    }

    /** True once the setup wizard has finished; the app starts in setup until then. */
    val isSetupDone: Flow<Boolean> = store.data.map { it[Keys.setupDone] == true && it[Keys.apiKey] != null }

    suspend fun apiKey(): String? = store.data.first()[Keys.apiKey]

    suspend fun signIn(key: String, account: Account) {
        store.edit {
            it[Keys.apiKey] = key
            it[Keys.userId] = account.userId
            it[Keys.userName] = account.userName
        }
    }

    suspend fun signOut() {
        store.edit {
            it.remove(Keys.apiKey)
            it.remove(Keys.userId)
            it.remove(Keys.userName)
            it.remove(Keys.setupDone)
        }
    }

    suspend fun setSetupDone() {
        store.edit { it[Keys.setupDone] = true }
    }

    /** Applies [change] to the current settings and stores the result. */
    suspend fun update(change: (Settings) -> Settings) {
        val s = change(settings.first())
        store.edit { p ->
            p[Keys.homepage] = s.homepage.name
            p[Keys.theme] = s.theme.name
            p[Keys.nightStart] = s.nightStart.toString()
            p[Keys.nightEnd] = s.nightEnd.toString()
            p[Keys.postFontSize] = s.postFontSize.name
            p[Keys.watchedNotify] = s.watchedNotify
            p[Keys.notifyInterval] = s.notifyIntervalMinutes
            p[Keys.openThreadsInBrowser] = s.openThreadsInBrowser
            p[Keys.loadPublicAtBottom] = s.loadPublicAtBottom
            p[Keys.loadPrivateAtBottom] = s.loadPrivateAtBottom
            p[Keys.recentDays] = s.recentHistoryDays
            p[Keys.loadRecentAtTop] = s.loadRecentAtTop
            p[Keys.ignoreOwnRecent] = s.ignoreOwnRepliesRecent
            p[Keys.loadWatchedAtTop] = s.loadWatchedAtTop
            p[Keys.ignoreOwnWatched] = s.ignoreOwnRepliesWatched
            p[Keys.watchedAutoMarkRead] = s.watchedAutoMarkAsRead
            p[Keys.watchedBackToList] = s.watchedBackToList
        }
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
