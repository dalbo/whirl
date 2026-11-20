package au.howesthat.whirl.feature.writer

import androidx.compose.ui.text.TextRange
import org.junit.Test
import kotlin.test.assertEquals

class FormattingTest {
    private fun edit(text: String, start: Int, end: Int = start) = Edit(text, TextRange(start, end))

    @Test
    fun `wrap keeps the selection selected, or puts the cursor inside an empty pair`() {
        assertEquals(edit("say [*hello*] there", 6, 11), Formatting.wrap(edit("say hello there", 4, 9), "[*", "*]"))
        assertEquals(edit("a [**] b", 4), Formatting.wrap(edit("a  b", 2), "[*", "*]"))
    }

    @Test
    fun `a line prefix goes on every selected line, and comes off when all have it`() {
        val on = Formatting.toggleLinePrefix(edit("one\ntwo\nthree", 1, 5), '>')
        assertEquals("> one\n> two\nthree", on.text)
        assertEquals(TextRange(0, 11), on.selection)
        assertEquals("one\ntwo\nthree", Formatting.toggleLinePrefix(on, '>').text)
    }

    @Test
    fun `a code goes in front of codes already there, and the cursor moves with its line`() {
        val nested = Formatting.toggleLinePrefix(edit("* item", 3), '>')
        assertEquals("&gt;* item".replace("&gt;", ">"), nested.text)
        assertEquals(TextRange(4), nested.selection)
        assertEquals(edit("> x", 3), Formatting.toggleLinePrefix(edit("x", 1), '>'))
    }

    @Test
    fun `a link with text is an anchor, without text the bare address`() {
        assertEquals(edit("see <a href=\"https://e.com\">this</a>", 36), Formatting.link(edit("see ", 4), "https://e.com", "this"))
        assertEquals(edit("see https://e.com", 17), Formatting.link(edit("see ", 4), " https://e.com ", ""))
    }

    @Test
    fun `a block sits on lines of its own`() {
        assertEquals(edit("text\n\n-----", 11), Formatting.block(edit("text", 4), "-----"))
        assertEquals(edit("a\n\n$$\n\n$$\n\nb", 6), Formatting.block(edit("ab", 1), "$$\n\n$$", cursorAt = 3))
    }

    @Test
    fun `the list buttons add a list, switch its kind keeping its depth, and take it off`() {
        val bullets = Formatting.toggleList(edit("one\ntwo", 0, 7), '*')
        assertEquals("* one\n* two", bullets.text)
        // Dallin, 2026-11-20: switching kind replaces the markers rather than adding a list inside the list.
        assertEquals("# one\n# two", Formatting.toggleList(bullets, '#').text)
        assertEquals("## nested", Formatting.toggleList(edit("** nested", 4), '#').text)
        assertEquals("one\ntwo", Formatting.toggleList(bullets, '*').text)
        // Inside a quote, the quote stays.
        assertEquals("># point", Formatting.toggleList(edit(">* point", 3), '#').text)
    }

    @Test
    fun `list items nest three deep and come back out, other lines unchanged`() {
        val deeper = Formatting.nestList(edit("* a\nplain\n# b", 0, 13), deeper = true)
        assertEquals("** a\nplain\n## b", deeper.text)
        assertEquals("*** a", Formatting.nestList(edit("** a", 3), deeper = true).text)
        assertEquals("*** a", Formatting.nestList(edit("*** a", 3), deeper = true).text, "three is WhirlCode's limit")
        assertEquals("* a", Formatting.nestList(edit("** a", 3), deeper = false).text)
        assertEquals("* a", Formatting.nestList(edit("* a", 2), deeper = false).text, "the top level stays a list")
        assertEquals("**** x", Formatting.nestList(edit("**** x", 2), deeper = true).text, "four codes is the limit")
    }
}
