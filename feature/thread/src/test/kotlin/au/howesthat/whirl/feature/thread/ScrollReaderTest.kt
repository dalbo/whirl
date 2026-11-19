package au.howesthat.whirl.feature.thread

import au.howesthat.whirl.core.data.ArchiveRepository
import au.howesthat.whirl.core.model.ArchivePageRef
import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrollReaderTest {

    private fun post(n: Int) = Post(
        replyId = 1000L + n, number = n, author = UserRef(1, "a"), userGroup = null, quip = null, postCount = null,
        postedText = null, postedAt = null, editedText = null, isOp = false, hiddenReason = null, isDeleted = false,
        shortUrl = null, bodyHtml = "",
    )

    private fun page(index: Int, vararg numbers: Int) =
        ScrollPage(index, "/archive/t-${index + 1}", numbers.map { UiPost(post(it), PostBody(emptyList())) })

    private val thread = ArchiveRepository.ArchiveThread(
        ThreadId("t"), "T", null,
        listOf(ArchivePageRef("/archive/1", "A"), ArchivePageRef("/archive/t-2", "B"), ArchivePageRef("/archive/t-3", "C")),
    )

    @Test
    fun `a request finds its post, or the next one when the archive left it out`() {
        val posts = listOf(post(5), post(6), post(9))
        assertEquals(1, ScrollRequest(number = 6).indexIn(posts))
        assertEquals(2, ScrollRequest(number = 7).indexIn(posts), "7 was hidden: the next post")
        assertEquals(2, ScrollRequest(number = 50).indexIn(posts), "past the end: the last post")
        assertEquals(0, ScrollRequest(replyId = 1005).indexIn(posts))
        assertEquals(2, ScrollRequest(last = true).indexIn(posts))
        assertNull(ScrollRequest(replyId = 1).indexIn(posts))
        assertNull(ScrollRequest(last = true).indexIn(emptyList()))
    }

    @Test
    fun `routes open at a post, or the newest`() {
        assertEquals(41, ThreadRoute("t", 3).scrollStart())
        assertEquals(1, ThreadRoute("t").scrollStart())
        assertEquals(57, ThreadRoute("t", 3, scrollToPost = 57).scrollStart())
        assertNull(ThreadRoute("t", -1).scrollStart())
    }

    @Test
    fun `switching modes keeps the post`() {
        val route = ThreadRoute("t", 1, scrollToReply = 99, fromWatched = true, lastReadPost = 10)
        assertEquals(ThreadRoute("t", 3, scrollToPost = 57, fromWatched = true, lastReadPost = 10), route.switchedTo(57))
        assertEquals(ThreadRoute("t", -1, fromWatched = true, lastReadPost = 10), route.switchedTo(null))
    }

    @Test
    fun `the window holds three pages, dropping the far end`() {
        val w = listOf(page(5, 4001), page(6, 4801), page(7, 5601))
        assertEquals(listOf(4, 5, 6), grow(w, page(4, 3201), earlier = true).map { it.index })
        assertEquals(listOf(6, 7, 8), grow(w, page(8, 6401), earlier = false).map { it.index })
        assertEquals(listOf(1, 2), grow(listOf(page(2, 801)), page(1, 1), earlier = true).map { it.index })
    }

    @Test
    fun `rows show gaps, the new posts marker, load buttons and mark as read at the end`() {
        val middle = ScrollUiState(thread = thread, window = listOf(page(1, 801, 802, 805)))
        val rows = scrollRows(middle, firstUnread = 802, markReadButton = true)
        assertEquals(
            listOf(
                ScrollRow.Status,
                ScrollRow.Earlier(1, 3, "A"),
                ScrollRow.PostRow(middle.posts[0]),
                ScrollRow.NewPosts,
                ScrollRow.PostRow(middle.posts[1]),
                ScrollRow.Gap(2, 805),
                ScrollRow.PostRow(middle.posts[2]),
                ScrollRow.Later(3, 3, "C"),
            ),
            rows,
        )

        val end = ScrollUiState(thread = thread, window = listOf(page(2, 1601)))
        assertEquals(ScrollRow.MarkRead, scrollRows(end, firstUnread = null, markReadButton = true).last())
        assertEquals(ScrollRow.PostRow(end.posts[0]), scrollRows(end, firstUnread = null, markReadButton = false).last())
    }

    @Test
    fun `filters round-trip through their codes`() {
        val all = listOf(
            ReaderFilter.Mine, ReaderFilter.Op, ReaderFilter.Moderators, ReaderFilter.Reps,
            ReaderFilter.User(27743, "Matt E.: Leaptel"), ReaderFilter.Text("5090 price"),
        )
        all.forEach { assertEquals(it, ReaderFilter.parse(it.code)) }
        assertEquals(ReaderFilter.User(5, ""), ReaderFilter.parse("u:5"))
        assertNull(ReaderFilter.parse("u:x:y"))
        assertNull(ReaderFilter.parse("t:  "))
        assertNull(ReaderFilter.parse("nope"))
        assertNull(ReaderFilter.parse(null))
        assertNull(ReaderFilter.Mine.scanQuery(myUserId = null), "no account, no posts by me")
    }

    @Test
    fun `a filtered reader shows the header and the matches only`() {
        val window = listOf(page(1, 801, 802, 805))
        val f = Filtered(ReaderFilter.Op, posts = window.single().posts.take(1), fromIndex = 1)
        val rows = scrollRows(ScrollUiState(thread = thread, window = window, filtered = f), firstUnread = 802, markReadButton = true)
        assertEquals(listOf(ScrollRow.Status, ScrollRow.FilterHeader(f, 3, 805), ScrollRow.PostRow(f.posts.single())), rows)
        assertEquals(801, f.fromPost)
    }

    @Test
    fun `find offers to search earlier pages`() {
        val middle = ScrollUiState(thread = thread, window = listOf(page(1, 801)))
        assertEquals(ScrollRow.SearchThread("cheap"), scrollRows(middle, null, false, findQuery = "cheap")[1])
        val first = ScrollUiState(thread = thread, window = listOf(page(0, 1)))
        assertTrue(scrollRows(first, null, false, findQuery = "cheap").none { it is ScrollRow.SearchThread }, "nothing earlier")
    }
}
