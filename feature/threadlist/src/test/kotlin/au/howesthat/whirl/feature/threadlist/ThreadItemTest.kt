package au.howesthat.whirl.feature.threadlist

import au.howesthat.whirl.core.data.WatchedPosition
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.RowKind
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadSummary
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WatchedThread
import au.howesthat.whirl.core.scraper.ThreadListParser
import org.junit.Test
import java.io.File
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadItemTest {
    private fun html(name: String) = File(System.getProperty("whirl.fixtures"), "html/$name").readText()

    @Test
    fun `forum page rows keep kinds, open targets and the unread overlay`() {
        val page = ThreadListParser.parse(html("forum92_p1.html"))
        val firstThread = page.rows.first { it.kind == RowKind.THREAD }
        val items = page.rows.map { it.toItem(page.forum, unreadIds = setOf(firstThread.id)) }
        assertEquals(50, items.size)
        assertEquals(1, items.count { it.isUnread })
        assertTrue(items.all { it.forum?.id == 92 })

        val moved = items.single { it.kind == RowKind.MOVED }
        assertEquals(page.rows.single { it.kind == RowKind.MOVED }.linkId, moved.openId, "moved rows open their target")
        val deleted = items.single { it.kind == RowKind.DELETED }
        assertEquals(page.rows.single { it.kind == RowKind.DELETED }.redirectId, deleted.openId)

        val sections = sectionsOf(items, sectioned = false)
        assertEquals(1, sections.size)
        assertNull(sections.single().forum)
    }

    @Test
    fun `popular keeps its forum sections in page order`() {
        val page = ThreadListParser.parse(html("popular.html"))
        val sections = sectionsOf(page.rows.map { it.toItem(null) }, sectioned = true)
        assertEquals(page.sections.map { it.id }, sections.map { it.forum?.id })
        assertEquals(183, sections.sumOf { it.items.size })
        assertEquals(2, sections.flatMap { it.items }.count { it.isSticky })
    }

    private val date = OffsetDateTime.parse("2026-10-19T20:39:50+10:00")

    private fun summary(id: String, forumId: Int, forumName: String) = ThreadSummary(
        ThreadId(id), "T $id", 4, forumId, forumName, null, date, UserRef(1, "a"), date, UserRef(2, "b"),
        isSubscribed = false, isUnread = id == "b", hasParticipated = false,
    )

    @Test
    fun `API lists group by forum in order of first appearance`() {
        val items = listOf(summary("a", 92, "ISP"), summary("b", 7, "Broadband"), summary("c", 92, "ISP")).map { it.toItem() }
        val sections = groupByForum(items)
        assertEquals(listOf(92, 7), sections.map { it.forum?.id })
        assertEquals(listOf("a", "c"), sections[0].items.map { it.id.value })
        assertEquals(listOf(true), sections[1].items.map { it.isUnread })
    }

    @Test
    fun `watched threads carry their read position and unread count`() {
        val w = WatchedThread(ThreadId("3pxj858k"), "NBN", 250, 92, "ISP", date, UserRef(1, "a"), date, UserRef(2, "b"), 4, 247, 13).toItem()
        assertEquals(WatchedPosition(13, 247), w.watched)
        assertEquals(4, w.unreadCount)
        assertTrue(w.isUnread)
        assertEquals(ForumRef(92, "ISP"), w.forum)
    }
}
