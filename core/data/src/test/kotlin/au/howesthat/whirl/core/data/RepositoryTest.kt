package au.howesthat.whirl.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.FetchLogEntity
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A clock tests can move. */
class MutableClock(var now: Instant = Instant.parse("2026-10-20T09:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("Australia/Sydney")
    override fun withZone(zone: ZoneId?) = this
    override fun instant(): Instant = now
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val clock = MutableClock()
    // DataStore needs a scope that actually runs; a TestScope that nobody advances would hang every read.
    private val dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings by lazy {
        SettingsRepository(PreferenceDataStoreFactory.create(scope = dataStoreScope) { tmp.newFile("s.preferences_pb").also { it.delete() } })
    }
    private val api by lazy {
        WhirlpoolApi(WhirlpoolHttp.client("Whirl/test"), settings::apiKey, server.url("/api/"), RetryPolicy.NONE)
    }

    @After
    fun tearDown() {
        server.close()
        db.close()
        dataStoreScope.cancel()
    }

    private fun enqueue(name: String, code: Int = 200) = server.enqueue(
        MockResponse.Builder().code(code).body(File(System.getProperty("whirl.fixtures"), "api/$name").readText()).build(),
    )

    @Test
    fun `sign in stores the key and account, bad key stores nothing`() = runTest {
        val account = AccountRepository(api, settings, db)
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
    fun `night window round trip`() = runTest {
        settings.update { it.copy(theme = ThemeMode.DARK_AT_NIGHT, nightStart = LocalTime.of(15, 5), nightEnd = LocalTime.of(6, 30)) }
        val s = settings.settings.first()
        assertEquals(LocalTime.of(15, 5), s.nightStart)
        assertEquals(LocalTime.of(6, 30), s.nightEnd)
    }

    @Test
    fun `watched list refreshes and respects max age`() = runTest {
        settings.signIn("k", Account(216903, "d@llin"))
        val repo = WatchedRepository(api, db.threadDao(), db.fetchLogDao(), clock, backgroundScope)
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
}
