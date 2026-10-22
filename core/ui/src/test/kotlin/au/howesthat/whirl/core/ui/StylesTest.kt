package au.howesthat.whirl.core.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StylesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun files(folder: String) = context.assets.list(folder).orEmpty().filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }
    private val palettes = Styles.palettes(context).filterNot { it.isWallpaper }
    private val layouts = Styles.layouts(context)

    @Test
    fun `every bundled file loads, named after its file, defaults first`() {
        assertEquals(files("palettes").toSet(), palettes.map { it.id }.toSet(), "a palette file failed to parse or is misnamed")
        assertEquals(files("layouts").toSet(), layouts.map { it.id }.toSet(), "a layout file failed to parse or is misnamed")
        assertEquals(Palette.WALLPAPER, Styles.palettes(context).first().id, "Wallpaper leads on Android 12+")
        assertEquals(Palette.Fallback, Styles.palette(context, Palette.WHIRL), "whirl.json and Palette.Fallback must agree")
        assertEquals(LayoutStyle.Fallback, layouts.first(), "classic.json and LayoutStyle.Fallback must agree")
        assertEquals(Palette.WHIRL, Styles.palette(context, "no-such-palette").id)
        assertEquals(LayoutStyle.CLASSIC, Styles.layout(context, "no-such-layout").id)
    }

    @Test
    fun `every colour role is a known one, and a top bar comes with its text colour`() {
        palettes.forEach { p ->
            listOf(false, true).forEach { dark ->
                p.colorScheme(dark)
                Palette.EXTRA_ROLES.forEach { p.extra(it, dark) }
                if (p.extra("topBar", dark) != null) assertNotNull(p.extra("onTopBar", dark), "${p.id}: topBar without onTopBar")
            }
        }
        assertFailsWith<IllegalArgumentException> { Palette("x", "X", light = mapOf("primry" to "#000000")).colorScheme(dark = false) }
        assertFailsWith<IllegalArgumentException> { Palette("x", "X", light = mapOf("primary" to "blue")).colorScheme(dark = false) }
    }

    @Test
    fun `text, accents and quotes reach 4,5 to 1 with every palette and layout, light and dark`() {
        for (p in palettes) for (l in layouts) for (dark in listOf(false, true)) {
            val s = p.colorScheme(dark)
            val where = "${p.id} + ${l.id} ${if (dark) "dark" else "light"}"
            // As WhirlTheme resolves it: the palette's tint, else a wash of primary where the layout tints rows.
            val unread = p.extra("unread", dark) ?: s.primary.copy(alpha = 0.08f).compositeOver(s.surface).takeIf { l.layout.unread == Unread.ACCENT }
            val quotes = listOf(
                p.extra("quoteRepliedUser", dark) ?: if (dark) QuoteColors.repliedUserDark else QuoteColors.repliedUserLight,
                p.extra("quoteOther", dark) ?: if (dark) QuoteColors.otherDark else QuoteColors.otherLight,
            )
            listOfNotNull(s.background, s.surface, s.surfaceContainerHigh, unread).forEach { bg ->
                listOf(s.onSurface, s.onSurfaceVariant, s.primary).forEach { fg -> readable(fg, bg, where) }
            }
            // Flat posts sit on the page background, cards on the surface, boxed quotes on surfaceContainerLow.
            quotes.forEach { q ->
                readable(q, s.background, where)
                readable(q, s.surface, where)
                if (l.layout.quotes == Quotes.BOXED) readable(q, s.surfaceContainerLow, where)
            }
            pairs(s).forEach { (fg, bg) -> readable(fg, bg, where) }
            p.extra("topBar", dark)?.let { bar ->
                val on = p.extra("onTopBar", dark)!!
                readable(on, bar, "$where top bar")
                readable(on.copy(alpha = 0.8f).compositeOver(bar), bar, "$where top bar subtitle")
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
