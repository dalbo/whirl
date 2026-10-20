package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.data.WhirlpoolLink.Archive
import au.howesthat.whirl.core.data.WhirlpoolLink.External
import au.howesthat.whirl.core.data.WhirlpoolLink.Forum
import au.howesthat.whirl.core.data.WhirlpoolLink.Reply
import au.howesthat.whirl.core.data.WhirlpoolLink.Short
import au.howesthat.whirl.core.data.WhirlpoolLink.Thread
import au.howesthat.whirl.core.data.WhirlpoolLink.User
import au.howesthat.whirl.core.model.ThreadId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhirlpoolLinkTest {
    private val f = "https://forums.whirlpool.net.au"

    @Test
    fun `thread links, with page and either anchor form`() {
        assertEquals(Thread(ThreadId("3pxj858k")), WhirlpoolLink.parse("$f/thread/3pxj858k"))
        val byNumber = WhirlpoolLink.parse("$f/thread/3pxj858k?p=2#r21") as Thread
        assertEquals(Thread(ThreadId("3pxj858k"), 2, 21), byNumber)
        assertFalse(byNumber.isReplyIdAnchor)
        val byReply = WhirlpoolLink.parse("$f/thread/3pxj858k?ux=915415#r75543951") as Thread
        assertTrue(byReply.isReplyIdAnchor)
        assertEquals(Thread(ThreadId("1000422")), WhirlpoolLink.parse("$f/thread/1000422"))
        assertEquals(Thread(ThreadId("1000422"), 3), WhirlpoolLink.parse("http://forums.whirlpool.net.au/forum-replies.cfm?t=1000422&p=3"))
    }

    @Test
    fun `other forum pages`() {
        assertEquals(Archive(ThreadId("1000422")), WhirlpoolLink.parse("$f/archive/1000422"))
        assertEquals(Forum(92), WhirlpoolLink.parse("$f/forum/92"))
        assertEquals(Forum(7, 179), WhirlpoolLink.parse("$f/forum/7?g=179"))
        assertEquals(User(30703), WhirlpoolLink.parse("$f/user/30703"))
        assertEquals(Reply(75543951), WhirlpoolLink.parse("$f/forum/?action=replies&r=75543951"))
        assertEquals(Short("https://whrl.pl/RgPkJ5"), WhirlpoolLink.parse("https://whrl.pl/RgPkJ5"))
    }

    @Test
    fun `everything else is external`() {
        listOf(
            "https://whirlpool.net.au/wiki/nbn",
            "$f/forum/?action=popular_views",
            "$f/forum/?action=reply&r=75543951",
            "https://example.com/thread/abc",
            "mailto:someone@example.com",
            "$f/",
        ).forEach { assertEquals(External(it), WhirlpoolLink.parse(it), it) }
    }
}
