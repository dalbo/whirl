package au.howesthat.whirl.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.AccountGeneration
import au.howesthat.whirl.core.data.ArchiveRepository
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadPageRepository
import au.howesthat.whirl.core.data.WatchQueue
import au.howesthat.whirl.core.data.WatchSendScheduler
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.data.WatchedStore
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.network.WhirlpoolWeb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Dns
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ScrollThreadViewModelTest {

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val client = WhirlpoolHttp.client("Whirl/test").newBuilder()
        .dns { host -> if (host == "localhost" || host == "127.0.0.1") Dns.SYSTEM.lookup(host) else throw UnknownHostException(host) }
        .build()
    private val clock = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC)
    private val api = WhirlpoolApi(client, { null }, server.url("/api/"), RetryPolicy.NONE)
    private val web = WhirlpoolWeb(client, WhirlpoolUrls(server.url("/")), RetryPolicy.NONE)
    private val forums = ForumRepository(api, db.forumDao(), db.fetchLogDao(), clock)
    private val pages = ThreadPageRepository(web, db.threadPageDao(), forums, clock, Dispatchers.Default)
    private val archive = ArchiveRepository(web, db.archiveDao(), pages, clock, Dispatchers.Default)
    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext())
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = WatchedStore(db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), clock)
    private val lists = WatchedRepository(api, store)
    private val queue = WatchQueue(api, store, lists, WatchSendScheduler.None, Dispatchers.Default, appScope)

    private val viewModels = ViewModelStore()
    private val created = mutableListOf<ScrollThreadViewModel>()
    private var count = 0

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        viewModels.clear()
        runBlocking { withTimeout(5_000) { created.forEach { it.viewModelScope.coroutineContext.job.join() } } }
        Dispatchers.resetMain()
        appScope.cancel()
        server.close()
        db.close()
    }

    private fun enqueue(name: String) =
        server.enqueue(MockResponse.Builder().body(File(System.getProperty("whirl.fixtures"), "html/$name").readText()).build())

    private fun viewModel(route: ThreadRoute, saved: SavedStateHandle = SavedStateHandle()): ScrollThreadViewModel {
        val factory = viewModelFactory {
            initializer { ScrollThreadViewModel(route, archive, pages, settings, lists, queue, saved, Dispatchers.Default) }
        }
        return ViewModelProvider.create(viewModels, factory)["vm${count++}", ScrollThreadViewModel::class].also { created += it }
    }

    private suspend fun <T> await(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }

    @Test
    fun `the last page opens the newest archive page at its newest post`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        val vm = viewModel(ThreadRoute("97r5wzn3", -1))
        val state = await { vm.state.first { it.window.isNotEmpty() } }
        assertEquals("Geforce RTX Discussion", state.title)
        assertEquals(57, state.window.single().index)
        assertEquals(524, state.posts.size)
        assertTrue(state.canLoadEarlier)
        assertTrue(state.atEnd)
        assertEquals(ScrollRequest(last = true), await { vm.scrollTo.filterNotNull().first() })
    }

    @Test
    fun `a post opens its archive page, and the saved top post wins after process death`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        val vm = viewModel(ThreadRoute("97r5wzn3", 2286, scrollToPost = 45710, fromWatched = true))
        assertEquals(ScrollRequest(number = 45710), await { vm.scrollTo.filterNotNull().first() })
        vm.setTopPost(45750)

        // Restored: the cache serves it, at the saved post rather than the route's.
        val restored = viewModel(ThreadRoute("97r5wzn3", 2286, scrollToPost = 45710), SavedStateHandle(mapOf("scrollAnchor" to 45750)))
        assertEquals(ScrollRequest(number = 45750), await { restored.scrollTo.filterNotNull().first() })
        assertEquals(45750, restored.topPost())
    }

    @Test
    fun `find matches posts in the window`() = runTest {
        enqueue("thread_97r5wzn3_last.html")
        enqueue("archive_97r5wzn3_last.html")
        val vm = viewModel(ThreadRoute("97r5wzn3", -1))
        await { vm.state.first { it.window.isNotEmpty() } }
        vm.setFindQuery("5090")
        val matches = await { vm.findMatches.filterNotNull().first() }
        assertTrue(matches.replyIds.isNotEmpty())
        val posts = vm.state.value.posts.associateBy { it.post.replyId }
        assertTrue(matches.replyIds.all { it in posts })
    }
}
