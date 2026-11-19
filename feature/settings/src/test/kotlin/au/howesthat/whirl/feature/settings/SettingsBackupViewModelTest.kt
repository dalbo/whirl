package au.howesthat.whirl.feature.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.data.AccountGeneration
import au.howesthat.whirl.core.data.AccountRepository
import au.howesthat.whirl.core.data.AppInfo
import au.howesthat.whirl.core.data.LibraryRepository
import au.howesthat.whirl.core.data.SettingChange
import au.howesthat.whirl.core.data.SettingsFile
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.data.WatchQueue
import au.howesthat.whirl.core.data.WatchSendScheduler
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.data.WatchedStore
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import androidx.lifecycle.ViewModelProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/** Settings › Backup's Export and Import, through the view model, with files in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SettingsBackupViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, WhirlDatabase::class.java).allowMainThreadQueries().build()
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings = SettingsRepository(context)
    private val library = LibraryRepository(context, db.forumDao())
    private val api = WhirlpoolApi(WhirlpoolHttp.client("t"), { null })
    private val store = WatchedStore(db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), Clock.systemUTC())
    private val watched = WatchedRepository(api, store)
    private val queue = WatchQueue(api, store, watched, WatchSendScheduler.None, Dispatchers.Default, appScope)
    private val files = MemoryFiles()
    private val viewModels = ViewModelStore()

    private val file = Uri.parse("content://test/whirl-settings.json")

    private class MemoryFiles : BackupFiles {
        val written = mutableMapOf<Uri, ByteArray>()
        override fun openInput(uri: Uri): InputStream? = written[uri]?.let(::ByteArrayInputStream)
        override fun openOutput(uri: Uri): OutputStream = object : ByteArrayOutputStream() {
            override fun close() {
                written[uri] = toByteArray()
            }
        }
    }

    private fun viewModel(files: BackupFiles = this.files): SettingsViewModel {
        val factory = viewModelFactory {
            initializer { SettingsViewModel(settings, AccountRepository(api, settings, db, queue), library, files, AppInfo("1.10.0"), Dispatchers.IO) }
        }
        return ViewModelProvider.create(viewModels, factory)[SettingsViewModel::class.java]
    }

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // One DataStore per JVM: start from the defaults and no favourites or mutes.
        settings.update { au.howesthat.whirl.core.data.Settings() }
        library.favouriteForums.first().forEach { library.setFavourite(it, false) }
        library.setMuted(library.mutedThreads.first(), muted = false)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
        appScope.cancel()
        db.close()
    }

    @Test
    fun `an export imports back after changes, adding favourites and keeping the key out`() = runBlocking {
        settings.signIn("0123-secret-key-4567", Account(216903, "d@llin"))
        settings.update { it.copy(theme = ThemeMode.DARK, notifyIntervalMinutes = 30) }
        library.setFavourite(92, true)
        library.setMuted(listOf(ThreadId("3rvvvlwj")), muted = true)
        val vm = viewModel()
        withTimeout(5_000) {
            vm.exportTo(file)
            assertEquals(BackupResult.Exported, vm.results.first())
        }
        val text = files.written.getValue(file).decodeToString()
        assertFalse("0123-secret-key-4567" in text || "216903" in text, "no key or account in the file")

        settings.update { it.copy(theme = ThemeMode.LIGHT) }
        library.setFavourite(92, false)
        library.setFavourite(7, true)

        withTimeout(5_000) {
            vm.importFrom(file)
            val preview = vm.preview.filterNotNull().first()
            assertEquals(listOf(SettingChange("theme", ThemeMode.LIGHT, ThemeMode.DARK)), preview.changes)
            assertEquals(1, preview.newFavourites)
            assertEquals(0, preview.newMutes)
            assertEquals(ThemeMode.LIGHT, settings.settings.first().theme, "nothing is written before Apply")

            vm.applyImport()
            assertEquals(BackupResult.Imported, vm.results.first())
            assertNull(vm.preview.value)
        }
        val now = settings.settings.first()
        assertEquals(ThemeMode.DARK, now.theme)
        assertEquals(30, now.notifyIntervalMinutes)
        assertEquals(setOf(7, 92), library.favouriteForums.first(), "merged, none removed")
        assertEquals(setOf(ThreadId("3rvvvlwj")), library.mutedThreads.first())
    }

    @Test
    fun `cancel writes nothing`() = runBlocking {
        files.written[file] = SettingsFile.write(au.howesthat.whirl.core.data.Settings(theme = ThemeMode.DARK), setOf(92), emptySet(), "1.10.0").toByteArray()
        val vm = viewModel()
        withTimeout(5_000) {
            vm.importFrom(file)
            vm.preview.filterNotNull().first()
        }
        vm.cancelImport()
        assertNull(vm.preview.value)
        assertEquals(ThemeMode.SYSTEM, settings.settings.first().theme)
        assertEquals(emptySet(), library.favouriteForums.first())
    }

    @Test
    fun `files that cannot be imported say why`() = runBlocking {
        val vm = viewModel()
        withTimeout(5_000) {
            files.written[file] = """{"app":"whirl","format":99}""".toByteArray()
            vm.importFrom(file)
            assertEquals(BackupResult.Refused(SettingsFile.Problem.NEWER_FORMAT), vm.results.first())

            files.written[file] = ByteArray(2 shl 20) { ' '.code.toByte() }
            vm.importFrom(file)
            assertEquals(BackupResult.Refused(SettingsFile.Problem.TOO_LARGE), vm.results.first())

            vm.importFrom(Uri.parse("content://test/missing.json"))
            assertEquals(BackupResult.ReadFailed, vm.results.first())
        }
        assertNull(vm.preview.value)
    }

    @Test
    fun `a failed write is reported`() = runBlocking {
        val broken = object : BackupFiles {
            override fun openInput(uri: Uri): InputStream? = null
            override fun openOutput(uri: Uri): OutputStream = throw IOException("disk full")
        }
        val vm = viewModel(broken)
        withTimeout(5_000) {
            vm.exportTo(file)
            assertEquals(BackupResult.ExportFailed, vm.results.first())
        }
    }
}
