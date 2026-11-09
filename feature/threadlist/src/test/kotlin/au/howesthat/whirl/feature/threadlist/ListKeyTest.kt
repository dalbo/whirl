package au.howesthat.whirl.feature.threadlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.AccountGeneration
import au.howesthat.whirl.core.data.AppVisits
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.ForumThreadsRepository
import au.howesthat.whirl.core.data.RecentRepository
import au.howesthat.whirl.core.data.ScrapedList
import au.howesthat.whirl.core.data.ScrapedListRepository
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchQueue
import au.howesthat.whirl.core.data.WatchSendScheduler
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.data.WatchedStore
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import au.howesthat.whirl.core.network.WhirlpoolWeb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Dns
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Screens that show different lists in one list body name each one ([ListState.listKey]), so ThreadListBody starts a
 * new one at the top (ThreadListScrollTest). Fetches fail without a request: no key, and no host resolves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListKeyTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val client = WhirlpoolHttp.client("Whirl/test").newBuilder().dns(Dns { throw UnknownHostException(it) }).build()
    private val api = WhirlpoolApi(client, { null }, retry = RetryPolicy.NONE)
    private val web = WhirlpoolWeb(client, retry = RetryPolicy.NONE)
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = WatchedStore(db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), clock)
    private val lists = WatchedRepository(api, store)
    private val forums = ForumRepository(api, db.forumDao(), db.fetchLogDao(), clock)
    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
    private val deps = ListDeps(settings, forums, lists, WatchQueue(api, store, lists, WatchSendScheduler.None, Dispatchers.Default, appScope), SavedStateHandle())
    private val scraped = ScrapedListRepository(web, forums, clock, Dispatchers.Default)

    private val viewModels = ViewModelStore()
    private val created = mutableListOf<ViewModel>()
    private var count = 0

    private inline fun <reified T : ViewModel> held(noinline create: () -> T): T =
        ViewModelProvider.create(viewModels, viewModelFactory { initializer { create() } })["vm${count++}", T::class].also { created += it }

    /** Waits in wall-clock time (Room and the failing fetches do real I/O) for a state with [key]. */
    private fun Flow<ListState>.awaitKey(key: Any?) = runBlocking {
        withContext(Dispatchers.Default) { withTimeout(10_000) { first { it.listKey == key } } }
    }

    @Before
    fun setUp() {
        // Settings live in one DataStore per JVM; read it once in wall-clock time.
        runBlocking { withTimeout(10_000) { settings.settings.first(); settings.account.first() } }
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        viewModels.clear()
        runBlocking { withTimeout(5_000) { created.forEach { it.viewModelScope.coroutineContext.job.join() } } }
        Dispatchers.resetMain()
        appScope.cancel()
        db.close()
    }

    @Test
    fun `Watched's tabs are different lists`() {
        val vm = held { WatchedViewModel(deps, AppVisits()) }
        vm.state.awaitKey("unread")
        vm.showUnreadOnly(false)
        vm.state.awaitKey("all")
    }

    @Test
    fun `each search, scope and page is a different list`() {
        val vm = held { SearchViewModel(SearchRoute("nbn", forumId = 92, scopeTitle = "Choosing an ISP"), deps, scraped) }
        val first = vm.search.value!!
        vm.state.awaitKey(first)
        vm.page(1)
        vm.state.awaitKey(first.copy(page = 2))
        vm.setScoped(false)
        vm.state.awaitKey(first.copy(forumId = null, groupId = null))
        vm.setText("adsl")
        vm.submit()
        vm.state.awaitKey(vm.search.value!!.also { check(it.query == "adsl") })
    }

    @Test
    fun `each group of a forum is a different list`() {
        val vm = held { ForumViewModel(ForumRoute(92, "Choosing an ISP"), deps, scraped, ForumThreadsRepository(api, db.threadDao(), db.fetchLogDao(), clock)) }
        val page = vm.page(1)
        page.awaitKey(null)
        vm.selectGroup(5)
        page.awaitKey(5)
    }

    @Test
    fun `each number of days in Recent is a different list`() {
        val before = runBlocking { settings.settings.first().recentHistoryDays }
        try {
            val vm = held {
                RecentViewModel(deps, RecentRepository(api, db.threadDao(), db.fetchLogDao(), RoomTransactor(db), AccountGeneration(), clock))
            }
            vm.state.awaitKey(before)
            val other = if (before == 1) 3 else 1
            vm.selectDays(other)
            vm.state.awaitKey(other)
        } finally {
            runBlocking { withTimeout(10_000) { settings.update { it.copy(recentHistoryDays = before) } } }
        }
    }
}
