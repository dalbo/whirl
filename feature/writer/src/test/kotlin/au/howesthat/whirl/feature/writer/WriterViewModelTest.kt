package au.howesthat.whirl.feature.writer

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.DraftRepository
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.Style
import au.howesthat.whirl.core.model.ThreadId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WriterViewModelTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val drafts = DraftRepository(db.draftDao(), Clock.systemUTC())
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = ViewModelStore()
    private val nbn = ThreadId("3pxj858k")

    // Unconfined, not a test dispatcher: the saves and the preview are debounced, and these tests wait in real time.
    @Before
    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
        appScope.cancel()
        db.close()
    }

    private fun viewModel(replyTo: Long? = null): WriterViewModel {
        val factory = viewModelFactory { initializer { WriterViewModel(WriterRoute(nbn.value, "NBN", replyTo), drafts, appScope, Dispatchers.Default) } }
        return ViewModelProvider.create(store, factory)[WriterViewModel::class.java]
    }

    /**
     * Waits in real time (the saves and the preview are debounced). With no Compose UI running nothing sends snapshot
     * apply notifications, which snapshotFlow needs to see an edit, so send them here.
     */
    private suspend fun until(check: suspend () -> Boolean) = withContext(Dispatchers.Default) {
        // Generous: under the full parallel test run a debounced save plus a Room write took over 5 s once.
        withTimeout(15_000) {
            while (!check()) {
                Snapshot.sendApplyNotifications()
                delay(20)
            }
        }
    }

    @Test
    fun `opens with the stored draft, saves as typed, and saves on close`() = runBlocking {
        drafts.save(nbn, "NBN", "stored")
        val vm = viewModel()
        until { vm.loaded.value }
        assertEquals("stored", vm.text.text.toString())

        vm.text.setTextAndPlaceCursorAtEnd("typed")
        until { drafts.get(nbn)?.text == "typed" }

        vm.text.setTextAndPlaceCursorAtEnd("last words")
        store.clear() // before the debounce: onCleared saves it
        until { drafts.get(nbn)?.text == "last words" }
    }

    @Test
    fun `a quote while the writer is open joins the text and is saved`() = runBlocking {
        val vm = viewModel()
        until { vm.loaded.value }
        vm.text.setTextAndPlaceCursorAtEnd("my reply")
        assertEquals(1, drafts.appendQuote(nbn, "NBN", "@1 A writes..."))
        assertEquals("my reply\n\n@1 A writes...", vm.text.text.toString())
        until { drafts.get(nbn)?.text == "my reply\n\n@1 A writes..." }
    }

    @Test
    fun `the preview is the text as the site shows it`() = runBlocking {
        val vm = viewModel()
        until { vm.loaded.value }
        vm.text.setTextAndPlaceCursorAtEnd("[*bold*] text")
        // Collecting subscribes the preview, which converts the text after its debounce.
        Snapshot.sendApplyNotifications()
        val body = withContext(Dispatchers.Default) { withTimeout(5_000) { vm.preview.first { it.blocks.isNotEmpty() } } }
        assertEquals(
            listOf(Block.Paragraph(listOf(Inline.Text("bold", Style(bold = true)), Inline.Text(" text")))),
            body.blocks,
        )
    }

    @Test
    fun `reply goes to the post it started from, else the thread, and delete removes the draft`() = runBlocking {
        assertEquals("https://forums.whirlpool.net.au/forum/?action=reply&t=3pxj858k", viewModel().replyUrl())
        store.clear()
        assertEquals("https://forums.whirlpool.net.au/forum/?action=reply&r=76888415", viewModel(replyTo = 76888415).replyUrl())

        drafts.save(nbn, "NBN", "gone soon")
        val vm = viewModel(replyTo = 76888415)
        until { vm.loaded.value }
        vm.deleteDraft()
        until { drafts.get(nbn) == null }
        assertNull(drafts.get(nbn))
    }
}
