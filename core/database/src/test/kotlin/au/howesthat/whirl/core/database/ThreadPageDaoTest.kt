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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadPageDaoTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val dao = db.threadPageDao()

    @After
    fun close() = db.close()

    private fun page(id: String, page: Int, at: Long, filter: String = "", count: Int = 13) =
        ThreadPageEntity(id, filter, page, count, "Title", 92, "Choosing an ISP", null, null, "", at, at)

    private fun post(id: String, page: Int, replyId: Long, num: Int, filter: String = "") = PostEntity(
        id, filter, page, replyId, num, 1, "user", null, null, null, "2025-Jun-23, 11:11 am", 0, null, false, null, false, null, "<p>hi</p>",
    )

    @Test
    fun `store replaces a page's posts`() = runTest {
        dao.store(page("a", 2, 1), listOf(post("a", 2, 10, 21), post("a", 2, 11, 22)), keep = 50)
        dao.store(page("a", 2, 2), listOf(post("a", 2, 12, 23)), keep = 50)
        assertEquals(listOf(12L), dao.observePosts("a", "", 2).first().map { it.replyId })
        assertEquals(2L, dao.fetchedAt("a", "", 2))
    }

    @Test
    fun `posts come back in thread order`() = runTest {
        dao.store(page("a", 1, 1), listOf(post("a", 1, 30, 3), post("a", 1, 10, 1), post("a", 1, 20, 2)), keep = 50)
        assertEquals(listOf(1, 2, 3), dao.observePosts("a", "", 1).first().map { it.replyNum })
    }

    @Test
    fun `filters are cached apart`() = runTest {
        dao.store(page("a", 1, 1), listOf(post("a", 1, 10, 1)), keep = 50)
        dao.store(page("a", 1, 2, filter = "m", count = 1), listOf(post("a", 1, 99, 40, filter = "m")), keep = 50)
        assertEquals(listOf(10L), dao.observePosts("a", "", 1).first().map { it.replyId })
        assertEquals(listOf(99L), dao.observePosts("a", "m", 1).first().map { it.replyId })
        assertEquals(13, dao.lastKnownPageCount("a", ""))
        assertEquals(1, dao.lastKnownPageCount("a", "m"))
    }

    @Test
    fun `eviction keeps the most recently opened pages and drops their posts`() = runTest {
        dao.store(page("a", 1, 1), listOf(post("a", 1, 10, 1)), keep = 2)
        dao.store(page("b", 1, 2), listOf(post("b", 1, 20, 1)), keep = 2)
        dao.touch("a", "", 1, at = 3)
        dao.store(page("c", 1, 4), listOf(post("c", 1, 30, 1)), keep = 2)
        assertEquals(2, dao.pageCount())
        assertNotNull(dao.observePage("a", "", 1).first())
        assertNull(dao.observePage("b", "", 1).first(), "b was opened least recently")
        assertEquals(emptyList(), dao.observePosts("b", "", 1).first())
    }
}
