package au.howesthat.whirl.feature.thread

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
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import kotlinx.coroutines.Dispatchers
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

/** Quote in the post menu: the post goes into the thread's draft (post writer, plan §6). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ThreadDraftViewModelTest {
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

    private fun post(replyId: Long, name: String, text: String) = UiPost(
        Post(
            replyId = replyId, number = 1, author = UserRef(1, name), userGroup = null, quip = null, postCount = null,
            postedText = null, postedAt = null, editedText = null, isOp = false, hiddenReason = null, isDeleted = false,
            shortUrl = null, bodyHtml = "",
        ),
        PostBody(listOf(Block.Paragraph(listOf(Inline.Text(text))))),
    )

    @Test
    fun `each quote joins the thread's draft under the thread's title, and is counted`() = runBlocking {
        val vm = ViewModelProvider.create(store, viewModelFactory { initializer { ThreadDraftViewModel("3pxj858k", drafts) } })[ThreadDraftViewModel::class.java]
        vm.title = "NBN"
        withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                vm.quote(post(10, "Alice", "First point"))
                assertEquals(1, vm.quotes.first())
                vm.quote(post(11, "Bob", "Second point"))
                assertEquals(2, vm.quotes.first())
            }
        }
        val draft = drafts.get(ThreadId("3pxj858k"))!!
        assertEquals("NBN", draft.threadTitle)
        assertEquals("@10 Alice writes...\n\n[\"First point\"]\n\n@11 Bob writes...\n\n[\"Second point\"]", draft.text)
    }
}
