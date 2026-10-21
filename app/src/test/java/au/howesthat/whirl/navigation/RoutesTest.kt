package au.howesthat.whirl.navigation

import au.howesthat.whirl.core.data.Homepage
import org.junit.Test
import au.howesthat.whirl.feature.thread.ThreadRoute
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
        assertTrue(isTopLevelRoute(WatchedRoute), "the notification's Watched link must not stack a second Watched")
        assertFalse(isTopLevelRoute(ThreadRoute("3pxj858k")), "a second thread link must push, not reuse the open thread")
        assertFalse(isTopLevelRoute(SettingsRoute))
    }
}
