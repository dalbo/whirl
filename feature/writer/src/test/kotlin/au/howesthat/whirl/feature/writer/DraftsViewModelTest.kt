package au.howesthat.whirl.feature.writer

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.DraftRepository
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.ThreadId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import kotlin.test.assertEquals

/** Drafts in the drawer: listed newest first; a delete can be undone (post writer, plan §6). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DraftsViewModelTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val drafts = DraftRepository(db.draftDao(), Clock.systemUTC())
    private val store = ViewModelStore()

    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        db.close()
    }

    @Test
    fun `lists the drafts, and a delete can be undone`() = runBlocking {
        drafts.save(ThreadId("a1"), "First", "one")
        Thread.sleep(5) // a later updated_at, so the order is certain
        drafts.save(ThreadId("b2"), "Second", "two")
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { DraftsViewModel(drafts) } })[DraftsViewModel::class.java]
        suspend fun titles() = withContext(Dispatchers.Default) { withTimeout(5_000) { vm.all.filterNotNull().first() } }.map { it.threadTitle }
        assertEquals(listOf("Second", "First"), titles())

        val second = vm.all.value!!.first()
        vm.delete(second)
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.all.first { it?.size == 1 } } }
        vm.restore(second)
        withContext(Dispatchers.Default) { withTimeout(5_000) { vm.all.first { it?.size == 2 } } }
        assertEquals("two", drafts.get(ThreadId("b2"))?.text)
    }
}
