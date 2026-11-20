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
}
