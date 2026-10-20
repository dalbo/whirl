package au.howesthat.whirl.feature.threadlist

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
    private val deps = ListDeps(
        SettingsRepository(ApplicationProvider.getApplicationContext()),
        ForumRepository(WhirlpoolApi(WhirlpoolHttp.client("t"), { null }), db.forumDao(), db.fetchLogDao(), Clock.systemUTC()),
    )

    /** A list whose fetches wait until the test completes them. */
    private class Blocking(deps: ListDeps) : ListViewModel(deps, OpenedFrom.LIST) {
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
}
