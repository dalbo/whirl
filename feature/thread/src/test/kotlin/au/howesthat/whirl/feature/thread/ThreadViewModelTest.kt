package au.howesthat.whirl.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThreadPageRepository
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.network.WhirlpoolWeb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadViewModelTest {

    private val server = MockWebServer().apply { start() }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val client = WhirlpoolHttp.client("Whirl/test")
    private val clock = Clock.systemUTC()
    private val forums = ForumRepository(WhirlpoolApi(client, { null }, server.url("/api/"), RetryPolicy.NONE), db.forumDao(), db.fetchLogDao(), clock)
    private val repo = ThreadPageRepository(WhirlpoolWeb(client, WhirlpoolUrls(server.url("/")), RetryPolicy.NONE), db.threadPageDao(), forums, clock)
    private val settings = SettingsRepository(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        server.close()
        db.close()
    }

    private fun enqueueHtml(name: String) =
        server.enqueue(MockResponse.Builder().body(File(System.getProperty("whirl.fixtures"), "html/$name").readText()).build())

    private fun viewModel(vararg args: Pair<String, Any?>) = ThreadViewModel(SavedStateHandle(mapOf(*args)), repo, settings)

    // Room and MockWebServer do real I/O, so wait in wall-clock time, not runTest's virtual time.
    private suspend fun <T> await(block: suspend () -> T): T =
        withContext(Dispatchers.Default.limitedParallelism(1)) { withTimeout(10_000) { block() } }

    @Test
    fun `the last page resolves through the server`() = runTest {
        enqueueHtml("thread_97r5wzn3_last.html")
        val vm = viewModel("threadId" to "97r5wzn3", "page" to -1)
        assertEquals(2290, await { vm.startPage.filterNotNull().first() })
        assertEquals("/thread/97r5wzn3?p=-1", server.takeRequest().target)
        assertEquals(2290, vm.pageCount.value)
        assertEquals(17, await { vm.page(2290).filterNotNull().first() }.posts.size)
    }

    @Test
    fun `a quote on the same page scrolls without fetching`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        val vm = viewModel("threadId" to "3pxj858k", "page" to 2)
        val page = await { vm.page(2).filterNotNull().first() }
        val quoted = page.posts[3].post
        vm.openQuote(quoted.replyId, page)
        assertEquals(ScrollTarget(2, replyId = quoted.replyId), vm.scrollTarget.value)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `links route in the app or to the browser`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        val vm = viewModel("threadId" to "3pxj858k", "page" to 2)
        await { vm.startPage.filterNotNull().first() }
        vm.navigation.test {
            vm.openLink("https://forums.whirlpool.net.au/thread/97r5wzn3?p=4#r61")
            assertEquals(ThreadEvent.OpenThread(ThreadRoute("97r5wzn3", 4, scrollToPost = 61)), awaitItem())
            vm.openLink("https://forums.whirlpool.net.au/user/30703")
            assertEquals(ThreadEvent.OpenUser(30703), awaitItem())
            vm.openLink("https://forums.whirlpool.net.au/forum/7?g=179")
            assertEquals(ThreadEvent.OpenForum(ForumRef(7, "", 179)), awaitItem())
            vm.openLink("https://whirlpool.net.au/wiki/nbn")
            assertEquals(ThreadEvent.Browser("https://whirlpool.net.au/wiki/nbn"), awaitItem())
            vm.openLink("https://forums.whirlpool.net.au/archive/1000422")
            assertEquals(ThreadEvent.OpenThread(ThreadRoute("1000422")), awaitItem())
        }
    }

    @Test
    fun `a link into the same thread moves the pager`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        val vm = viewModel("threadId" to "3pxj858k", "page" to 2)
        await { vm.startPage.filterNotNull().first() }
        vm.openLink("https://forums.whirlpool.net.au/thread/3pxj858k?p=7#r130")
        assertEquals(7, await { vm.jumpTo.filterNotNull().first() })
        assertEquals(ScrollTarget(7, postNumber = 130), vm.scrollTarget.value)
    }

    @Test
    fun `a quote on another page is located through the site`() = runTest {
        enqueueHtml("thread_3pxj858k_p2.html")
        val vm = viewModel("threadId" to "3pxj858k", "page" to 1)
        await { vm.startPage.filterNotNull().first() }
        server.takeRequest()
        enqueueHtml("thread_3pxj858k_p2.html")
        vm.openQuote(75543951, onPage = null)
        assertEquals(2, await { vm.jumpTo.filterNotNull().first() })
        assertEquals(ScrollTarget(2, postNumber = 21), vm.scrollTarget.value)
        assertEquals("/forum/?action=replies&r=75543951", server.takeRequest().target)
    }
}
