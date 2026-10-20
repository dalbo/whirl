package au.howesthat.whirl.navigation

import au.howesthat.whirl.feature.thread.ThreadRoute
import au.howesthat.whirl.feature.threadlist.ForumRoute
import au.howesthat.whirl.feature.user.UserRoute
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeepLinksTest {
    private val f = "https://forums.whirlpool.net.au"

    @Test
    fun `whirlpool pages map to routes`() {
        assertEquals(ThreadRoute("3pxj858k"), routeForLink("$f/thread/3pxj858k"))
        assertEquals(ThreadRoute("3pxj858k", 2, scrollToPost = 21), routeForLink("$f/thread/3pxj858k?p=2#r21"))
        assertEquals(ThreadRoute("3pxj858k", 1, scrollToReply = 75543951), routeForLink("$f/thread/3pxj858k?ux=915415#r75543951"))
        assertEquals(ThreadRoute("1000422", 3), routeForLink("http://forums.whirlpool.net.au/forum-replies.cfm?t=1000422&p=3"))
        assertEquals(ThreadRoute("1000422"), routeForLink("$f/archive/1000422"))
        assertEquals(ForumRoute(7, "", 179), routeForLink("$f/forum/7?g=179"))
        assertEquals(UserRoute(30703), routeForLink("$f/user/30703"))
    }

    @Test
    fun `links that must stay in the browser`() {
        assertNull(routeForLink("$f/forum/?action=reply&r=75543951"))
        assertNull(routeForLink("$f/forum/?action=replies&r=75543951"))
        assertNull(routeForLink("https://whirlpool.net.au/wiki/nbn"))
    }
}
