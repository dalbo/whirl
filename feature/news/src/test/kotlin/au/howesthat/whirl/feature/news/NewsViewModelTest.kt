package au.howesthat.whirl.feature.news

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.NewsRepository
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.RetryPolicy
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NewsViewModelTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val body = File(System.getProperty("whirl.fixtures"), "api/api_news.json").readText()

    // The first [held] API calls wait until the test lets them go; later ones answer at once.
    @Volatile private var held = 1
    private val calls = AtomicInteger()
    private val firstHeld = CountDownLatch(1)
    private val release = CountDownLatch(1)
    private val secondCall = CountDownLatch(1)
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val n = calls.incrementAndGet()
        when (n) {
            1 -> firstHeld.countDown()
            2 -> secondCall.countDown()
        }
        if (n <= held) release.await(5, TimeUnit.SECONDS)
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody()).build()
    }.build()
    private val api = WhirlpoolApi(client, { "k" }, retry = RetryPolicy.NONE)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        release.countDown()
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `a pull to refresh while the list is loading is not dropped`() {
        val vm = NewsViewModel(NewsRepository(api, db.newsDao(), db.fetchLogDao(), Clock.systemUTC()), SavedStateHandle(), api)
        assertTrue(firstHeld.await(5, TimeUnit.SECONDS), "the first load is under way")
        vm.refresh()
        assertTrue(secondCall.await(2, TimeUnit.SECONDS), "the pull to refresh fetches while the first load is still held")
        release.countDown()
        runBlocking {
            withTimeout(5_000) { vm.refreshing.first { !it } }
        }
        assertEquals(2, calls.get())
        assertNull(vm.error.value, "the cancelled first load is not an error")
    }

    @Test
    fun `pulling to refresh again while a refresh is running sends nothing more`() {
        held = 2
        val vm = NewsViewModel(NewsRepository(api, db.newsDao(), db.fetchLogDao(), Clock.systemUTC()), SavedStateHandle(), api)
        assertTrue(firstHeld.await(5, TimeUnit.SECONDS))
        vm.refresh()
        assertTrue(secondCall.await(2, TimeUnit.SECONDS), "the pull to refresh replaces the opening load")
        vm.refresh()
        vm.refresh()
        release.countDown()
        runBlocking {
            withTimeout(5_000) { vm.refreshing.first { !it } }
        }
        assertEquals(2, calls.get(), "the repeated pulls joined the refresh already running")
    }
}
