package au.howesthat.whirl.feature.forums

import au.howesthat.whirl.core.data.ForumListItem
import au.howesthat.whirl.core.model.Forum
import au.howesthat.whirl.core.model.TextQuery
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ForumSectionsTest {
    private fun f(id: Int, section: String, fav: Boolean = false) = ForumListItem(Forum(id, "F$id", section), fav, isPrivate = false)

    @Test
    fun `favourites lead, then API sections in order`() {
        val sections = forumSections(listOf(f(1, "Whirlpool"), f(92, "ISPs", fav = true), f(7, "ISPs"), f(71, "Lifestyle", fav = true)), "Favourites")
        assertEquals(listOf("Favourites", "Whirlpool", "ISPs", "Lifestyle"), sections.map { it.title })
        assertTrue(sections.first().isFavourites)
        assertEquals(listOf(92, 71), sections.first().forums.map { it.forum.id })
        assertEquals(listOf(92, 7), sections[2].forums.map { it.forum.id }, "favourites stay in their own section too")
    }

    @Test
    fun `no favourites, no favourites section`() {
        assertEquals(listOf("ISPs"), forumSections(listOf(f(92, "ISPs")), "Favourites").map { it.title })
    }

    @Test
    fun `find keeps matching forums, or a whole section by its title`() {
        val sections = forumSections(listOf(f(92, "ISPs", fav = true), f(7, "Hardware"), f(8, "Hardware")), "Favourites")
        assertEquals(listOf("Favourites" to listOf(92), "ISPs" to listOf(92)), filterForums(sections, TextQuery("f92")).map { it.title to it.forums.map { f -> f.forum.id } })
        assertEquals(listOf("Hardware" to listOf(7, 8)), filterForums(sections, TextQuery("hardware")).map { it.title to it.forums.map { f -> f.forum.id } })
        assertEquals(listOf("Hardware" to listOf(7)), filterForums(sections, TextQuery("hardware f7")).map { it.title to it.forums.map { f -> f.forum.id } })
        assertEquals(emptyList(), filterForums(sections, TextQuery("nothing")))
        assertSame(sections, filterForums(sections, TextQuery("")))
    }
}
