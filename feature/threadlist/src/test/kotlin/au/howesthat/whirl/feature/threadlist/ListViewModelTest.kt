package au.howesthat.whirl.feature.threadlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.AccountGeneration
import au.howesthat.whirl.core.data.OpenThread
import au.howesthat.whirl.core.data.AppVisits
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.LibraryRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.data.WatchEvent
import au.howesthat.whirl.core.data.WatchedPosition
import au.howesthat.whirl.core.data.WatchSendScheduler
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.database.FetchLogEntity
import au.howesthat.whirl.core.database.WatchedThreadColumns
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.RetryPolicy
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import au.howesthat.whirl.core.data.WatchedStore
import au.howesthat.whirl.core.data.WatchQueue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListViewModelTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC)
    private val dispatcher = StandardTestDispatcher()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val api = WhirlpoolApi(WhirlpoolHttp.client("t"), { null })
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = WatchedStore(db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), clock)
    private val lists = WatchedRepository(api, store)
    private val watched = WatchQueue(api, store, lists, WatchSendScheduler.None, Dispatchers.Default, appScope)
    private val deps = ListDeps(
        SettingsRepository(ApplicationProvider.getApplicationContext()),
        ForumRepository(api, db.forumDao(), db.fetchLogDao(), clock),
        lists,
        watched,
        LibraryRepository(ApplicationProvider.getApplicationContext(), db.forumDao()), SavedStateHandle(), api,
    )

    /** A list whose fetches wait until the test completes them. */
    private class Blocking(deps: ListDeps) : ListViewModel(deps, OpenedFrom.LIST) {
        fun filter(state: ListState) = flowOf(state).filtered()

        val calls = mutableListOf<CompletableDeferred<Unit>>()
        var cancelled = 0
        override suspend fun fetch(force: Boolean) {
            val gate = CompletableDeferred<Unit>()
            calls += gate
            try {
                gate.await()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    /**
     * The view models made here, cleared after each test. Settings live in one DataStore for the whole JVM: a view
     * model left collecting them on a finished test's dispatcher could hold its first read open, and every later read
     * then waited forever.
     */
    private val viewModels = ViewModelStore()
    private var viewModelCount = 0

    private inline fun <reified T : ViewModel> held(noinline create: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { create() } })["vm${viewModelCount++}", T::class]

    @Before
    fun setUp() {
        // DataStore reads each file once (settings, account); do it here in wall-clock time, not on a test dispatcher.
        runBlocking { withTimeout(10_000) { deps.settings.settings.first(); deps.settings.account.first(); deps.library.mutedThreads.first() } }
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        viewModels.clear()
        Dispatchers.resetMain()
        appScope.cancel()
        db.close()
    }

    /** Answers every API call with [body] and [code]; a bad key is 403 with Whirlpool's error envelope. */
    private class Canned(var code: Int, var body: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = Response.Builder()
            .request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }

    private class Fetching(deps: ListDeps) : ListViewModel(deps, OpenedFrom.LIST) {
        var fetches = 0
        override suspend fun fetch(force: Boolean) {
            fetches++
            deps.api.forums()
        }
    }

    @Test
    fun `a list showing a rejected key fetches again once a new key is accepted`() = TestScope(dispatcher).runTest {
        val canned = Canned(403, """{"ALERT":"","AUTH":{"NAME":"","ID":0},"DOCS":"","ERROR":"Not authenticated. Bad API key?"}""")
        val keyed = WhirlpoolApi(OkHttpClient.Builder().addInterceptor(canned).build(), { "1234-5678-9012-34" }, retry = RetryPolicy.NONE)
        val vm = held { Fetching(ListDeps(deps.settings, deps.forums, deps.watched, deps.watchQueue, deps.library, SavedStateHandle(), keyed)) }
        vm.refresh()
        advanceUntilIdle()
        waitFor { vm.error.value is WhirlpoolError.BadKey }
        assertEquals(1, vm.fetches)

        canned.code = 200
        canned.body = """{"ALERT":"","AUTH":{"NAME":"d@llin","ID":216903},"DOCS":"","ERROR":""}"""
        keyed.user("8765-4321") // setup's check of the new key
        waitFor { vm.fetches == 2 }
    }

    /** The API reads on its own IO dispatcher, so the test waits in wall-clock time between steps. */
    private fun TestScope.waitFor(condition: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!condition()) { withContext(Dispatchers.Default) { kotlinx.coroutines.delay(10) }; testScheduler.advanceUntilIdle() } }
    }

    @Test
    fun `a refresh during a fetch replaces it instead of being dropped`() = TestScope(dispatcher).runTest {
        val vm = held { Blocking(deps) }
        vm.refresh(force = false)
        advanceUntilIdle()
        vm.refresh(force = false)
        advanceUntilIdle()
        assertEquals(2, vm.calls.size, "the second request ran")
        assertEquals(1, vm.cancelled, "the first was cancelled")
        assertTrue(vm.refreshing.value, "the cancelled fetch must not clear the indicator")

        vm.calls[1].complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.refreshing.value)
        assertNull(vm.error.value)
    }

    /**
     * Not runTest: nothing drives Main here, so the ViewModel's settings state keeps the defaults, as in the moment
     * after launch, while DataStore is waited for in wall-clock time.
     */
    @Test
    fun `a tap right after launch opens as the stored settings say, not as the defaults`(): Unit = runBlocking {
        withTimeout(10_000) { deps.settings.update { it.copy(openThreadsInBrowser = true) } }
        try {
            val vm = held { Blocking(deps) }
            val row = ThreadItem(ThreadId("3pxj858k"), ThreadId("3pxj858k"), "NBN", forum = ForumRef(92, "Choosing an ISP"))
            withTimeout(10_000) {
                assertIs<OpenThread.Browser>(vm.open(row))
                assertIs<OpenThread.Browser>(vm.lastPost(row))
            }
        } finally {
            withTimeout(10_000) { deps.settings.update { it.copy(openThreadsInBrowser = false) } }
        }
    }

    @Test
    fun `rows offer Watch, or Mark as read and Unwatch once watched`() = TestScope(dispatcher).runTest {
        val vm = held { Blocking(deps) }
        val row = ThreadItem(ThreadId("3pxj858k"), ThreadId("3pxj858k"), "NBN", forum = ForumRef(92, "Choosing an ISP"))
        assertEquals(listOf(WatchAction.WATCH), vm.watchActions(row))
        assertEquals(emptyList(), vm.watchActions(row.copy(openId = null)), "deleted rows with nowhere to go")
        assertEquals(listOf(WatchAction.MARK_READ, WatchAction.UNWATCH), vm.watchActions(row.copy(unreadCount = 3, watched = WatchedPosition(1, 1, 3))))

        watched.perform(WatchAction.WATCH, listOf(row.id))
        vm.watchedIds.first { row.id in it }
        assertEquals(listOf(WatchAction.UNWATCH), vm.watchActions(row))
    }

    @Test
    fun `a watched row offers Don't notify, then Notify again`() = TestScope(dispatcher).runTest {
        val vm = held { Blocking(deps) }
        val row = ThreadItem(ThreadId("3pxj858k"), ThreadId("3pxj858k"), "NBN", forum = ForumRef(92, "Choosing an ISP"))
        deps.library.setMuted(listOf(row.id), muted = false) // one library store per JVM
        assertNull(vm.muted(row), "not watched: nothing to mute")
        val watchedRow = row.copy(unreadCount = 3, watched = WatchedPosition(1, 1, 3))
        assertEquals(false, vm.muted(watchedRow))
        vm.setMuted(watchedRow, true)
        vm.mutedIds.first { row.id in it }
        assertEquals(true, vm.muted(watchedRow))
        vm.setMuted(watchedRow, false)
        vm.mutedIds.first { row.id !in it }
    }

    @Test
    fun `the find query filters the list and is saved`() = TestScope(dispatcher).runTest {
        val vm = held { Blocking(deps) }
        val isp = ForumRef(92, "Choosing an ISP")
        val list = ListState(
            listOf(Section(isp, listOf(ThreadItem(ThreadId("a"), ThreadId("a"), "NBN plans", forum = isp), ThreadItem(ThreadId("b"), ThreadId("b"), "Optus", forum = isp)))),
            updatedAt = null,
        )
        assertNull(vm.query.value, "the bar starts closed")
        assertEquals(2, vm.filter(list).first().sections!!.single().items.size)

        vm.setQuery("nbn")
        assertEquals(listOf("NBN plans"), vm.filter(list).first().sections!!.single().items.map { it.title })
        assertEquals("nbn", deps.savedState.get<String>("findQuery"), "kept in the saved state, for process death")
        assertEquals("nbn", held { Blocking(deps) }.query.value, "a new ViewModel over the same saved state")

        vm.setQuery(null)
        assertEquals(2, vm.filter(list).first().sections!!.single().items.size)
    }

    private fun watchedRow(id: String, title: String, unread: Int) = WatchedThreadColumns(
        id, title, 92, "Choosing an ISP", 40, 1, "a", 1000, 2, "b", 3000, unread, 41 - unread, 3,
    )

    @Test
    fun `mark all marks the unread threads shown, only the matching ones while finding`() = TestScope(dispatcher).runTest {
        val threads = db.threadDao()
        threads.replaceWatchedList("unread", listOf(watchedRow("a", "NBN plans", 3), watchedRow("b", "Bike lights", 2)))
        threads.replaceWatchedList("all", listOf(watchedRow("a", "NBN plans", 3), watchedRow("b", "Bike lights", 2), watchedRow("c", "Bike racks", 0)))
        db.fetchLogDao().put(FetchLogEntity("watched:unread", 1))
        db.fetchLogDao().put(FetchLogEntity("watched:all", 1))
        val vm = held { WatchedViewModel(deps, AppVisits()) }
        backgroundScope.launch { vm.markAllIds.collect {} }

        assertEquals(listOf(ThreadId("a"), ThreadId("b")), vm.markAllIds.first { it.isNotEmpty() })
        vm.setQuery("bike")
        assertEquals(listOf(ThreadId("b")), vm.markAllIds.first { it.size == 1 })
        // All shows a read thread matching too; only the unread one is marked.
        vm.showUnreadOnly(false)
        advanceUntilIdle()
        assertEquals(listOf(ThreadId("b")), vm.markAllIds.first { it.size == 1 })

        vm.setQuery(null)
        vm.markAllIds.first { it.size == 2 }
        val applied = async(start = CoroutineStart.UNDISPATCHED) { watched.events.filterIsInstance<WatchEvent.Applied>().first() }
        vm.markAllRead()
        val p = withContext(Dispatchers.IO) { withTimeout(5_000) { applied.await().pending } }
        assertEquals(WatchAction.MARK_READ, p.action)
        assertEquals(listOf(ThreadId("a"), ThreadId("b")), p.ids, "one action for all of them, with Undo")
    }
}
