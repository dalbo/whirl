package au.howesthat.whirl.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
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
        assertEquals(listOf("3rvvvlwj", "988lwp10", "1000422"), threads.observeSource("LATEST", "").first().map { it.id })
        threads.replaceApiList(ThreadSource.LATEST, "", listOf(api("1000422"), api("3rvvvlwj")))
        assertEquals(listOf("1000422", "3rvvvlwj"), threads.observeSource("LATEST", "").first().map { it.id })
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
        assertEquals(1, threads.observeSource("WATCHED", "all").first().size)
    }

    @Test
    fun `mark read and remove from the unread list`() = runTest {
        threads.replaceWatchedList("unread", listOf(watched("a", 5), watched("b", 2)))
        threads.replaceWatchedList("all", listOf(watched("a", 5), watched("b", 2)))
        threads.markRead(listOf("a"))
        threads.removeFromSourceKey("WATCHED", "unread", listOf("a"))
        val a = threads.get("a")!!
        assertEquals(0, a.unreadCount)
        assertEquals(41, a.lastReadPost)
        assertEquals(listOf("b"), threads.observeSource("WATCHED", "unread").first().map { it.id })
        assertEquals(2, threads.observeSource("WATCHED", "all").first().size)
    }

    @Test
    fun `watched ids, source rows and positions for undo`() = runTest {
        threads.replaceWatchedList("unread", listOf(watched("a", 5)))
        threads.replaceWatchedList("all", listOf(watched("a", 5), watched("b", 0)))
        assertEquals(setOf("a", "b"), threads.observeWatchedIds().first().toSet())
        assertEquals(1, threads.maxPosition("WATCHED", "all"))
        assertNull(threads.maxPosition("WATCHED", "nothing"))
        val rows = threads.sourceRows("WATCHED", listOf("a"))
        assertEquals(setOf("unread" to 0, "all" to 0), rows.map { it.key to it.position }.toSet())

        val before = threads.getAll(listOf("a", "missing"))
        assertEquals(listOf("a"), before.map { it.id })
        threads.markRead(listOf("a"))
        threads.upsert(before)
        assertEquals(5, threads.get("a")!!.unreadCount, "a full upsert restores the row")
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
        assertEquals(emptyList(), threads.observeSource("WATCHED", "all").first())
        assertNull(threads.get("w"))
        assertEquals(1, threads.observeSource("LATEST", "").first().size)
        assertNull(db.fetchLogDao().fetchedAt("watched:all"))
    }
}
