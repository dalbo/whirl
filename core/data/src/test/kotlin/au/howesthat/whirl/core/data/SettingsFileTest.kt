package au.howesthat.whirl.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import au.howesthat.whirl.core.model.ThreadId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings › Backup's export and import file (docs/plans/settings-backup.md, Part 2). */
class SettingsFileTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Every setting changed from its default (checked below, so a new setting must be added here). */
    private val changed = Settings(
        homepage = Homepage.WATCHED, theme = ThemeMode.DARK, palette = "whirl", layout = "compact",
        nightStart = LocalTime.of(21, 30), nightEnd = LocalTime.of(6, 15), postFontSize = PostFontSize.LARGE,
        textScale = TextScale.LARGER, screenAnimations = false, twoPane = true, twoPaneLandscapeOnly = true,
        browserApp = true, watchedNotify = true, notifyIntervalMinutes = 15, notifyStyle = NotifyStyle.PER_THREAD,
        notifyKeepUntilRead = true, openThreadsInBrowser = true, loadPublicAtBottom = true, loadPrivateAtBottom = true, readThreadsAsScroll = true,
        recentHistoryDays = 30, loadRecentAtTop = true, ignoreOwnRepliesRecent = true, loadWatchedAtTop = true,
        ignoreOwnRepliesWatched = true, watchedAutoMarkAsRead = true, watchedBackToList = true, watchUndo = false,
        undoSeconds = 10,
    )

    private fun imported(text: String): Import = assertIs<SettingsFile.Read.Imported>(SettingsFile.read(text)).import

    private fun rejected(text: String) = assertIs<SettingsFile.Read.Rejected>(SettingsFile.read(text)).problem

    private fun file(settings: String, extra: String = "") =
        """{"app":"whirl","format":1,"exportedFrom":"1.10.0","settings":{$settings}$extra}"""

    @Test
    fun `every setting, favourite and mute survives a round trip`() {
        SETTING_FIELDS.forEach { f -> assertTrue(f.valueIn(changed) != f.valueIn(Settings()), "${f.key.name} is not changed in the test") }
        val text = SettingsFile.write(changed, setOf(92, 100), setOf(ThreadId("3rvvvlwj"), ThreadId("1000422")), "1.10.0")

        val import = imported(text)
        assertEquals(changed, import.applyTo(Settings()))
        assertEquals(setOf(92, 100), import.favouriteForums)
        assertEquals(setOf(ThreadId("3rvvvlwj"), ThreadId("1000422")), import.mutedThreads)
        assertEquals(emptyList(), import.skipped)
        assertEquals(SETTING_FIELDS.size, import.changes(Settings()).size)
        assertEquals(emptyList(), import.changes(changed), "nothing to change on the phone it came from")
    }

    @Test
    fun `defaults are written too, and thread ids stay strings`() {
        val root = Json.parseToJsonElement(SettingsFile.write(Settings(), emptySet(), setOf(ThreadId("1000422")), "1.10.0")).jsonObject
        assertEquals(SETTING_KEYS.toSet(), root.getValue("settings").jsonObject.keys)
        assertEquals("""["1000422"]""", root.getValue("mutedThreads").toString())
    }

    @Test
    fun `the file never holds the key or the account`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val repo = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "settings.preferences_pb") },
            PreferenceDataStoreFactory.create(scope = scope) { File(tmp.root, "account.preferences_pb") },
        )
        repo.signIn("0123-secret-key-4567", Account(216903, "d@llin"))
        repo.setSetupDone()
        repo.update { changed }
        val text = SettingsFile.write(repo.settings.first(), setOf(92), emptySet(), "1.10.0")
        scope.coroutineContext.job.cancelAndJoin()

        listOf("0123-secret-key-4567", "api_key", "216903", "d@llin", "user_id", "user_name", "setup_done").forEach {
            assertFalse(it in text, "$it is in the file")
        }
    }

    @Test
    fun `a setting left out keeps its value, and one from a newer version is ignored`() {
        val import = imported(file(""""homepage":"NEWS","a_later_setting":true""", ""","laterList":[1]"""))
        val current = Settings(theme = ThemeMode.DARK)
        assertEquals(current.copy(homepage = Homepage.NEWS), import.applyTo(current))
        assertEquals(listOf(SettingChange("homepage", Homepage.FORUMS, Homepage.NEWS)), import.changes(current))
        assertEquals(emptyList(), import.skipped, "unknown names are not problems")
    }

    @Test
    fun `values this version cannot apply are skipped and reported, and the rest apply`() {
        val import = imported(
            file(
                """"theme":"SEPIA","night_start":"25:00","notify_interval":7,"recent_days":"30","watch_undo":"false",""" +
                    """"homepage":"NEWS","undo_seconds":3,"palette":42""",
                ""","favouriteForums":[92,"100",-1,7],"mutedThreads":["3rvvvlwj",123,"no/slash",""]""",
            ),
        )
        assertEquals(Settings(homepage = Homepage.NEWS, undoSeconds = 3), import.applyTo(Settings()))
        assertEquals(
            listOf("theme", "night_start", "notify_interval", "recent_days", "watch_undo", "palette", "favouriteForums", "mutedThreads"),
            import.skipped,
        )
        assertEquals(setOf(92, 7), import.favouriteForums)
        assertEquals(setOf(ThreadId("3rvvvlwj")), import.mutedThreads)
    }

    @Test
    fun `files that are not Whirl settings are refused`() {
        assertEquals(SettingsFile.Problem.NOT_JSON, rejected(""))
        assertEquals(SettingsFile.Problem.NOT_WHIRL, rejected("theme=DARK"), "kotlinx reads a bare word as a JSON literal")
        assertEquals(SettingsFile.Problem.NOT_JSON, rejected("""{"app":"whirl","""))
        assertEquals(SettingsFile.Problem.NOT_WHIRL, rejected("[]"))
        assertEquals(SettingsFile.Problem.NOT_WHIRL, rejected("""{"app":"other","format":1,"settings":{}}"""))
        assertEquals(SettingsFile.Problem.NOT_WHIRL, rejected("""{"app":"whirl","settings":{}}"""))
        assertEquals(SettingsFile.Problem.NOT_WHIRL, rejected("""{"app":"whirl","format":"1","settings":{}}"""))
        assertEquals(SettingsFile.Problem.NEWER_FORMAT, rejected("""{"app":"whirl","format":2,"settings":{"theme":"DARK"}}"""))
    }

    @Test
    fun `a file with no settings or lists changes nothing`() {
        val import = imported("""{"app":"whirl","format":1}""")
        assertEquals(emptyList(), import.changes(changed))
        assertEquals(emptySet(), import.favouriteForums)
        assertEquals(emptyList(), import.skipped)
    }

    @Test
    fun `reading stops past the size limit`() {
        val big = object : InputStream() {
            var left = 10 shl 20
            override fun read(): Int = if (left-- > 0) ' '.code else -1
        }
        assertNull(SettingsFile.readText(big))
        assertNull(SettingsFile.readText(ByteArrayInputStream(ByteArray(SettingsFile.MAX_BYTES + 1) { ' '.code.toByte() })))
        val exact = ByteArray(SettingsFile.MAX_BYTES) { ' '.code.toByte() }
        assertEquals(SettingsFile.MAX_BYTES, SettingsFile.readText(ByteArrayInputStream(exact))?.length)
    }
}
