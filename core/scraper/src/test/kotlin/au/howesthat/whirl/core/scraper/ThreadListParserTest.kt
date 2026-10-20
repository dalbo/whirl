package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.ForumGroup
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.RowKind
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WhirlpoolError
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadListParserTest {

    @Test
    fun `forum 92 page 1`() {
        val page = ThreadListParser.parse(Fixtures.html("forum92_p1.html"))
        assertEquals(ForumRef(92, "Choosing an ISP"), page.forum)
        assertEquals(1, page.page)
        assertTrue(page.pageCount > 11, "page count from select[name=p], got ${page.pageCount}")
        assertEquals(emptyList(), page.groups)
        assertEquals(50, page.rows.size)
        assertEquals(48, page.rows.count { it.kind == RowKind.THREAD })
        assertEquals(1, page.rows.count { it.kind == RowKind.MOVED })
        assertEquals(1, page.rows.count { it.kind == RowKind.DELETED })
        assertEquals(17, page.rows.count { it.pageCount > 1 })
        assertTrue(page.rows.none { it.isSticky || it.isClosed })

        val first = page.rows.first()
        assertEquals(ThreadId("3rvvvlwj"), first.id)
        assertEquals(first.id, first.linkId)
        assertEquals("nbntracker.au – NBN price comparison & matchmaker", first.title)
        assertEquals(7, first.pageCount)
        assertEquals(135, first.replies)
        assertEquals(8883, first.views)
        assertEquals(UserRef(535231, "kzrl"), first.firstPoster)
        assertEquals("2026-Mar-14, 4:46 pm", first.firstPostedText)
        assertEquals(UserRef(535231, "kzrl"), first.lastPoster)
        assertEquals("Yesterday at 9:39 pm", first.lastPostedText)
        assertNull(first.note)
    }

    @Test
    fun `moved and deleted rows keep their own id and point elsewhere`() {
        val rows = ThreadListParser.parse(Fixtures.html("forum92_p1.html")).rows
        val moved = rows.single { it.kind == RowKind.MOVED }
        assertEquals(ThreadId("9yqqj0m1"), moved.id)
        assertEquals(ThreadId("9kppq2y7"), moved.linkId)
        assertEquals("Router compatibility?", moved.title)
        assertEquals("moved to Modems/Routers", moved.note)
        assertNull(moved.replies)

        val deleted = rows.single { it.kind == RowKind.DELETED }
        assertEquals(ThreadId("3m00lzkp"), deleted.id)
        assertEquals(ThreadId("31mm8w44"), deleted.linkId)
        assertEquals(ThreadId("988852x5"), deleted.redirectId)
        assertEquals("use existing thread", deleted.note)
    }

    @Test
    fun `replies with a padded title attribute`() {
        val row = ThreadListParser.parse(Fixtures.html("forum92_p1.html")).rows.single { it.id == ThreadId("9pxxr668") }
        assertEquals(8, row.replies)
        assertEquals(455, row.views)
        assertEquals("Experience on Arctel ? Need to change the ISP", row.title)
    }

    @Test
    fun `forum 92 page 2 has two closed threads`() {
        val page = ThreadListParser.parse(Fixtures.html("forum92_p2.html"))
        assertEquals(2, page.page)
        assertEquals(50, page.rows.size)
        assertEquals(2, page.rows.count { it.isClosed })
    }

    @Test
    fun `forum group page`() {
        val page = ThreadListParser.parse(Fixtures.html("forum7_g179.html"))
        assertEquals(ForumRef(7, "Desktops", groupId = 179), page.forum)
        assertEquals(50, page.rows.size)
        assertEquals(9, page.groups.size)
        assertTrue(ForumGroup(179, "GPUs") in page.groups)
        assertEquals(ForumGroup(75, "Audio"), page.groups.first())
    }

    @Test
    fun `popular has sections, groups and stickies`() {
        val page = ThreadListParser.parse(Fixtures.html("popular.html"))
        assertNull(page.forum)
        assertEquals(36, page.sections.size)
        assertEquals(183, page.rows.size)
        assertEquals(2, page.rows.count { it.isSticky })
        assertTrue(page.rows.all { it.section != null })

        val rtx = page.rows.first()
        assertEquals(ThreadId("97r5wzn3"), rtx.id)
        assertEquals(ForumRef(7, "Desktops"), rtx.section)
        assertEquals(ForumRef(7, "GPUs", groupId = 179), rtx.group)
        assertEquals(2290, rtx.pageCount)
        assertEquals(45795, rtx.replies)

        val sticky = page.rows.first { it.isSticky }
        assertEquals(ThreadId("3mnkq4v9"), sticky.id)
        assertEquals(1211, sticky.pageCount)
    }

    @Test
    fun `search list view uses the same table`() {
        val page = ThreadListParser.parse(Fixtures.html("search_nbn_listview.html"))
        assertEquals(100, page.rows.size)
        assertEquals(17, page.sections.size)
        assertEquals(3, page.rows.count { it.isClosed })
    }

    @ParameterizedTest
    @ValueSource(strings = ["forum1_pm_html.html", "forum35_feedback_html.html", "thread_private_probe_35.html"])
    fun `private forums need a web login`(name: String) {
        assertFailsWith<WhirlpoolError.NotSignedIn> { ThreadListParser.parse(Fixtures.html(name)) }
    }

    @Test
    fun `a page without a thread table is drift`() {
        assertFailsWith<WhirlpoolError.Drift> { ThreadListParser.parse(Fixtures.html("forum_index.html")) }
    }
}
