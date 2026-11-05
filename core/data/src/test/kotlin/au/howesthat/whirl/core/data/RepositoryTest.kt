package au.howesthat.whirl.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.FetchLogEntity
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock tests can move. */
class MutableClock(var now: Instant = Instant.parse("2026-10-20T09:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("Australia/Sydney")
    override fun withZone(zone: ZoneId?) = this
    override fun instant(): Instant = now
}

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MutableClock()
    // DataStore needs a scope that actually runs; a TestScope that nobody advances would hang every read.
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store by lazy { PreferenceDataStoreFactory.create(scope = dataStoreScope) { tmp.newFile("s.preferences_pb").also { it.delete() } } }
    private val settings by lazy { SettingsRepository(store) }
    private val api by lazy {
        WhirlpoolApi(WhirlpoolHttp.client("Whirl/test"), settings::apiKey, server.url("/api/"), RetryPolicy.NONE)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
        dataStoreScope.cancel()
    }

    private fun watchQueue(generation: AccountGeneration, clock: Clock, scope: CoroutineScope): WatchQueue {
        val store = WatchedStore(db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), generation, clock)
        return WatchQueue(api, store, WatchedRepository(api, store), WatchSendScheduler.None, Dispatchers.Default, scope)
    }

    private fun enqueue(name: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).body(File(System.getProperty("whirl.fixtures"), "api/$name").readText()).build(),
    )

    @Test
    fun `sign in stores the key and account, bad key stores nothing`() = runTest {
        val account = AccountRepository(api, settings, db, watchQueue(AccountGeneration(), Clock.systemUTC(), backgroundScope))
        enqueue("api_badkey.json", 403)
        assertFailsWith<WhirlpoolError.BadKey> { account.signIn("bad") }
        assertNull(settings.apiKey())
        assertNull(settings.account.first())

        enqueue("api_user.json")
        assertEquals(Account(216903, "d@llin"), account.signIn("  1234-5678  "))
        assertEquals("1234-5678", settings.apiKey(), "the key is trimmed")
        assertEquals(Account(216903, "d@llin"), settings.account.first())
        assertFalse(settings.isSetupDone.first())
        settings.setSetupDone()
        assertTrue(settings.isSetupDone.first())

        account.signOut()
        assertNull(settings.apiKey())
        assertFalse(settings.isSetupDone.first())
    }

    @Test
    fun `settings round trip`() = runTest {
        assertEquals(Settings(), settings.settings.first())
        settings.update { it.copy(theme = ThemeMode.DARK, recentHistoryDays = 14, watchedNotify = true) }
        val s = settings.settings.first()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals(14, s.recentHistoryDays)
        assertTrue(s.watchedNotify)
    }

    @Test
    fun `colours default to the wallpaper, and v1_0's wallpaper switch carries over`() = runTest {
        assertEquals("wallpaper", settings.settings.first().palette)
        store.edit { it[booleanPreferencesKey("dynamic_color")] = false }
        assertEquals("whirl", settings.settings.first().palette)
        settings.update { it.copy(palette = "whirlpool") }
        assertEquals("whirlpool", settings.settings.first().palette)
    }

    @Test
    fun `screen animations default on, the browser app off`() = runTest {
        val d = settings.settings.first()
        assertTrue(d.screenAnimations)
        assertFalse(d.browserApp)
        settings.update { it.copy(screenAnimations = false, browserApp = true) }
        val s = settings.settings.first()
        assertFalse(s.screenAnimations)
        assertTrue(s.browserApp)
    }

    @Test
    fun `two panes are off until turned on`() = runTest {
        assertFalse(settings.settings.first().twoPane)
        assertFalse(settings.settings.first().twoPaneLandscapeOnly)
        settings.update { it.copy(twoPane = true, twoPaneLandscapeOnly = true) }
        assertTrue(settings.settings.first().twoPane)
        assertTrue(settings.settings.first().twoPaneLandscapeOnly)
    }

    @Test
    fun `the layout defaults to Classic and is stored by id`() = runTest {
        assertEquals("classic", settings.settings.first().layout)
        settings.update { it.copy(layout = "cards") }
        assertEquals("cards", settings.settings.first().layout)
    }

    @Test
    fun `text sizes default to Whirldroid's 17 sp posts and the system size elsewhere`() = runTest {
        val d = settings.settings.first()
        assertEquals(17f, d.postFontSize.sp)
        assertEquals(TextScale.DEFAULT, d.textScale)
        settings.update { it.copy(textScale = TextScale.LARGER) }
        assertEquals(1.3f, settings.settings.first().textScale.factor)
    }

    @Test
    fun `night window round trip`() = runTest {
        settings.update { it.copy(theme = ThemeMode.DARK_AT_NIGHT, nightStart = LocalTime.of(15, 5), nightEnd = LocalTime.of(6, 30)) }
        val s = settings.settings.first()
        assertEquals(LocalTime.of(15, 5), s.nightStart)
        assertEquals(LocalTime.of(6, 30), s.nightEnd)
    }

    @Test
    fun `watched list refreshes and respects max age`() = runTest {
        settings.signIn("k", Account(216903, "d@llin"))
        val repo = WatchedRepository(api, WatchedStore(db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), clock))
        enqueue("api_watched_mode0.json")
        assertTrue(repo.refresh(unreadOnly = true))
        val cached = repo.watched(unreadOnly = true).first()
        assertEquals(listOf("9yvx1663", "9q684xp3"), cached.value.map { it.id.value })
        assertEquals(683, cached.value.first().unreadCount)
        assertEquals(clock.now, cached.updatedAt)

        clock.now = clock.now.plusSeconds(5 * 60)
        assertFalse(repo.refresh(unreadOnly = true), "fresh for 10 minutes")
        assertEquals(1, server.requestCount)
        enqueue("api_watched_mode0.json")
        assertTrue(repo.refresh(unreadOnly = true, force = true), "pull-to-refresh ignores the max age")
        assertEquals(2, server.requestCount)

    }

    @Test
    fun `failed refresh keeps the old data and timestamp`() = runTest {
        settings.signIn("k", Account(216903, "d@llin"))
        val repo = NewsRepository(api, db.newsDao(), db.fetchLogDao(), clock)
        enqueue("api_news.json")
        repo.refresh()
        val before = repo.news.first()
        assertEquals(30, before.value.size)
        clock.now = clock.now.plusSeconds(2 * 3600)
        server.enqueue(MockResponse.Builder().code(403).addHeader("cf-mitigated", "challenge").build())
        assertFailsWith<WhirlpoolError.Challenge> { repo.refresh() }
        assertEquals(before, repo.news.first())
    }

    @Test
    fun `forums and latest threads`() = runTest {
        settings.signIn("k", Account(216903, "d@llin"))
        val forums = ForumRepository(api, db.forumDao(), db.fetchLogDao(), clock)
        enqueue("api_forum.json")
        forums.refresh()
        forums.setFavourite(92, true)
        val list = forums.forums.first().value
        assertEquals(54, list.size)
        assertTrue(list.single { it.forum.id == 92 }.isFavourite)

        val latest = LatestThreadsRepository(api, db.threadDao(), db.fetchLogDao(), clock)
        enqueue("api_threads_count100.json")
        latest.refresh()
        server.takeRequest() // get=forum
        assertEquals("100", server.takeRequest().url.queryParameter("threadcount"))
        val threads = latest.threads.first().value
        assertEquals(100, threads.size)
        assertEquals(
            Instant.parse("2026-10-20T09:55:25Z"),
            threads.first().lastDate.toInstant(),
            "LAST_DATE 2026-10-20T19:55:25+1000 survives the cache as an instant",
        )
    }

    @Test
    fun `refresher throttles manual refreshes to one per 10 seconds`() = runTest {
        val refresher = Refresher(db.fetchLogDao(), clock)
        var calls = 0
        assertTrue(refresher.refresh("k", MaxAge.THREAD_LISTS, force = true) { calls++ })
        clock.now = clock.now.plusSeconds(5)
        assertFalse(refresher.refresh("k", MaxAge.THREAD_LISTS, force = true) { calls++ })
        clock.now = clock.now.plusSeconds(6)
        assertTrue(refresher.refresh("k", MaxAge.THREAD_LISTS, force = true) { calls++ })
        assertEquals(2, calls)
        db.fetchLogDao().put(FetchLogEntity("k", 0))
        assertTrue(refresher.refresh("k", MaxAge.THREAD_LISTS, force = false) { calls++ })
    }

    @Test
    fun `refresher keeps a key's lock only while it is used`() = runTest {
        val refresher = Refresher(db.fetchLogDao(), clock)
        repeat(3) { i -> refresher.refresh("user:$i", MaxAge.THREAD_LISTS, force = false) {} }
        assertEquals(0, refresher.lockCount, "users and searches are keys: a lock per key ever seen would only grow")
        val release = CompletableDeferred<Unit>()
        val held = async { refresher.refresh("k", MaxAge.THREAD_LISTS, force = false) { release.await() } }
        val waiting = async { refresher.refresh("k", MaxAge.THREAD_LISTS, force = false) {} }
        testScheduler.runCurrent()
        assertEquals(1, refresher.lockCount)
        release.complete(Unit)
        assertTrue(held.await())
        assertFalse(waiting.await(), "the second caller waited for the first, then found it fresh")
        assertEquals(0, refresher.lockCount)
    }

    @Test
    fun `a memory cache keeps the most recently fetched`() = runTest {
        val cache = MemoryCache<Int, String>(keep = 2, clock)
        for (key in 1..3) {
            cache.refresh(key, MaxAge.THREAD_LISTS, force = false) { "v$key" }
            clock.now = clock.now.plusSeconds(1)
        }
        assertNull(cache.observe(1).first(), "the oldest went")
        assertEquals("v3", cache.observe(3).first()?.value)
        assertFalse(cache.refresh(2, MaxAge.THREAD_LISTS, force = false) { "again" }, "still fresh")
        assertEquals("v2", cache.observe(2).first()?.value)
    }

    @Test
    fun `every setting is in the table of keys and reads back as stored`() = runTest {
        val properties = Settings::class.java.declaredFields.count { !java.lang.reflect.Modifier.isStatic(it.modifiers) }
        assertEquals(properties, SETTING_FIELDS.size, "a setting missing from SETTING_FIELDS would never be stored")
        assertEquals(SETTING_FIELDS.size, SETTING_FIELDS.map { it.key.name }.toSet().size)
        val changed = Settings(
            homepage = Homepage.WATCHED, theme = ThemeMode.DARK, palette = "whirl", layout = "compact",
            nightStart = LocalTime.of(21, 30), nightEnd = LocalTime.of(6, 15), postFontSize = PostFontSize.LARGE,
            textScale = TextScale.LARGER, screenAnimations = false, twoPane = true, twoPaneLandscapeOnly = true,
            browserApp = true, watchedNotify = true, notifyIntervalMinutes = 15, notifyStyle = NotifyStyle.PER_THREAD,
            notifyKeepUntilRead = true, openThreadsInBrowser = true, loadPublicAtBottom = true, loadPrivateAtBottom = true,
            recentHistoryDays = 30, loadRecentAtTop = true, ignoreOwnRepliesRecent = true, loadWatchedAtTop = true,
            ignoreOwnRepliesWatched = true, watchedAutoMarkAsRead = true, watchedBackToList = true, watchUndo = false,
            undoSeconds = 10,
        )
        settings.update { changed }
        assertEquals(changed, settings.settings.first())
        assertEquals(SETTING_FIELDS.size, store.data.first().asMap().size, "every one differs from its default, so every one is stored")
    }

    @Test
    fun `a stored value this version cannot read falls back to the default`() = runTest {
        store.edit {
            it[stringPreferencesKey("night_start")] = "25:99"
            it[stringPreferencesKey("theme")] = "SEPIA"
            it[stringPreferencesKey("homepage")] = Homepage.NEWS.name
        }
        val s = settings.settings.first()
        assertEquals(Settings().nightStart, s.nightStart)
        assertEquals(Settings().theme, s.theme)
        assertEquals(Homepage.NEWS, s.homepage, "the rest still read")
    }

    @Test
    fun `settings changed at the same time all take effect`() = runTest {
        withContext(Dispatchers.Default) {
            repeat(20) { launch { settings.update { it.copy(undoSeconds = it.undoSeconds + 1) } } }
        }
        assertEquals(Settings().undoSeconds + 20, settings.settings.first().undoSeconds)
    }

    @Test
    fun `only settings changed from their default are stored`() = runTest {
        suspend fun stored() = store.data.first().asMap().keys.map { it.name }.toSet()
        settings.update { it.copy(theme = ThemeMode.DARK) }
        assertEquals(setOf("theme"), stored())
        settings.update { it.copy(theme = Settings().theme) }
        assertEquals(emptySet(), stored(), "back to the default, so a new default would reach this user")

        // v1.0's wallpaper switch turns into the palette it chose.
        store.edit { it[booleanPreferencesKey("dynamic_color")] = false }
        settings.update { it.copy(theme = ThemeMode.DARK) }
        assertEquals(setOf("theme", "palette"), stored())
        assertEquals("whirl", settings.settings.first().palette)
    }

    @Test
    fun `signing out while Recent is being fetched stores nothing`() = runTest {
        val generation = AccountGeneration()
        val account = AccountRepository(api, settings, db, watchQueue(generation, clock, backgroundScope))
        val recent = RecentRepository(api, db.threadDao(), db.fetchLogDao(), RoomTransactor(db), generation, clock)
        settings.signIn("k", Account(216903, "d@llin"))
        val release = CountDownLatch(1)
        // get=recent has only been seen empty; this assumes the THREADS shape, as ThreadListRepositoryTest does.
        val body = File(System.getProperty("whirl.fixtures"), "api/api_threads_forum92.json").readText().replace("\"THREADS\"", "\"RECENT\"")
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                release.await(5, TimeUnit.SECONDS)
                return MockResponse.Builder().body(body).build()
            }
        }
        val refresh = async(Dispatchers.IO) { runCatching { recent.refresh(7) } }
        server.takeRequest(5, TimeUnit.SECONDS)!!
        account.signOut()
        release.countDown()
        // Dropped, and fetched again for the next account: there is none.
        assertIs<WhirlpoolError.BadKey>(refresh.await().exceptionOrNull())
        assertEquals(1, server.requestCount)
        assertEquals(emptyList(), recent.threads(7).first().value)
        assertNull(db.fetchLogDao().fetchedAt("recent:7"))
    }

    @Test
    fun `settings stored at their defaults before v1_7 are dropped once`() = runTest {
        // What v1.6 stored after a change of theme: every setting, the rest at their defaults.
        val oldScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val file = File(tmp.root, "old.preferences_pb")
        val old = PreferenceDataStoreFactory.create(scope = oldScope) { file }
        SettingsRepository(old).update { it.copy(theme = ThemeMode.DARK) }
        old.edit { p ->
            p[stringPreferencesKey("homepage")] = Settings().homepage.name
            p[intPreferencesKey("undo_seconds")] = Settings().undoSeconds
            p[booleanPreferencesKey("watch_undo")] = Settings().watchUndo
        }
        oldScope.coroutineContext.job.cancelAndJoin() // one DataStore per file at a time

        val migrated = PreferenceDataStoreFactory.create(scope = dataStoreScope, migrations = listOf(DropStoredDefaults)) { file }
        val settings = SettingsRepository(migrated)
        assertEquals(Settings(theme = ThemeMode.DARK), settings.settings.first())
        assertEquals(setOf("theme", "stored_defaults_dropped"), migrated.data.first().asMap().keys.map { it.name }.toSet())
    }
}
