package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.ArchivePageRef
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.PostRole
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WhirlpoolError
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchivePageParserTest {

    @Test
    fun `last of two pages`() {
        val page = ArchivePageParser.parse(Fixtures.html("archive_97q1vrm9_p2.html"))
        assertEquals(ThreadId("97q1vrm9"), page.threadId)
        assertEquals("Whirldroid - yet another Whirlpool app", page.title)
        assertEquals(140, page.forum?.id)
        assertEquals(
            listOf(
                ArchivePageRef("/archive/1906307", "April 2012—May 2019"),
                ArchivePageRef("/archive/97q1vrm9-2", "May 2019—November 2026"),
            ),
            page.pages,
        )
        assertEquals(1, page.current)
        assertEquals(2, page.pageInPart)
        assertEquals(402, page.posts.size)
        assertEquals(801, page.posts.first().number)
        assertEquals(1213, page.posts.last().number)
        // Numbers only ever rise; where they jump, the archive left a hidden post out.
        assertTrue(page.posts.zipWithNext().all { (a, b) -> b.number > a.number })
        assertTrue(page.posts.none { it.isDeleted || it.isHidden || it.postedAt == null })
    }

    @Test
    fun `last of 58 pages, first page by numeric id`() {
        val page = ArchivePageParser.parse(Fixtures.html("archive_97r5wzn3_last.html"))
        assertEquals(ThreadId("97r5wzn3"), page.threadId)
        assertEquals(ForumRef(7, "Desktops", 179), page.forum)
        assertEquals(58, page.pages.size)
        assertEquals(57, page.current)
        assertEquals(ArchivePageRef("/archive/2747207", "August 2018—September 2018"), page.pages.first())
        assertEquals((2..58).map { "/archive/97r5wzn3-$it" }, page.pages.drop(1).map { it.path })
        assertEquals("September 2026—November 2026", page.pages.last().label)
        assertEquals(524, page.posts.size)
        assertEquals(45601..46125, page.posts.first().number..page.posts.last().number)
    }

    @Test
    fun `times are Sydney time, daylight saving included`() {
        // The page text says "posted 2026-Nov-6, 7:11 am AEST" all year; datePublished is 2026-11-06T07:11:30+10:00,
        // which is 8:11 am in Sydney in November (AEDT), as the thread page shows it ("Friday at 8:11 am").
        val post = ArchivePageParser.parse(Fixtures.html("archive_97r5wzn3_last.html")).posts.first { it.number == 46123 }
        assertEquals(LocalDateTime.of(2026, 11, 6, 8, 11), post.postedAt)
        assertEquals("2026-Nov-6, 8:11 am", post.postedText)
    }

    @Test
    fun `one page with no list, reps and moderators`() {
        val page = ArchivePageParser.parse(Fixtures.html("archive_3l10nlj5_reps.html"))
        assertEquals(ThreadId("3l10nlj5"), page.threadId)
        assertEquals(listOf(ArchivePageRef("/archive/3l10nlj5", null)), page.pages)
        assertEquals(0, page.current)
        assertEquals(616, page.posts.size)

        val reps = page.posts.filter { it.role == PostRole.REP }
        assertEquals(61, reps.size)
        assertTrue(reps.all { it.userGroup == "ISP Representative" })
        assertEquals(setOf("Leaptel"), reps.mapNotNull { it.repOf }.toSet())
        val matt = page.posts.first { it.number == 13 }
        assertEquals("Matt E.", matt.author.name)
        assertEquals("Leaptel", matt.repOf)
        assertEquals(3, page.posts.count { it.role == PostRole.MODERATOR })
        assertTrue(page.posts.filter { it.role == null }.all { it.repOf == null })
    }

    @Test
    fun `multi-part thread lists pages across thread ids`() {
        val page = ArchivePageParser.parse(Fixtures.html("archive_3mnkq4v9_chunks.html"))
        assertEquals(ThreadId("3mnkq4v9"), page.threadId)
        assertEquals(37, page.pages.size)
        assertEquals(ArchivePageRef("/archive/2629951", "May 2017—July 2017"), page.pages[0])
        assertEquals("/archive/91qrjn49-2", page.pages[1].path)
        assertEquals("/archive/3mnkq4v9-31", page.pages.last().path)
        val ids = page.pages.map { it.path.removePrefix("/archive/").substringBefore('-') }.toSet()
        assertTrue(ids.containsAll(setOf("91qrjn49", "3vx00883", "3mnkq4v9")), "pages of every part: $ids")
        // This is page 7, the first of the third part, where post numbers start again.
        assertEquals(6, page.current)
        assertEquals("/archive/2690338", page.pages[page.current].path)
        assertEquals(listOf(true, false, false, true, false, false, true), page.pages.take(7).map { it.startsPart })
        assertEquals(31, page.partPages.size)
        assertEquals(1, page.pageInPart)
        assertEquals("/archive/3mnkq4v9-31", page.partPages.last().path)
        assertEquals(796, page.posts.size)
        assertEquals(1, page.posts.first().number)
        assertEquals("/archive/2690338", page.pageFor(800)?.path)
        assertEquals("/archive/3mnkq4v9-2", page.pageFor(801)?.path)
    }

    @Test
    fun `post numbers map exactly to pages despite gaps`() {
        val two = ArchivePageParser.parse(Fixtures.html("archive_97q1vrm9_p2.html"))
        assertEquals(2, two.partPages.size)
        assertEquals(2, two.pageInPart)
        assertEquals("/archive/1906307", two.pageFor(1)?.path)
        assertEquals("/archive/1906307", two.pageFor(800)?.path)
        assertEquals("/archive/97q1vrm9-2", two.pageFor(801)?.path)
        assertEquals("/archive/97q1vrm9-2", two.pageFor(1213)?.path)
        assertNull(two.pageFor(1601))
        assertNull(two.pageFor(0))

        val rtx = ArchivePageParser.parse(Fixtures.html("archive_97r5wzn3_last.html"))
        assertEquals(58, rtx.partPages.size)
        assertEquals("/archive/97r5wzn3-58", rtx.pageFor(rtx.posts.first().number)?.path)
        assertEquals("/archive/97r5wzn3-58", rtx.pageFor(rtx.posts.last().number)?.path)
        // Page 58's first post is 57 * 800 + 1, whatever was hidden on earlier pages.
        assertEquals(57 * 800 + 1, rtx.posts.first().number)
    }

    @Test
    fun `old whole-thread archive`() {
        val page = ArchivePageParser.parse(Fixtures.html("thread_archive_numeric.html"))
        assertEquals(ThreadId("9nwqj8q3"), page.threadId)
        assertEquals(1, page.pages.size)
        assertEquals("/archive/1000422", page.pages.single().path)
        assertEquals(48, page.posts.size)
        assertNull(page.posts.first().repOf)
    }

    @Test
    fun `no posts is drift`() {
        val html = Fixtures.html("archive_3l10nlj5_reps.html").replace("replyblock", "replyblockgone")
        assertFailsWith<WhirlpoolError.Drift> { ArchivePageParser.parse(html) }
    }

    @Test
    fun `roles from user groups`() {
        assertEquals(PostRole.MODERATOR, PostRole.of("Section Moderator", null))
        assertEquals(PostRole.MODERATOR, PostRole.of("Benevolent Dictator", null))
        assertEquals(PostRole.REP, PostRole.of("ISP Representative", null))
        assertEquals(PostRole.REP, PostRole.of(null, "Leaptel"))
        assertNull(PostRole.of("Whirlpool Forums Addict", null))
        assertNull(PostRole.of(null, null))
    }
}
