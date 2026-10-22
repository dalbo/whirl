package au.howesthat.whirl.core.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.QuoteKind
import au.howesthat.whirl.core.model.Style
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppStyleTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val files = context.assets.list("styles").orEmpty().filter { it.endsWith(".json") }
    private val styles = AppStyles.all(context)

    @Test
    fun `every bundled style loads, named after its file, Classic first`() {
        assertEquals(files.size, styles.size, "a style file failed to parse")
        assertEquals(files.map { it.removeSuffix(".json") }.toSet(), styles.map { it.id }.toSet())
        assertEquals(AppStyle.CLASSIC, styles.first().id)
        assertEquals(AppStyle.Fallback, styles.first(), "classic.json and AppStyle.Fallback must agree")
        assertEquals("silver-ember", AppStyles.find(context, "silver-ember").id)
        assertEquals(AppStyle.CLASSIC, AppStyles.find(context, "no-such-style").id)
    }

    @Test
    fun `every colour role is a known one`() {
        styles.forEach { style ->
            listOf(false, true).forEach { dark ->
                style.colorScheme(dark)
                AppStyle.EXTRA_ROLES.forEach { style.extra(it, dark) }
            }
        }
        assertFailsWith<IllegalArgumentException> { AppStyle("x", "X", light = mapOf("primry" to "#000000")).colorScheme(dark = false) }
        assertFailsWith<IllegalArgumentException> { AppStyle("x", "X", light = mapOf("primary" to "blue")).colorScheme(dark = false) }
    }

    @Test
    fun `text and accents reach 4,5 to 1 in every style, light and dark`() {
        styles.forEach { style ->
            listOf(false, true).forEach { dark ->
                val s = style.colorScheme(dark)
                val where = "${style.id} ${if (dark) "dark" else "light"}"
                val unread = style.extra("unread", dark)
                val quotes = listOf(
                    style.extra("quoteRepliedUser", dark) ?: if (dark) QuoteColors.repliedUserDark else QuoteColors.repliedUserLight,
                    style.extra("quoteOther", dark) ?: if (dark) QuoteColors.otherDark else QuoteColors.otherLight,
                )
                val surfaces = listOfNotNull(s.background, s.surface, s.surfaceContainerHigh, unread)
                surfaces.forEach { bg ->
                    listOf(s.onSurface, s.onSurfaceVariant, s.primary).forEach { fg -> readable(fg, bg, where) }
                }
                quotes.forEach { q ->
                    readable(q, s.surface, where)
                    if (style.layout.quotes == Quotes.BOXED) readable(q, s.surfaceContainerLow, where)
                }
                pairs(s).forEach { (fg, bg) -> readable(fg, bg, where) }
            }
        }
    }

    @Test
    fun `a paragraph is boxed only when all of it is quoted`() {
        val other = Style(quote = QuoteKind.OTHER)
        val mine = Style(quote = QuoteKind.REPLIED_USER)
        assertEquals(QuoteKind.OTHER, wholeQuote(listOf(Inline.Text("a", other), Inline.LineBreak, Inline.Text(" "), Inline.Text("b", other))))
        assertEquals(QuoteKind.REPLIED_USER, wholeQuote(listOf(Inline.Text("a", other), Inline.Spoiler(listOf(Inline.Text("b", mine))))))
        assertNull(wholeQuote(listOf(Inline.Text("a", other), Inline.Text("reply"))))
        assertNull(wholeQuote(listOf(Inline.LineBreak)))
    }

    private fun pairs(s: ColorScheme) = listOf(
        s.onPrimary to s.primary,
        s.onPrimaryContainer to s.primaryContainer,
        s.onSecondaryContainer to s.secondaryContainer,
    )

    private fun readable(fg: Color, bg: Color, where: String) {
        val (a, b) = fg.luminance() to bg.luminance()
        val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue(ratio >= 4.5f, "$where: $fg on $bg is ${"%.2f".format(ratio)}:1")
    }
}
