package au.howesthat.whirl.navigation

import au.howesthat.whirl.feature.thread.ThreadRoute
import au.howesthat.whirl.feature.threadlist.ForumRoute
import au.howesthat.whirl.feature.threadlist.SearchRoute
import au.howesthat.whirl.feature.user.UserRoute
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TopLevelBackStackTest {
    private val tops = TOP_LEVEL.map { it.route }
    private fun stack() = TopLevelBackStack(ForumsRoute, tops)

    @Test
    fun `screens push onto the start stack and pop off it`() {
        val s = stack()
        s.push(ForumRoute(7, "Desktops"))
        s.push(ThreadRoute("3pxj858k"))
        assertEquals(listOf(ForumsRoute, ForumRoute(7, "Desktops"), ThreadRoute("3pxj858k")), s.visible)
        assertTrue(s.pop())
        assertTrue(s.pop())
        assertEquals(listOf(ForumsRoute), s.visible)
        assertFalse(s.pop(), "only the start screen left: Back leaves the app")
    }

    @Test
    fun `a drawer destination opens above the start screen and keeps its stack`() {
        val s = stack()
        s.push(ForumRoute(7, "Desktops"))
        s.select(WatchedRoute)
        s.push(ThreadRoute("a"))
        assertEquals(listOf(ForumsRoute, WatchedRoute, ThreadRoute("a")), s.visible, "the start screen alone under Watched")

        s.select(NewsRoute)
        assertEquals(listOf(ForumsRoute, NewsRoute), s.visible)
        s.select(WatchedRoute)
        assertEquals(listOf(ForumsRoute, WatchedRoute, ThreadRoute("a")), s.visible, "Watched as it was left")
        s.select(WatchedRoute)
        assertEquals(ThreadRoute("a"), s.visible.last(), "choosing the destination on screen changes nothing")

        s.select(ForumsRoute)
        assertEquals(listOf(ForumsRoute, ForumRoute(7, "Desktops")), s.visible, "the start screen's stack as it was left")
    }

    @Test
    fun `Back from a drawer destination's own screen returns to the start stack`() {
        val s = stack()
        s.select(PopularRoute)
        assertTrue(s.pop())
        assertEquals(ForumsRoute, s.current)
        assertEquals(listOf(ForumsRoute), s.visible)
    }

    @Test
    fun `Settings push onto whichever stack is showing`() {
        val s = stack()
        s.select(WatchedRoute)
        s.push(SettingsRoute)
        assertEquals(listOf(ForumsRoute, WatchedRoute, SettingsRoute), s.visible)
        assertFalse(s.isTopLevel(SettingsRoute))
    }

    @Test
    fun `every route survives saving and restoring`() {
        val s = stack()
        s.push(ForumRoute(7, "Desktops", groupId = 179))
        s.push(ThreadRoute("3pxj858k", page = -1, scrollToReply = 123L, forumId = 7, fromWatched = true))
        s.push(UserRoute(216903))
        s.push(SearchRoute("nbn", forumId = 7, scopeTitle = "Desktops"))
        s.push(SettingsRoute)
        s.push(AboutRoute)
        s.select(RecentRoute)
        s.push(ThreadRoute("a"))

        val restored = TopLevelBackStack(ForumsRoute, tops, TopLevelBackStack.decode(TopLevelBackStack.encode(s.save())))
        assertEquals(RecentRoute, restored.current)
        assertEquals(s.visible, restored.visible)
        restored.select(ForumsRoute)
        s.select(ForumsRoute)
        assertEquals(s.visible, restored.visible)
    }

    @Test
    fun `an unreadable save starts afresh`() {
        val s = TopLevelBackStack(WatchedRoute, tops, TopLevelBackStack.decode("not json"))
        assertEquals(listOf(WatchedRoute), s.visible)
    }

    private val isList: (androidx.navigation3.runtime.NavKey) -> Boolean = { it == WatchedRoute || it is ForumRoute || it is UserRoute }

    @Test
    fun `a thread chosen in a list replaces the threads beside it`() {
        val s = stack()
        s.select(WatchedRoute)
        s.openFromList(ThreadRoute("a"), isList)
        assertEquals(listOf(ForumsRoute, WatchedRoute, ThreadRoute("a")), s.visible, "with the list on top it is a push")
        s.push(ThreadRoute("b"))
        s.openFromList(ThreadRoute("c"), isList)
        assertEquals(listOf(ForumsRoute, WatchedRoute, ThreadRoute("c")), s.visible, "a and the thread it linked to go")

        s.push(ForumRoute(7, "Desktops"))
        s.openFromList(ThreadRoute("d"), isList)
        assertEquals(listOf(ForumsRoute, WatchedRoute, ThreadRoute("c"), ForumRoute(7, "Desktops"), ThreadRoute("d")), s.visible)
    }

    @Test
    fun `threads know their list and lists know the thread beside them`() {
        val s = stack()
        s.select(WatchedRoute)
        s.openFromList(ThreadRoute("a"), isList)
        s.push(ThreadRoute("b"))
        s.push(UserRoute(1))
        assertEquals(WatchedRoute, s.listBelow(ThreadRoute("b"), isList))
        assertEquals(WatchedRoute, s.listBelow(UserRoute(1), isList))
        assertEquals(ThreadRoute("a"), s.above(WatchedRoute))
        assertEquals(null, s.above(UserRoute(1)))
        assertEquals(null, s.listBelow(ForumsRoute, isList))
    }
}
