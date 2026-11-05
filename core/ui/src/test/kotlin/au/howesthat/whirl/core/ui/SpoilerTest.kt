package au.howesthat.whirl.core.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.ListItem
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.scraper.PostBodyParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SpoilerTest {

    @get:Rule val compose = createComposeRule()

    /** Spoilers in two paragraphs and a list, numbered as the parser numbers them. */
    private val body = PostBodyParser.parse(
        """<p>First <span class="wcspoil">one</span> here.</p><p>Second <span class="wcspoil">two</span> here.</p>""" +
            """<ul><li>Third <span class="wcspoil">three</span> here.</li></ul>""",
    )

    /** The spoilers still hidden in the text that contains [visible]. */
    private fun hiddenIn(visible: String): Int {
        val text = compose.onNodeWithText(visible, substring = true).fetchSemanticsNode().config[SemanticsProperties.Text].first()
        return text.getLinkAnnotations(0, text.length).count { (it.item as? LinkAnnotation.Clickable)?.tag?.startsWith("spoiler") == true }
    }

    /** Taps the hidden spoiler in the text that contains [visible], where it is drawn. */
    private fun tapSpoiler(visible: String) {
        val node = compose.onNodeWithText(visible, substring = true)
        val semantics = node.fetchSemanticsNode()
        val text = semantics.config[SemanticsProperties.Text].first()
        val spoiler = text.getLinkAnnotations(0, text.length).first { (it.item as? LinkAnnotation.Clickable)?.tag?.startsWith("spoiler") == true }
        val layouts = mutableListOf<TextLayoutResult>()
        semantics.config[SemanticsActions.GetTextLayoutResult].action!!(layouts)
        val box = layouts.first().getBoundingBox(spoiler.start)
        node.performTouchInput { click(box.center) }
        compose.waitForIdle()
    }

    @Test
    fun `the parser numbers spoilers in body order`() {
        fun spoilers(inlines: List<Inline>) = inlines.filterIsInstance<Inline.Spoiler>().map { it.index }
        val (first, second, list) = body.blocks
        assertEquals(listOf(0), spoilers((first as Block.Paragraph).inlines))
        assertEquals(listOf(1), spoilers((second as Block.Paragraph).inlines))
        assertEquals(listOf(2), spoilers((list as Block.ListBlock).items.single().let(ListItem::inlines)))
    }

    @Test
    fun `tapping the second spoiler reveals that one, then the others still reveal`() {
        compose.setContent { WhirlTheme(darkTheme = false) { PostBodyView(body, onLink = {}, onQuote = {}) } }
        tapSpoiler("Second")
        assertEquals(0, hiddenIn("Second"), "the second spoiler shows")
        assertEquals(1, hiddenIn("First"), "the first is still hidden")
        assertEquals(1, hiddenIn("Third"))

        tapSpoiler("Third")
        assertEquals(0, hiddenIn("Third"))
        tapSpoiler("First")
        assertEquals(0, hiddenIn("First"))
    }

    /**
     * A table's cells recompose on their own, without the rest of the body: numbering spoilers as they were composed
     * gave the cells new numbers then, so the tapped spoiler never showed.
     */
    @Test
    fun `a spoiler in a table cell reveals`() {
        val table = PostBodyParser.parse(
            """<table><tr><td>Cell <span class="wcspoil">a</span></td><td>Other <span class="wcspoil">b</span></td></tr></table>""",
        )
        compose.setContent { WhirlTheme(darkTheme = false) { PostBodyView(table, onLink = {}, onQuote = {}) } }
        tapSpoiler("Other")
        assertEquals(0, hiddenIn("Other"))
        assertEquals(1, hiddenIn("Cell"))
        tapSpoiler("Cell")
        assertEquals(0, hiddenIn("Cell"))
    }

    @Test
    fun `hand-built bodies number their own spoilers`() {
        val built = PostBody(listOf(Block.Paragraph(listOf(Inline.Text("A "), Inline.Spoiler(listOf(Inline.Text("x")), index = 4)))))
        compose.setContent { WhirlTheme(darkTheme = false) { PostBodyView(built, onLink = {}, onQuote = {}) } }
        tapSpoiler("A ")
        assertEquals(0, hiddenIn("A "))
    }
}
