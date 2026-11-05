package au.howesthat.whirl.feature.threadlist

import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.ThreadId
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class FilterSectionsTest {
    private val isp = ForumRef(92, "Choosing an ISP")
    private val desktops = ForumRef(7, "Desktops")

    private fun item(id: String, title: String, forum: ForumRef?, group: String? = null) =
        ThreadItem(ThreadId(id), ThreadId(id), title, forum = forum, group = group)

    private val sections = listOf(
        Section(isp, listOf(item("a", "NBN plan comparison", isp), item("b", "Aussie Broadband support", isp))),
        Section(desktops, listOf(item("c", "Best GPU for 1440p", desktops), item("d", "NBN gaming latency", desktops, group = "Gaming"))),
    )

    private fun titles(result: List<Section>) = result.map { s -> s.forum?.title to s.items.map { it.title } }

    @Test
    fun `rows are kept by title and headers stay while a row is left`() {
        assertEquals(
            listOf("Choosing an ISP" to listOf("NBN plan comparison"), "Desktops" to listOf("NBN gaming latency")),
            titles(filterSections(sections, TextQuery("nbn"))),
        )
        assertEquals(listOf("Desktops" to listOf("Best GPU for 1440p")), titles(filterSections(sections, TextQuery("gpu"))), "empty sections drop")
    }

    @Test
    fun `a forum name keeps its rows and words may mix fields`() {
        assertEquals(
            listOf("Choosing an ISP" to listOf("NBN plan comparison", "Aussie Broadband support")),
            titles(filterSections(sections, TextQuery("isp"))),
        )
        assertEquals(listOf("Desktops" to listOf("NBN gaming latency")), titles(filterSections(sections, TextQuery("nbn desktops"))))
        assertEquals(listOf("Desktops" to listOf("NBN gaming latency")), titles(filterSections(sections, TextQuery("gaming"))), "group names match")
    }

    @Test
    fun `a forum page leaves its own forum out`() {
        assertEquals(emptyList(), filterSections(sections, TextQuery("isp"), matchForum = false))
    }

    @Test
    fun `a blank query returns the list unchanged`() {
        assertSame(sections, filterSections(sections, TextQuery("  ")))
    }
}

class KeepRowTest {
    private val isp = ForumRef(92, "Choosing an ISP")
    private val desktops = ForumRef(7, "Desktops")
    private fun item(id: String, forum: ForumRef) = ThreadItem(ThreadId(id), ThreadId(id), id, forum = forum)

    private val before = listOf(Section(isp, listOf(item("a", isp), item("b", isp))), Section(desktops, listOf(item("c", desktops))))

    private fun ids(sections: List<Section>) = sections.map { s -> s.forum?.id to s.items.map { it.id.value } }

    @Test
    fun `the open thread stays where it was after it leaves the list`() {
        val after = listOf(Section(isp, listOf(item("b", isp))), Section(desktops, listOf(item("c", desktops))))
        assertEquals(listOf(92 to listOf("a", "b"), 7 to listOf("c")), ids(keepRow(after, before, ThreadId("a"))))
    }

    @Test
    fun `its section comes back if it was the only row`() {
        val after = listOf(Section(isp, listOf(item("a", isp), item("b", isp))))
        assertEquals(listOf(92 to listOf("a", "b"), 7 to listOf("c")), ids(keepRow(after, before, ThreadId("c"))))
    }

    @Test
    fun `nothing changes without a thread open or while it is still listed`() {
        val after = listOf(Section(isp, listOf(item("b", isp))))
        assertSame(after, keepRow(after, before, null))
        assertSame(after, keepRow(after, null, ThreadId("a")))
        assertSame(before, keepRow(before, before, ThreadId("a")))
    }
}

class ListKeysTest {
    private val isp = ForumRef(92, "Choosing an ISP")
    private val desktops = ForumRef(7, "Desktops")
    private fun item(id: String, title: String, forum: ForumRef) = ThreadItem(ThreadId(id), ThreadId(id), title, forum = forum)

    @Test
    fun `list keys stay put when find hides a section, and never repeat`() {
        // Popular-style runs: the same forum twice, and a thread listed twice in one run.
        val runs = sectionsOf(
            listOf(item("a", "NBN plans", isp), item("c", "GPU prices", desktops), item("b", "NBN outage", isp), item("b", "NBN outage", isp)),
            sectioned = true,
        )
        assertEquals(listOf("forum-92", "forum-7", "forum-92-2"), runs.map { it.key })
        assertEquals(listOf("forum-92-2/b", "forum-92-2/b#2"), runs[2].rowKeys)

        val found = filterSections(runs, TextQuery("outage"))
        assertEquals(listOf("forum-92-2"), found.map { it.key }, "the earlier sections are hidden, the key is not shifted")
        assertEquals(runs[2].rowKeys, found.single().rowKeys)
    }
}
