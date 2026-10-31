package au.howesthat.whirl.feature.threadlist

import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.data.WatchedPosition
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val api = WhirlpoolApi(WhirlpoolHttp.client("t"), { null })
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val watched = WatchedRepository(api, db.threadDao(), db.fetchLogDao(), Clock.systemUTC(), appScope)
    private val deps = ListDeps(
        SettingsRepository(ApplicationProvider.getApplicationContext()),
        ForumRepository(api, db.forumDao(), db.fetchLogDao(), Clock.systemUTC()),
        watched,
        SavedStateHandle(),
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

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        appScope.cancel()
        db.close()
    }

    @Test
    fun `a refresh during a fetch replaces it instead of being dropped`() = TestScope(dispatcher).runTest {
        val vm = Blocking(deps)
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

    @Test
    fun `rows offer Watch, or Mark as read and Unwatch once watched`() = TestScope(dispatcher).runTest {
        val vm = Blocking(deps)
        val row = ThreadItem(ThreadId("3pxj858k"), ThreadId("3pxj858k"), "NBN", forum = ForumRef(92, "Choosing an ISP"))
        assertEquals(listOf(WatchAction.WATCH), vm.watchActions(row))
        assertEquals(emptyList(), vm.watchActions(row.copy(openId = null)), "deleted rows with nowhere to go")
        assertEquals(listOf(WatchAction.MARK_READ, WatchAction.UNWATCH), vm.watchActions(row.copy(unreadCount = 3, watched = WatchedPosition(1, 1))))

        watched.perform(WatchAction.WATCH, listOf(row.id))
        vm.watchedIds.first { row.id in it }
        assertEquals(listOf(WatchAction.UNWATCH), vm.watchActions(row))
    }

    @Test
    fun `the find query filters the list and is saved`() = TestScope(dispatcher).runTest {
        val vm = Blocking(deps)
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
        assertEquals("nbn", Blocking(deps).query.value, "a new ViewModel over the same saved state")

        vm.setQuery(null)
        assertEquals(2, vm.filter(list).first().sections!!.single().items.size)
    }
}
