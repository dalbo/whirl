package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.data.OpenThread.Browser
import au.howesthat.whirl.core.data.OpenThread.InApp
import au.howesthat.whirl.core.data.ThreadPageRepository.Companion.LAST_PAGE
import au.howesthat.whirl.core.model.ThreadId
import org.junit.Test
import kotlin.test.assertEquals

class ThreadOpenerTest {
    private val opener = ThreadOpener()
    private val id = ThreadId("3pxj858k")
    private val defaults = Settings()
    private val watched = WatchedPosition(lastPage = 3, lastReadPost = 47, unreadCount = 5)

    private fun open(from: OpenedFrom, settings: Settings = defaults, private: Boolean = false, w: WatchedPosition? = null) =
        opener.open(id, 92, private, from, settings, w)

    @Test
    fun `watched threads with unread posts open at the first unread post`() {
        // The list scrolls to it, which puts its "New posts" marker at the top of the screen.
        assertEquals(InApp(id, 3, 48, 92, fromWatched = true, lastReadPost = 47), open(OpenedFrom.WATCHED, w = watched))
        // The last read post ends page 2: the first unread one is on page 3, past LASTPAGE.
        assertEquals(InApp(id, 3, 41, 92, fromWatched = true, lastReadPost = 40), open(OpenedFrom.WATCHED, w = WatchedPosition(2, 40, 3)))
        // At the top, the new posts are still marked, and their page is the one that opens.
        val atTop = defaults.copy(loadWatchedAtTop = true)
        assertEquals(InApp(id, 3, null, 92, fromWatched = true, lastReadPost = 47), open(OpenedFrom.WATCHED, atTop, w = watched))
        assertEquals(InApp(id, 3, null, 92, fromWatched = true, lastReadPost = 40), open(OpenedFrom.WATCHED, atTop, w = WatchedPosition(2, 40, 3)))
    }

    @Test
    fun `the page follows LASTPAGE, not a page size of its own`() {
        // Whirlpool says which page the last read post is on; only a post ending a page moves the first unread one on.
        assertEquals(5, WatchedPosition(lastPage = 5, lastReadPost = 47, unreadCount = 2).openPage)
        assertEquals(6, WatchedPosition(lastPage = 5, lastReadPost = 100, unreadCount = 2).openPage)
        assertEquals(5, WatchedPosition(lastPage = 5, lastReadPost = 100, unreadCount = 0).openPage)
    }

    @Test
    fun `watched threads with nothing unread open at the last read post`() {
        assertEquals(InApp(id, 3, 47, 92, fromWatched = true, lastReadPost = 47), open(OpenedFrom.WATCHED, w = watched.copy(unreadCount = 0)))
        assertEquals(InApp(id, 2, 40, 92, fromWatched = true, lastReadPost = 40), open(OpenedFrom.WATCHED, w = WatchedPosition(2, 40, 0)))
        assertEquals(InApp(id, 1, null, 92, fromWatched = true), open(OpenedFrom.WATCHED, w = WatchedPosition(1, 0, 5)), "nothing read yet")
    }

    @Test
    fun `recent threads open at the last page unless set to the top`() {
        assertEquals(InApp(id, LAST_PAGE, forumId = 92), open(OpenedFrom.RECENT))
        assertEquals(InApp(id, 1, forumId = 92), open(OpenedFrom.RECENT, defaults.copy(loadRecentAtTop = true)))
    }

    @Test
    fun `public list threads open at the top unless set to the bottom`() {
        assertEquals(InApp(id, 1, forumId = 92), open(OpenedFrom.LIST))
        assertEquals(InApp(id, LAST_PAGE, forumId = 92), open(OpenedFrom.LIST, defaults.copy(loadPublicAtBottom = true)))
    }

    @Test
    fun `private forum threads open in the browser`() {
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k"), open(OpenedFrom.LIST, private = true))
        assertEquals(
            Browser("https://forums.whirlpool.net.au/thread/3pxj858k?p=-1#bottom"),
            open(OpenedFrom.LIST, defaults.copy(loadPrivateAtBottom = true), private = true),
        )
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k?p=3#r48"), open(OpenedFrom.WATCHED, private = true, w = watched))
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k?p=3#r41"), open(OpenedFrom.WATCHED, private = true, w = WatchedPosition(2, 40, 3)))
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k?p=3#r47"), open(OpenedFrom.WATCHED, private = true, w = watched.copy(unreadCount = 0)))
        // Nothing read yet: no post to jump to.
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k"), open(OpenedFrom.WATCHED, private = true, w = WatchedPosition(1, 0, 5)))
    }

    @Test
    fun `open in browser applies everywhere`() {
        val browser = defaults.copy(openThreadsInBrowser = true)
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k"), open(OpenedFrom.LIST, browser))
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k?p=-1#bottom"), open(OpenedFrom.RECENT, browser))
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k?p=3#r48"), open(OpenedFrom.WATCHED, browser, w = watched))
    }

    @Test
    fun `jump to last post`() {
        assertEquals(InApp(id, LAST_PAGE, forumId = 92), opener.lastPost(id, 92, false, defaults))
        assertEquals(Browser("https://forums.whirlpool.net.au/thread/3pxj858k?p=-1#bottom"), opener.lastPost(id, 35, true, defaults))
    }
}
