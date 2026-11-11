package au.howesthat.whirl.feature.threadlist

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.data.AccountGeneration
import au.howesthat.whirl.core.data.ForumRepository
import au.howesthat.whirl.core.data.LibraryRepository
import au.howesthat.whirl.core.data.OpenedFrom
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchQueue
import au.howesthat.whirl.core.data.WatchSendScheduler
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.data.WatchedStore
import au.howesthat.whirl.core.database.RoomTransactor
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.network.WhirlpoolApi
import au.howesthat.whirl.core.network.WhirlpoolHttp
import au.howesthat.whirl.core.ui.WhirlTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Scrolling when one list body shows different lists in turn (Watched's Unread and All tabs). */
@RunWith(RobolectricTestRunner::class)
class ThreadListScrollTest {
    @get:Rule val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-05T00:00:00Z"), ZoneOffset.UTC)
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val api = WhirlpoolApi(WhirlpoolHttp.client("t"), { null })
    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val store = WatchedStore(db.threadDao(), db.fetchLogDao(), db.pendingWatchDao(), RoomTransactor(db), AccountGeneration(), clock)
    private val lists = WatchedRepository(api, store)
    private val deps = ListDeps(
        SettingsRepository(ApplicationProvider.getApplicationContext()),
        ForumRepository(api, db.forumDao(), db.fetchLogDao(), clock),
        lists,
        WatchQueue(api, store, lists, WatchSendScheduler.None, Dispatchers.Default, appScope),
        LibraryRepository(ApplicationProvider.getApplicationContext(), db.forumDao()), SavedStateHandle(),
    )
    private val viewModels = ViewModelStore()

    private class Plain(deps: ListDeps) : ListViewModel(deps, OpenedFrom.WATCHED) {
        override suspend fun fetch(force: Boolean) = Unit
    }

    private val nav = ThreadListNav(openThread = {}, openForum = {}, openSearch = {})

    @Before
    fun setUp() {
        // Settings live in one DataStore per JVM; read it once in wall-clock time.
        runBlocking { withTimeout(10_000) { deps.settings.settings.first(); deps.settings.account.first(); deps.library.mutedThreads.first() } }
    }

    @After
    fun tearDown() {
        viewModels.clear()
        appScope.cancel()
        db.close()
    }

    private fun thread(id: String, forum: ForumRef) =
        ThreadItem(id = ThreadId(id), openId = ThreadId(id), title = "Thread $id", forum = forum, lastPoster = "x", lastPostedText = "today")

    @Test
    fun `switching to another list starts it at the top`() {
        val forums = (1..12).map { ForumRef(it, "Forum $it") }
        // All: twelve forums of four threads, far taller than the screen. Unread: one thread in the last forum.
        val all = forums.map { f -> Section(f, (1..4).map { thread("t${f.id}x$it", f) }) }
        val unread = listOf(Section(forums.last(), listOf(thread("t12x1", forums.last()))))

        var state by mutableStateOf(ListState(unread, clock.instant(), listKey = "unread"))
        show { state }
        compose.onNodeWithText("Thread t12x1").assertIsDisplayed()

        // The same forum heading and thread are in All: the list must not stay on them.
        state = ListState(all, clock.instant(), listKey = "all")
        compose.onNodeWithText("Forum 1").assertIsDisplayed()
        compose.onNodeWithText("Thread t1x1").assertIsDisplayed()
    }

    @Test
    fun `the next page of results starts at the top, not at the page buttons`() {
        val forum = ForumRef(1, "Forum 1")
        fun page(n: Int) = listOf(Section(forum, (1..40).map { thread("p${n}x$it", forum) }))
        var state by mutableStateOf(ListState(page(1), clock.instant(), listKey = 1))
        show(footer = { item(key = "pages") { Text("Next page") } }) { state }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Next page"))
        compose.onNodeWithText("Next page").assertIsDisplayed()

        // Search's Next: the new page loads (nothing cached), then arrives.
        state = ListState(null, null, listKey = 2)
        compose.waitForIdle()
        state = ListState(page(2), clock.instant(), listKey = 2)
        compose.onNodeWithText("Thread p2x1").assertIsDisplayed()
    }

    @Test
    fun `a refresh of the same list keeps its place`() {
        val forum = ForumRef(1, "Forum 1")
        val threads = (1..40).map { thread("r$it", forum) }
        var state by mutableStateOf(ListState(listOf(Section(forum, threads)), clock.instant(), listKey = "all"))
        show { state }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Thread r30"))

        // A new reply moves r40 to the top; the list stays where it was.
        state = ListState(listOf(Section(forum, listOf(threads.last()) + threads.dropLast(1))), clock.instant(), listKey = "all")
        compose.onNodeWithText("Thread r30").assertIsDisplayed()
    }

    private fun show(footer: (LazyListScope.() -> Unit)? = null, state: () -> ListState) {
        val viewModel = ViewModelProvider.create(viewModels, viewModelFactory { initializer { Plain(deps) } })["list", Plain::class]
        compose.setContent {
            val current = state()
            WhirlTheme(darkTheme = false) {
                ThreadListBody(
                    sections = current.sections,
                    refreshing = false,
                    error = null,
                    onRefresh = {},
                    viewModel = viewModel,
                    nav = nav,
                    emptyText = "",
                    footer = footer,
                    listKey = current.listKey,
                )
            }
        }
    }
}
