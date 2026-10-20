package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadPart
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadPageParserTest {

    @Test
    fun `page 2 of 13`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_3pxj858k_p2.html"))
        assertEquals(ThreadId("3pxj858k"), page.threadId)
        assertEquals("Simple, ad-free NBN plan comparison tool I built", page.title)
        assertEquals(ForumRef(92, "Choosing an ISP"), page.forum)
        assertEquals(2, page.page)
        assertEquals(13, page.pageCount)
        assertEquals(20, page.posts.size)
        assertEquals((21..40).toList(), page.posts.map { it.number })
        // HANDOFF's "8 edited" counts .edited markers; each edited post has two or three of them.
        assertEquals(3, page.posts.count { it.isEdited })
        assertEquals(5, page.posts.count { it.isOp })
        assertTrue(page.posts.none { it.isDeleted || it.isHidden })
        assertNull(page.moderatorNoteHtml)
        assertEquals(emptyList(), page.parts)

        val attributions = page.posts.flatMap { PostBodyParser.parse(it.bodyHtml).blocks }.filterIsInstance<Block.QuoteAttribution>()
        assertEquals(17, attributions.size)
        assertTrue(attributions.all { it.postId != null })
    }

    @Test
    fun `post metadata`() {
        val posts = ThreadPageParser.parse(Fixtures.html("thread_3pxj858k_p2.html")).posts
        val first = posts.first()
        assertEquals(75543951L, first.replyId)
        assertEquals(UserRef(915415, "Eragus"), first.author)
        assertEquals("Participant", first.userGroup)
        assertEquals(188, first.postCount)
        assertEquals("2025-Jun-23, 11:11 am", first.postedText)
        assertEquals(LocalDateTime.of(2025, 6, 23, 11, 11), first.postedAt)
        assertEquals("https://whrl.pl/RgPkJ5", first.shortUrl)
        assertNull(first.quip)
        assertFalse(first.isEdited)

        val second = posts[1]
        assertEquals("2025-Jun-23, 11:55 am", second.editedText)
        assertEquals("2025-Jun-23, 11:30 am", second.postedText)

        assertEquals("moments later", posts.single { it.replyId == 75544778L }.editedText)
        assertEquals("CarbonComms 1000/400", posts.first { it.author.name == "JebusJebus" }.quip)
        assertTrue(posts.none { "O.P." in it.bodyHtml || "this post was" in it.bodyHtml })
    }

    @Test
    fun `last page of a huge thread with a moderator note and parts`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_big_9nznpky9_last.html"))
        assertEquals(2663, page.page)
        assertEquals(2663, page.pageCount)
        assertEquals(9, page.posts.size)
        val note = assertNotNull(page.moderatorNoteHtml)
        assertTrue("/thread/9kv68jl3" in note)
        assertTrue(page.parts.size > 3)
        assertEquals(ThreadPart(1, ThreadId("9xqyn453"), isCurrent = false), page.parts.first())
    }

    @Test
    fun `sticky with three parts`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_sticky_3mnkq4v9.html"))
        assertEquals(1, page.page)
        assertEquals(1211, page.pageCount)
        assertNull(page.moderatorNoteHtml)
        assertEquals(
            listOf(
                ThreadPart(1, ThreadId("91qrjn49"), false),
                ThreadPart(2, ThreadId("3vx00883"), false),
                ThreadPart(3, ThreadId("3mnkq4v9"), true),
            ),
            page.parts,
        )
    }

    @Test
    fun `moderator hidden post`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_sticky_3mnkq4v9.html"))
        val hidden = page.posts.single { it.isHidden }
        assertEquals(55298350L, hidden.replyId)
        assertEquals(2, hidden.number)
        assertEquals(UserRef(695922, "My Possum"), hidden.author)
        assertEquals("pointless", hidden.hiddenReason)
        assertEquals(LocalDateTime.of(2017, 12, 17, 10, 55), hidden.postedAt)
        assertFalse(hidden.isDeleted)
        assertEquals("", hidden.bodyHtml)
        assertEquals("personal attack", ThreadPageParser.parse(Fixtures.html("thread_numeric_1000422.html")).posts.single { it.isHidden }.hiddenReason)
    }

    @Test
    fun `public moderator note on another thread`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_big_9pl2kzy9_last.html"))
        assertEquals(3167, page.page)
        assertNotNull(page.moderatorNoteHtml)
        assertEquals(18, page.posts.size)
    }

    @Test
    fun `legacy numeric url resolves to the slug`() {
        for (name in listOf("thread_numeric_1000422.html", "thread_old_url_1000422.html")) {
            val page = ThreadPageParser.parse(Fixtures.html(name))
            assertEquals(ThreadId("9nwqj8q3"), page.threadId, name)
            assertEquals(ForumRef(71, "Lifestyle", groupId = 189), page.forum)
            assertEquals(1, page.page)
            assertEquals(3, page.pageCount)
            assertEquals(20, page.posts.size)
        }
    }

    @Test
    fun `archive is the whole thread on one page`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_archive_numeric.html"))
        assertEquals(ThreadId("9nwqj8q3"), page.threadId)
        assertEquals("Lite n' Easy tryout, I'm always hungry!!", page.title)
        assertEquals(1, page.page)
        assertEquals(1, page.pageCount)
        // 49 posts, but the archive leaves out #15, which a moderator hid.
        assertEquals(48, page.posts.size)
        assertEquals((1..49).toList() - 15, page.posts.map { it.number })
        val first = page.posts.first()
        assertEquals(UserRef(30703, "Tiedan"), first.author)
        assertEquals(903, first.postCount)
        assertEquals("2008-Jun-24, 1:15 pm AEST", first.postedText)
        assertEquals(LocalDateTime.of(2008, 6, 24, 13, 15), first.postedAt)
        assertEquals("https://whrl.pl/RbzjOW", first.shortUrl)
        assertTrue(page.posts.none { it.isDeleted })
    }

    @Test
    fun `first page of a long thread`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_97r5wzn3_p1.html"))
        assertEquals(1, page.page)
        assertEquals(2290, page.pageCount)
        assertEquals(20, page.posts.size)
        assertEquals(8, page.posts.count { it.isEdited })
        assertEquals(8, page.posts.count { it.isOp })
        assertNull(page.moderatorNoteHtml)
    }

    @Test
    fun `last page of a one-page thread`() {
        val page = ThreadPageParser.parse(Fixtures.html("thread_big_9w88m1qr_last.html"))
        assertEquals(1, page.page)
        assertEquals(1, page.pageCount)
        assertEquals(4, page.posts.size)
    }

    @Test
    fun `private thread needs a web login`() {
        assertFailsWith<WhirlpoolError.NotSignedIn> {
            ThreadPageParser.parse(Fixtures.html("thread_private_feedback_3nzn4p83.html"))
        }
    }

    @Test
    fun `a forum page is not a thread`() {
        assertFailsWith<WhirlpoolError.Drift> { ThreadPageParser.parse(Fixtures.html("forum92_p1.html")) }
    }
}
