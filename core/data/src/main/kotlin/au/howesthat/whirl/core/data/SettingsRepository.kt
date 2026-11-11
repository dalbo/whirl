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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** The settings: [SETTING_FIELDS] and nothing secret, so it can be backed up and exported. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_STORE,
    produceMigrations = { listOf(DropStoredDefaults) },
)

/** The API key and its account, apart from the settings so the backup rules can treat them differently. */
private val Context.accountStore: DataStore<Preferences> by preferencesDataStore(
    name = ACCOUNT_STORE,
    produceMigrations = { context -> listOf(MoveAccountOut(context.dataStore)) },
)

/** The DataStore files (`files/datastore/<name>.preferences_pb`); the backup rules name each one. */
const val SETTINGS_STORE = "settings"
const val ACCOUNT_STORE = "account"

/** What the account store holds. Up to v1.8 these were in the settings store. */
private object AccountKeys {
    val apiKey = stringPreferencesKey("api_key")
    val userId = longPreferencesKey("user_id")
    val userName = stringPreferencesKey("user_name")
    val setupDone = booleanPreferencesKey("setup_done")
    val all = listOf(apiKey, userId, userName, setupDone)
}

/** What the settings store holds besides [Settings]: v1.0's colour switch. */
private object Keys {
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
    bool("read_threads_as_scroll", Settings::readThreadsAsScroll) { copy(readThreadsAsScroll = it) },
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
 * Once (v1.9), on the account store: copies the key and account out of the settings store, then removes them there,
 * so the settings file holds nothing secret. The copy runs once (the marker); the removal is retried on a later start
 * if it failed, and never copies again, so it cannot bring back an account signed out since.
 */
internal class MoveAccountOut(private val settings: DataStore<Preferences>) : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences) =
        currentData[moved] != true || AccountKeys.all.any { it in settings.data.first() }

    override suspend fun migrate(currentData: Preferences): Preferences {
        if (currentData[moved] == true) return currentData
        val old = settings.data.first()
        return currentData.toMutablePreferences().apply {
            AccountKeys.all.forEach { copy(old, it) }
            this[moved] = true
        }
    }

    /**
     * A failure here is swallowed: DataStore would otherwise fail every read and write of the account store until it
     * succeeded (a full disk would stop the app starting). [shouldMigrate] sees the keys still in the settings file and
     * tries the removal again on a later start.
     */
    override suspend fun cleanUp() {
        try {
            settings.edit { p -> AccountKeys.all.forEach { p.remove(it) } }
        } catch (_: IOException) {
            // Retried on a later start.
        }
    }

    private fun <T> MutablePreferences.copy(from: Preferences, key: Preferences.Key<T>) {
        from[key]?.let { this[key] = it }
    }

    private companion object {
        val moved = booleanPreferencesKey("moved_from_settings")
    }
}

/**
 * Settings, and the API key with its account, in two Preferences DataStores. The key is never logged; [apiKey] exists
 * for the network layer and nothing else should read it.
 */
@Singleton
class SettingsRepository internal constructor(
    private val store: DataStore<Preferences>,
    private val accountStore: DataStore<Preferences>,
) {

    @Inject constructor(@ApplicationContext context: Context) : this(context.dataStore, context.accountStore)

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    /**
     * The account store, its read retried a few times on a passing I/O error (a failed [MoveAccountOut] copy fails the
     * read until the next attempt).
     */
    private val accountData: Flow<Preferences> = accountStore.data.retryWhen { cause, attempt ->
        (cause is IOException && attempt < 3).also { if (it) delay(100L shl attempt.toInt()) }
    }

    /** The signed-in account, or null before setup / after log out. */
    val account: Flow<Account?> = accountData.map { p ->
        val id = p[AccountKeys.userId]
        if (p[AccountKeys.apiKey] != null && id != null) Account(id, p[AccountKeys.userName].orEmpty()) else null
    }

    /** True once the setup wizard has finished; the app starts in setup until then. */
    val isSetupDone: Flow<Boolean> = accountData.map { it[AccountKeys.setupDone] == true && it[AccountKeys.apiKey] != null }

    suspend fun apiKey(): String? = accountData.first()[AccountKeys.apiKey]

    suspend fun signIn(key: String, account: Account) {
        accountStore.edit {
            it[AccountKeys.apiKey] = key
            it[AccountKeys.userId] = account.userId
            it[AccountKeys.userName] = account.userName
        }
    }

    suspend fun signOut() {
        accountStore.edit { p -> AccountKeys.all.forEach { p.remove(it) } }
    }

    suspend fun setSetupDone() {
        accountStore.edit { it[AccountKeys.setupDone] = true }
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
