package au.howesthat.whirl.core.data

import android.content.Context
import androidx.datastore.core.DataMigration
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
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
    produceMigrations = { listOf(DropStoredDefaults) },
)

/** What the store holds besides [Settings]: the account, and v1.0's colour switch. */
private object Keys {
    val apiKey = stringPreferencesKey("api_key")
    val userId = longPreferencesKey("user_id")
    val userName = stringPreferencesKey("user_name")
    val setupDone = booleanPreferencesKey("setup_done")
    /** v1.0's "Use wallpaper colours"; read only, to choose the first palette. */
    val dynamicColor = booleanPreferencesKey("dynamic_color")
}

/**
 * One setting in the store: its key, its [Settings] field, and how it is stored. Its default is the field's value in
 * `Settings()`; a value equal to the default is not stored.
 */
internal class SettingField<T, S : Any>(
    val key: Preferences.Key<S>,
    private val get: (Settings) -> T,
    private val set: Settings.(T) -> Settings,
    private val encode: (T) -> S,
    /** Null for a value this version does not know (an enum constant since removed): the default is used. */
    private val decode: (S) -> T?,
) {
    fun read(p: Preferences, s: Settings): Settings = p[key]?.let(decode)?.let { s.set(it) } ?: s

    fun write(p: MutablePreferences, s: Settings) {
        val value = get(s)
        if (value == get(DEFAULTS)) p.remove(key) else p[key] = encode(value)
    }
}

private val DEFAULTS = Settings()

private fun bool(name: String, get: (Settings) -> Boolean, set: Settings.(Boolean) -> Settings) =
    SettingField(booleanPreferencesKey(name), get, set, { it }, { it })

private fun int(name: String, get: (Settings) -> Int, set: Settings.(Int) -> Settings) =
    SettingField(intPreferencesKey(name), get, set, { it }, { it })

private fun string(name: String, get: (Settings) -> String, set: Settings.(String) -> Settings) =
    SettingField(stringPreferencesKey(name), get, set, { it }, { it })

private fun time(name: String, get: (Settings) -> LocalTime, set: Settings.(LocalTime) -> Settings) =
    SettingField(stringPreferencesKey(name), get, set, LocalTime::toString) { runCatching { LocalTime.parse(it) }.getOrNull() }

private inline fun <reified E : Enum<E>> enum(name: String, noinline get: (Settings) -> E, noinline set: Settings.(E) -> Settings) =
    SettingField(stringPreferencesKey(name), get, set, { it.name }, { stored -> enumValues<E>().firstOrNull { it.name == stored } })

/** Every setting, once: the stored names are fixed, since they are in users' stores. */
internal val SETTING_FIELDS: List<SettingField<*, *>> = listOf(
    enum("homepage", Settings::homepage) { copy(homepage = it) },
    enum("theme", Settings::theme) { copy(theme = it) },
    // With no palette stored, v1.0's switch (if it was turned off) chooses the first one: see toSettings().
    string("palette", Settings::palette) { copy(palette = it) },
    string("layout", Settings::layout) { copy(layout = it) },
    time("night_start", Settings::nightStart) { copy(nightStart = it) },
    time("night_end", Settings::nightEnd) { copy(nightEnd = it) },
    enum("post_font_size", Settings::postFontSize) { copy(postFontSize = it) },
    enum("text_scale", Settings::textScale) { copy(textScale = it) },
    bool("screen_animations", Settings::screenAnimations) { copy(screenAnimations = it) },
    bool("two_pane", Settings::twoPane) { copy(twoPane = it) },
    bool("two_pane_landscape_only", Settings::twoPaneLandscapeOnly) { copy(twoPaneLandscapeOnly = it) },
    bool("browser_app", Settings::browserApp) { copy(browserApp = it) },
    bool("watched_notify", Settings::watchedNotify) { copy(watchedNotify = it) },
    int("notify_interval", Settings::notifyIntervalMinutes) { copy(notifyIntervalMinutes = it) },
    enum("notify_style", Settings::notifyStyle) { copy(notifyStyle = it) },
    bool("notify_keep_until_read", Settings::notifyKeepUntilRead) { copy(notifyKeepUntilRead = it) },
    bool("open_threads_in_browser", Settings::openThreadsInBrowser) { copy(openThreadsInBrowser = it) },
    bool("load_public_at_bottom", Settings::loadPublicAtBottom) { copy(loadPublicAtBottom = it) },
    bool("load_private_at_bottom", Settings::loadPrivateAtBottom) { copy(loadPrivateAtBottom = it) },
    int("recent_days", Settings::recentHistoryDays) { copy(recentHistoryDays = it) },
    bool("load_recent_at_top", Settings::loadRecentAtTop) { copy(loadRecentAtTop = it) },
    bool("ignore_own_replies_recent", Settings::ignoreOwnRepliesRecent) { copy(ignoreOwnRepliesRecent = it) },
    bool("load_watched_at_top", Settings::loadWatchedAtTop) { copy(loadWatchedAtTop = it) },
    bool("ignore_own_replies_watched", Settings::ignoreOwnRepliesWatched) { copy(ignoreOwnRepliesWatched = it) },
    bool("watched_auto_mark_read", Settings::watchedAutoMarkAsRead) { copy(watchedAutoMarkAsRead = it) },
    bool("watched_back_to_list", Settings::watchedBackToList) { copy(watchedBackToList = it) },
    bool("watch_undo", Settings::watchUndo) { copy(watchUndo = it) },
    int("undo_seconds", Settings::undoSeconds) { copy(undoSeconds = it) },
)

private val PALETTE = SETTING_FIELDS.single { it.key.name == "palette" }.key

private fun Preferences.toSettings(): Settings {
    val first = if (this[PALETTE] == null && this[Keys.dynamicColor] == false) DEFAULTS.copy(palette = "whirl") else DEFAULTS
    return SETTING_FIELDS.fold(first) { s, field -> field.read(this, s) }
}

/**
 * Stores [s], leaving out what equals the default, so a default changed in a later version reaches everyone who never
 * changed that setting.
 */
private fun MutablePreferences.writeSettings(s: Settings) {
    SETTING_FIELDS.forEach { it.write(this, s) }
    // v1.0's switch only chose the first palette; from now on the palette key (or its absence) says.
    remove(Keys.dynamicColor)
}

/**
 * Once (v1.7): up to v1.6 every settings change stored every setting, pinning the defaults of the time. Drops the
 * stored values that equal today's defaults, as [SettingsRepository.update] now leaves them out. Nothing reads
 * differently; a later change of default then reaches these users too.
 */
internal object DropStoredDefaults : DataMigration<Preferences> {
    private val done = booleanPreferencesKey("stored_defaults_dropped")

    override suspend fun shouldMigrate(currentData: Preferences) = currentData[done] != true

    override suspend fun migrate(currentData: Preferences): Preferences = currentData.toMutablePreferences().apply {
        writeSettings(toSettings())
        this[done] = true
    }

    override suspend fun cleanUp() = Unit
}

/**
 * Settings and the API key, in Preferences DataStore. The key is never logged; [apiKey] exists for the network layer
 * and nothing else should read it.
 */
@Singleton
class SettingsRepository internal constructor(private val store: DataStore<Preferences>) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.dataStore)


    val settings: Flow<Settings> = store.data.map { it.toSettings() }

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
        store.edit { p -> p.writeSettings(change(p.toSettings())) }
    }


}
