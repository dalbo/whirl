package au.howesthat.whirl.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.model.WatchAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DaoTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val threads = db.threadDao()
    private val forums = db.forumDao()

    @After
    fun close() = db.close()

    private fun api(id: String, unread: Boolean = false) = ApiThreadColumns(
        id, "Thread $id", 92, "Choosing an ISP", null, 40, 1, "a", 1000, 2, "b", 2000, unread,
    )

    private fun watched(id: String, unread: Int) = WatchedThreadColumns(
        id, "Thread $id", 92, "Choosing an ISP", 40, 1, "a", 1000, 2, "b", 3000, unread, 41 - unread, 3,
    )

    @Test
    fun `lists keep their own order`() = runTest {
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("3rvvvlwj"), api("988lwp10"), api("1000422")))
        assertEquals(listOf("3rvvvlwj", "988lwp10", "1000422"), threads.observeSource(ThreadSource.LATEST, "").first().map { it.id })
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("1000422"), api("3rvvvlwj")))
        assertEquals(listOf("1000422", "3rvvvlwj"), threads.observeSource(ThreadSource.LATEST, "").first().map { it.id })
    }

    @Test
    fun `a thread that drops off every list is deleted`() = runTest {
        threads.replaceWatchedList("all", listOf(watched("3rvvvlwj", unread = 5)))
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("3rvvvlwj"), api("988lwp10")))
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("1000422")))
        assertNull(threads.get("988lwp10"), "on no list any more")
        assertEquals(5, threads.get("3rvvvlwj")?.unreadCount, "still watched")
        assertEquals("Thread 1000422", threads.get("1000422")?.title)
    }

    @Test
    fun `a thread named by a queued action is kept while it is on no list`() = runTest {
        // An Unwatch queued by an earlier run of the app: its thread is on no list until it is sent or fails.
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("3rvvvlwj"), api("988lwp10")))
        db.pendingWatchDao().insert(PendingWatchEntity(action = WatchAction.UNWATCH, ids = listOf("x1", "988lwp10"), sendAfter = 0, createdAt = 0))
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("1000422")))
        assertEquals("Thread 988lwp10", threads.get("988lwp10")?.title)
        assertNull(threads.get("3rvvvlwj"))
    }

    @Test
    fun `sources merge into one thread row without clobbering each other`() = runTest {
        threads.replaceWatchedList("all", listOf(watched("3rvvvlwj", unread = 5)))
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("3rvvvlwj", unread = true)))
        val row = threads.get("3rvvvlwj")!!
        assertEquals(5, row.unreadCount, "the API list does not reset watched columns")
        assertEquals(3, row.lastPage)
        assertEquals(true, row.isUnread)
        assertEquals(2000L, row.lastDate, "the newer upsert wins for shared columns")
        assertEquals(1, threads.observeSource(ThreadSource.WATCHED, "all").first().size)
    }

    @Test
    fun `mark read and remove from the unread list`() = runTest {
        threads.replaceWatchedList("unread", listOf(watched("a", 5), watched("b", 2)))
        threads.replaceWatchedList("all", listOf(watched("a", 5), watched("b", 2)))
        threads.markRead(listOf("a"))
        threads.removeFromSourceKey(ThreadSource.WATCHED, "unread", listOf("a"))
        val a = threads.get("a")!!
        assertEquals(0, a.unreadCount)
        assertEquals(41, a.lastReadPost)
        assertEquals(listOf("b"), threads.observeSource(ThreadSource.WATCHED, "unread").first().map { it.id })
        assertEquals(2, threads.observeSource(ThreadSource.WATCHED, "all").first().size)
    }

    @Test
    fun `watched ids, source rows and positions for undo`() = runTest {
        threads.replaceWatchedList("unread", listOf(watched("a", 5)))
        threads.replaceWatchedList("all", listOf(watched("a", 5), watched("b", 0)))
        assertEquals(setOf("a", "b"), threads.observeWatchedIds().first().toSet())
        assertEquals(1, threads.maxPosition(ThreadSource.WATCHED, "all"))
        assertNull(threads.maxPosition(ThreadSource.WATCHED, "nothing"))
        val rows = threads.sourceRows(ThreadSource.WATCHED, listOf("a"))
        assertEquals(setOf("unread" to 0, "all" to 0), rows.map { it.key to it.position }.toSet())

        val before = threads.getAll(listOf("a", "missing"))
        assertEquals(listOf("a"), before.map { it.id })
        threads.markRead(listOf("a"))
        threads.upsert(before)
        assertEquals(5, threads.get("a")!!.unreadCount, "a full upsert restores the row")
    }

    @Test
    fun `notified keeps only threads still unread`() = runTest {
        val notified = db.notifiedDao()
        notified.put(listOf(NotifiedEntity("a", 1), NotifiedEntity("b", 2)))
        notified.put(listOf(NotifiedEntity("a", 3)))
        notified.keepOnly(listOf("a"))
        assertEquals(listOf(NotifiedEntity("a", 3)), notified.all())
        notified.keepOnly(emptyList())
        assertEquals(emptyList(), notified.all())
    }

    @Test
    fun `forum refresh keeps favourites and learned privacy`() = runTest {
        forums.replaceAll(listOf(ForumEntity(92, "Choosing an ISP", "ISPs", 0), ForumEntity(35, "Feedback", "Whirlpool", 1)))
        forums.addFavourite(FavouriteForumEntity(92, 1))
        forums.setPrivate(35, true)
        forums.replaceAll(listOf(ForumEntity(35, "Feedback", "Whirlpool", 0), ForumEntity(92, "Choosing an ISP", "ISPs", 1)))
        val rows = forums.observeAll().first()
        assertEquals(listOf(35, 92), rows.map { it.id })
        assertTrue(rows.single { it.id == 92 }.isFavourite)
        assertTrue(rows.single { it.id == 35 }.isPrivate)
        forums.removeFavourite(92)
        assertTrue(forums.observeAll().first().none { it.isFavourite })
    }

    @Test
    fun `clearing account data drops watched lists but keeps public ones`() = runTest {
        threads.replaceWatchedList("all", listOf(watched("w", 1)))
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("l")))
        db.fetchLogDao().put(FetchLogEntity("watched:all", 1))
        db.clearAccountData()
        assertEquals(emptyList(), threads.observeSource(ThreadSource.WATCHED, "all").first())
        assertNull(threads.get("w"))
        assertEquals(1, threads.observeSource(ThreadSource.LATEST, "").first().size)
        assertNull(db.fetchLogDao().fetchedAt("watched:all"))
    }

    @Test
    fun `a queued action can be undone only until it is claimed for sending`() = runTest {
        val queue = db.pendingWatchDao()
        val a = queue.insert(PendingWatchEntity(action = WatchAction.MARK_READ, ids = listOf("a", "b"), sendAfter = 500, createdAt = 0))
        val b = queue.insert(PendingWatchEntity(action = WatchAction.UNWATCH, ids = listOf("c"), sendAfter = 500, createdAt = 0))
        assertEquals(listOf(listOf("a", "b"), listOf("c")), queue.all().map { it.ids })

        assertEquals(1, queue.claim(a))
        assertEquals(0, queue.claim(a), "claimed once")
        assertEquals(0, queue.deleteWaiting(a), "too late to undo")
        assertEquals(1, queue.deleteWaiting(b))

        queue.release(a, listOf("b"))
        val row = queue.all().single()
        assertEquals(listOf("b"), row.ids)
        assertEquals(PendingState.WAITING, row.state)
        assertEquals(1, row.attempts)

        queue.makeDue(a, now = 100)
        queue.makeDue(a, now = 900)
        assertEquals(100, queue.all().single().sendAfter, "never later than it was")
        queue.claim(a)
        queue.resetSending()
        assertEquals(PendingState.WAITING, queue.all().single().state)
    }
}
