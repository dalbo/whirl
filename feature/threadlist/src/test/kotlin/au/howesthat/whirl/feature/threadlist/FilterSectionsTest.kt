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
