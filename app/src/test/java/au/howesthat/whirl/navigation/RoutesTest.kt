package au.howesthat.whirl.navigation

import au.howesthat.whirl.core.data.Homepage
import org.junit.Test
import au.howesthat.whirl.feature.thread.ThreadRoute
import au.howesthat.whirl.feature.threadlist.ForumRoute
import au.howesthat.whirl.feature.threadlist.SearchRoute
import au.howesthat.whirl.feature.user.UserRoute
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutesTest {
    @Test
    fun `every start screen is a drawer destination`() {
        val drawerRoutes = TOP_LEVEL.map { it.route }
        Homepage.entries.forEach { assertTrue(it.route() in drawerRoutes, "$it has no drawer entry") }
        assertEquals(Homepage.entries.size, drawerRoutes.toSet().size)
    }

    @Test
    fun `links to drawer screens open like the drawer, others are pushed`() {
        val stack = TopLevelBackStack(Homepage.FORUMS.route(), TOP_LEVEL.map { it.route })
        assertTrue(stack.isTopLevel(WatchedRoute), "the notification's Watched link must not stack a second Watched")
        assertFalse(stack.isTopLevel(ThreadRoute("3pxj858k")), "a second thread link must push, not reuse the open thread")
        assertFalse(stack.isTopLevel(SettingsRoute))
    }

    @Test
    fun `the thread lists, and only they, are shown as the list beside a thread`() {
        val lists = listOf(WatchedRoute, RecentRoute, LatestRoute, PopularRoute, ForumRoute(7, "x"), SearchRoute("q"), UserRoute(1))
        lists.forEach { assertTrue(isListPane(it), "$it") }
        listOf(NewsRoute, ForumsRoute, SettingsRoute, AboutRoute, CheckHistoryRoute, ThreadRoute("3pxj858k")).forEach { assertFalse(isListPane(it), "$it") }
    }
}
