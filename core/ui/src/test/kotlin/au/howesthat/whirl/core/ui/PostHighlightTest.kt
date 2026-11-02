package au.howesthat.whirl.core.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.TextQuery
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostHighlightTest {

    @get:Rule val compose = createComposeRule()

    private val body = PostBody(
        listOf(
            Block.QuoteAttribution("Dave writes about NBN...", postId = 1),
            Block.Paragraph(listOf(Inline.Text("The nbn plan, and "), Inline.Spoiler(listOf(Inline.Text("a secret nbn"))), Inline.Text(" more NBN."))),
            Block.Code("ping nbn.com.au"),
        ),
    )

    private fun marked(text: String): List<String> {
        val node = compose.onNodeWithText(text, substring = true).fetchSemanticsNode()
        val shown: AnnotatedString = node.config.getOrNull(SemanticsProperties.Text)!!.first()
        return shown.spanStyles.filter { it.item.background == markBackground }.map { shown.text.substring(it.start, it.end) }
    }

    private var markBackground = androidx.compose.ui.graphics.Color.Unspecified

    @Test
    fun `find marks words in paragraphs, code and quote attributions, never in spoilers`() {
        compose.setContent {
            WhirlTheme(darkTheme = false) {
                val highlight = FindHighlight(TextQuery("nbn"))
                markBackground = highlight.style().background
                PostBodyView(body, onLink = {}, onQuote = {}, highlight = highlight)
            }
        }
        assertEquals(listOf("nbn", "NBN"), marked("The nbn plan"), "the spoiler's nbn is left alone")
        assertEquals(listOf("nbn"), marked("ping nbn"))
        assertEquals(listOf("NBN"), marked("Dave writes"))
    }
}
