package au.howesthat.whirl.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The key and account moved out of the settings store (v1.9), so the settings file can be backed up. */
class AccountStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private val settingsFile by lazy { File(tmp.root, "settings.preferences_pb") }
    private val accountFile by lazy { File(tmp.root, "account.preferences_pb") }
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }

    /** Opens both files as the app does; [close] before opening them again (one DataStore per file at a time). */
    private inner class Stores(wrapSettings: (DataStore<Preferences>) -> DataStore<Preferences> = { it }) {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val settings = wrapSettings(PreferenceDataStoreFactory.create(scope = scope, migrations = listOf(DropStoredDefaults)) { settingsFile })
        val account = PreferenceDataStoreFactory.create(scope = scope, migrations = listOf(MoveAccountOut(settings))) { accountFile }
        val repo = SettingsRepository(settings, account)

        suspend fun close() = scope.coroutineContext.job.cancelAndJoin()
    }

    /** What v1.8 stored after setup and a change of theme: settings and account in one file. */
    private suspend fun v18Store() {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        PreferenceDataStoreFactory.create(scope = scope) { settingsFile }.edit {
            it[stringPreferencesKey("theme")] = ThemeMode.DARK.name
            it[booleanPreferencesKey("stored_defaults_dropped")] = true
            it[stringPreferencesKey("api_key")] = "1234-5678"
            it[longPreferencesKey("user_id")] = 216903
            it[stringPreferencesKey("user_name")] = "d@llin"
            it[booleanPreferencesKey("setup_done")] = true
        }
        scope.coroutineContext.job.cancelAndJoin()
    }

    private suspend fun keys(store: DataStore<Preferences>) = store.data.first().asMap().keys.map { it.name }.toSet()

    @Test
    fun `v1_8's key and account move to the account file and leave the settings file`() = runBlocking {
        v18Store()
        val s = Stores()
        withTimeout(5_000) {
            assertEquals("1234-5678", s.repo.apiKey())
            assertEquals(Account(216903, "d@llin"), s.repo.account.first())
            assertTrue(s.repo.isSetupDone.first())
            assertEquals(ThemeMode.DARK, s.repo.settings.first().theme)
            assertEquals(setOf("theme", "stored_defaults_dropped"), keys(s.settings))
            assertEquals(setOf("api_key", "user_id", "user_name", "setup_done", "moved_from_settings"), keys(s.account))
        }
    }

    @Test
    fun `the move runs once and never brings back an account signed out since`() = runBlocking {
        v18Store()
        val first = Stores()
        withTimeout(5_000) {
            first.repo.signOut()
            assertNull(first.repo.apiKey())
            assertEquals(ThemeMode.DARK, first.repo.settings.first().theme, "signing out keeps the settings")
        }
        first.close()

        val again = Stores()
        withTimeout(5_000) {
            assertNull(again.repo.apiKey())
            assertFalse(again.repo.isSetupDone.first())
            assertEquals(setOf("moved_from_settings"), keys(again.account))
        }
    }

    @Test
    fun `a fresh install starts with an empty account`() = runBlocking {
        val s = Stores()
        withTimeout(5_000) {
            assertNull(s.repo.account.first())
            s.repo.signIn("key", Account(1, "me"))
            s.repo.setSetupDone()
            assertTrue(s.repo.isSetupDone.first())
            assertEquals(setOf("stored_defaults_dropped"), keys(s.settings), "nothing of the account in the settings file")
        }
    }

    @Test
    fun `a removal that fails is retried, and the key stays readable`() = runBlocking {
        v18Store()
        var failures = 1
        val s = Stores { real -> FailingEdits(real) { failures-- > 0 } }
        withTimeout(5_000) {
            assertFailsWith<IOException> { s.account.data.first() }
            // The repository retries the read, which retries the removal.
            assertEquals("1234-5678", s.repo.apiKey())
            assertEquals(setOf("theme", "stored_defaults_dropped"), keys(s.settings))
        }
        s.close()

        // And on a later start, had the retry failed too: nothing is copied again.
        failures = 0
        val again = Stores()
        withTimeout(5_000) { assertEquals("1234-5678", again.repo.apiKey()) }
    }

    /** A settings store whose writes throw while [fail] says so, as a full disk would. */
    private class FailingEdits(private val real: DataStore<Preferences>, private val fail: () -> Boolean) : DataStore<Preferences> {
        override val data = real.data
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            if (fail()) throw IOException("disk full")
            return real.updateData(transform)
        }
    }
}
