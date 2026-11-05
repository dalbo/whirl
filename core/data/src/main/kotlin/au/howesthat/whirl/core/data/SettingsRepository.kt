package au.howesthat.whirl.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
        /** v1.0's "Use wallpaper colours"; read only, to choose the first [palette]. */
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val palette = stringPreferencesKey("palette")
        val layout = stringPreferencesKey("layout")
        val nightStart = stringPreferencesKey("night_start")
        val nightEnd = stringPreferencesKey("night_end")
        val postFontSize = stringPreferencesKey("post_font_size")
        val textScale = stringPreferencesKey("text_scale")
        val screenAnimations = booleanPreferencesKey("screen_animations")
        val twoPane = booleanPreferencesKey("two_pane")
        val twoPaneLandscapeOnly = booleanPreferencesKey("two_pane_landscape_only")
        val browserApp = booleanPreferencesKey("browser_app")
        val watchedNotify = booleanPreferencesKey("watched_notify")
        val notifyInterval = intPreferencesKey("notify_interval")
        val notifyStyle = stringPreferencesKey("notify_style")
        val notifyKeepUntilRead = booleanPreferencesKey("notify_keep_until_read")
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
        val watchUndo = booleanPreferencesKey("watch_undo")
        val undoSeconds = intPreferencesKey("undo_seconds")
    }

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    private fun Preferences.toSettings(): Settings {
        val p = this
        val d = Settings()
        return Settings(
            homepage = p[Keys.homepage].toEnum(d.homepage),
            theme = p[Keys.theme].toEnum(d.theme),
            palette = p[Keys.palette] ?: if (p[Keys.dynamicColor] == false) "whirl" else d.palette,
            layout = p[Keys.layout] ?: d.layout,
            screenAnimations = p[Keys.screenAnimations] ?: d.screenAnimations,
            twoPane = p[Keys.twoPane] ?: d.twoPane,
            twoPaneLandscapeOnly = p[Keys.twoPaneLandscapeOnly] ?: d.twoPaneLandscapeOnly,
            browserApp = p[Keys.browserApp] ?: d.browserApp,
            nightStart = p[Keys.nightStart]?.let(LocalTime::parse) ?: d.nightStart,
            nightEnd = p[Keys.nightEnd]?.let(LocalTime::parse) ?: d.nightEnd,
            postFontSize = p[Keys.postFontSize].toEnum(d.postFontSize),
            textScale = p[Keys.textScale].toEnum(d.textScale),
            watchedNotify = p[Keys.watchedNotify] ?: d.watchedNotify,
            notifyIntervalMinutes = p[Keys.notifyInterval] ?: d.notifyIntervalMinutes,
            notifyStyle = p[Keys.notifyStyle].toEnum(d.notifyStyle),
            notifyKeepUntilRead = p[Keys.notifyKeepUntilRead] ?: d.notifyKeepUntilRead,
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
            watchUndo = p[Keys.watchUndo] ?: d.watchUndo,
            undoSeconds = p[Keys.undoSeconds] ?: d.undoSeconds,
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

    /**
     * Applies [change] to the current settings and stores the result, as one read-modify-write: two updates at once
     * both take effect. Only settings that differ from the default are stored, so a default changed in a later version
     * reaches everyone who never changed that setting.
     */
    suspend fun update(change: (Settings) -> Settings) {
        store.edit { p ->
            val s = change(p.toSettings())
            val d = Settings()
            p.put(Keys.homepage, s.homepage.name, d.homepage.name)
            p.put(Keys.theme, s.theme.name, d.theme.name)
            p.put(Keys.palette, s.palette, d.palette)
            p.put(Keys.layout, s.layout, d.layout)
            p.put(Keys.screenAnimations, s.screenAnimations, d.screenAnimations)
            p.put(Keys.twoPane, s.twoPane, d.twoPane)
            p.put(Keys.twoPaneLandscapeOnly, s.twoPaneLandscapeOnly, d.twoPaneLandscapeOnly)
            p.put(Keys.browserApp, s.browserApp, d.browserApp)
            p.put(Keys.nightStart, s.nightStart.toString(), d.nightStart.toString())
            p.put(Keys.nightEnd, s.nightEnd.toString(), d.nightEnd.toString())
            p.put(Keys.postFontSize, s.postFontSize.name, d.postFontSize.name)
            p.put(Keys.textScale, s.textScale.name, d.textScale.name)
            p.put(Keys.watchedNotify, s.watchedNotify, d.watchedNotify)
            p.put(Keys.notifyInterval, s.notifyIntervalMinutes, d.notifyIntervalMinutes)
            p.put(Keys.notifyStyle, s.notifyStyle.name, d.notifyStyle.name)
            p.put(Keys.notifyKeepUntilRead, s.notifyKeepUntilRead, d.notifyKeepUntilRead)
            p.put(Keys.openThreadsInBrowser, s.openThreadsInBrowser, d.openThreadsInBrowser)
            p.put(Keys.loadPublicAtBottom, s.loadPublicAtBottom, d.loadPublicAtBottom)
            p.put(Keys.loadPrivateAtBottom, s.loadPrivateAtBottom, d.loadPrivateAtBottom)
            p.put(Keys.recentDays, s.recentHistoryDays, d.recentHistoryDays)
            p.put(Keys.loadRecentAtTop, s.loadRecentAtTop, d.loadRecentAtTop)
            p.put(Keys.ignoreOwnRecent, s.ignoreOwnRepliesRecent, d.ignoreOwnRepliesRecent)
            p.put(Keys.loadWatchedAtTop, s.loadWatchedAtTop, d.loadWatchedAtTop)
            p.put(Keys.ignoreOwnWatched, s.ignoreOwnRepliesWatched, d.ignoreOwnRepliesWatched)
            p.put(Keys.watchedAutoMarkRead, s.watchedAutoMarkAsRead, d.watchedAutoMarkAsRead)
            p.put(Keys.watchedBackToList, s.watchedBackToList, d.watchedBackToList)
            p.put(Keys.watchUndo, s.watchUndo, d.watchUndo)
            p.put(Keys.undoSeconds, s.undoSeconds, d.undoSeconds)
            // v1.0's switch only chose the first palette; from now on the palette key (or its absence) says.
            p.remove(Keys.dynamicColor)
        }
    }

    private fun <T> MutablePreferences.put(key: Preferences.Key<T>, value: T, default: T) {
        if (value == default) remove(key) else this[key] = value
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
}
