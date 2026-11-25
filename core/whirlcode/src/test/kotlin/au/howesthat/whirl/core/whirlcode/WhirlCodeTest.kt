package au.howesthat.whirl.core.whirlcode

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.scraper.PostBodyParser
import au.howesthat.whirl.core.scraper.ThreadPageParser
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhirlCode to the site's HTML, compared as the app sees both: parsed to [PostBody]. Oracles: Dallin's every-code test
 * post (`fixtures/whirlcode/post_76888474.txt`, rendered in `thread_9244p8v1_whirlcode.html`), the wiki's inline table
 * (`wiki_whirlcode.html`) and the links in all fixtures. Cases marked "guess" wait for the edge-case capture
 * (`docs/plans/post-writer.md` §1.4), now settled by the edge-case post.
 */
class WhirlCodeTest {

    private val fixtures = File(System.getProperty("whirl.fixtures") ?: error("whirl.fixtures not set"))

    private fun body(source: String): List<Block> = PostBodyParser.parse(WhirlCode.toHtml(source)).blocks

    /**
     * As a post's body reaches the parser from a page: re-serialised by jsoup (ThreadPageParser stores `element.html()`),
     * which puts a line break after a `<br>` inside an inline tag, read as a space. The site's posts in the app carry
     * that space, so a comparison with them does the same to Whirl's HTML.
     */
    private fun stored(source: String): List<Block> =
        PostBodyParser.parse(Jsoup.parseBodyFragment(WhirlCode.toHtml(source)).body().html()).blocks
    private fun site(html: String): List<Block> = PostBodyParser.parse(html).blocks

    @Test
    fun `the every-code test post renders as the site rendered it`() {
        val source = File(fixtures, "whirlcode/post_76888474.txt").readText()
        val page = ThreadPageParser.parse(File(fixtures, "html/thread_9244p8v1_whirlcode.html").readText())
        val posted = PostBodyParser.parse(page.posts.single { it.number == 37 }.bodyHtml).blocks
        // Dallin quoted a post and added a paragraph above the test text when posting.
        val start = posted.indexOfFirst { (it as? Block.Paragraph)?.text()?.startsWith("Formatting test") == true }
        val expected = posted.drop(start)
        val actual = body(source)
        // Block by block first, so a failure names the block that differs.
        expected.zip(actual).forEachIndexed { i, (e, a) -> assertEquals(e, a, "block $i") }
        assertEquals(expected.size, actual.size)
    }

    @Test
    fun `the edge-case post renders as the site rendered it`() {
        // Dallin's post of 2026-11-25 (reply 76935755): an announcement, then fixtures/whirlcode/edge_cases.txt.
        val source = File(fixtures, "whirlcode/edge_cases.txt").readText()
        val page = ThreadPageParser.parse(File(fixtures, "html/thread_9244p8v1_edgecases.html").readText())
        val posted = PostBodyParser.parse(page.posts.single { it.replyId == 76935755L }.bodyHtml).blocks
        val start = posted.indexOfFirst { (it as? Block.Paragraph)?.text()?.startsWith("Edge cases for Whirl") == true }
        val expected = posted.drop(start)
        val actual = stored(source)
        expected.zip(actual).forEachIndexed { i, (e, a) -> assertEquals(e, a, "block $i") }
        assertEquals(expected.size, actual.size)
    }

    /** Each row of the wiki's inline table: source → the site's result, without the row's prose note. */
    private val wikiRows = mapOf(
        "[*bold*]" to "<strong>bold</strong>",
        "[/italic/]" to "<em>italic</em>",
        "[(small)]" to "<small>small</small>",
        "[-strike-]" to "<strike>strike</strike>",
        "[#monospace#]" to "<tt>monospace</tt>",
        "[~serif~]" to "<span class=\"wcserif\">serif</span>",
        "H[\\2\\]O" to "H<sub>2</sub>O",
        "500m[^2^]" to "500m<sup>2</sup>",
        "[\"forum quote\"]" to "<span class=\"wcrep1\">forum quote</span>",
        "['external quote']" to "<span class=\"wcrep2\">external quote</span>",
        "[`alternate quote`]" to "<span class=\"wcgrey\">alternate quote</span>",
        "[_spoiler_]" to "<span class=\"wcspoil\">spoiler</span>",
        "[?google search?]" to "<a class=\"external\" rel=\"nofollow\" target=\"_blank\" href=\"http://www.google.com.au/search?q=google search\">google search</a>",
        "[[whirlcode]]" to "<a class=\"wiki\" href=\"/wiki/whirlcode\">whirlcode</a>",
        // The wiki shows this one literally (its `|` splits the wiki's own table cell); in a post it is a link.
        "[[whirlcode|label]]" to "<a class=\"wiki\" href=\"/wiki/whirlcode\">label</a>",
        "[.*escaped*.]" to "[*escaped*]",
        "-" to "–",
        "--" to "—",
    )

    @Test
    fun `every row of the wiki's inline table`() {
        val wiki = Jsoup.parse(File(fixtures, "html/wiki_whirlcode.html").readText())
        val codes = wiki.select("table").first()!!.select("tr").drop(1).map { it.child(0).text().replace("​", "") }
        assertEquals(codes.toSet(), wikiRows.keys, "a row added to the wiki needs an expected value here")
        wikiRows.forEach { (code, html) ->
            // Dashes only count when spaced, as in the wiki's own example.
            val source = if (code == "-" || code == "--") "a $code b" else code
            val expected = if (code == "-" || code == "--") "a $html b" else html
            assertEquals(site("<p>$expected</p>"), body(source), code)
        }
    }

    @Test
    fun `links follow the site's rules from the fixtures`() {
        fun link(source: String) = body(source).single().let { (it as Block.Paragraph).inlines.single() as Inline.Link }
        assertEquals(Inline.Link("https://example.com", "https://example.com"), link("https://example.com"))
        assertEquals(
            Inline.Link("https://forums.whirlpool.net.au/forum-replies.cfm?t=2691384", "/forum-replies.cfm?t=2691384"),
            link("https://forums.whirlpool.net.au/forum-replies.cfm?t=2691384"),
        )
        assertEquals(Inline.Link("https://whrl.pl/RgCnTb", "whrl.pl/RgCnTb"), link("https://whrl.pl/RgCnTb"))
        assertEquals(Inline.Link("mailto:someone@example.com", "someone@example.com"), link("someone@example.com"))
        assertEquals(Inline.Link("https://www.google.com.au", "Google"), link("<a href=\"https://www.google.com.au\">Google</a>"))
        assertEquals(
            Inline.Link("https://forums.whirlpool.net.au/thread/3mnkq4v9?ur=1#bottom", "Reps"),
            link("<a href=\"https://forums.whirlpool.net.au/thread/3mnkq4v9?ur=1#bottom\">Reps</a>"),
        )
        // A bare www. address is not linked (edge-case post, line 10); trailing punctuation is the sentence's (line 9).
        assertEquals(listOf(Block.Paragraph(listOf(Inline.Text("see www.example.com/page")))), body("see www.example.com/page"))
        val sentence = (body("See https://example.com/a_(b), then go.").single() as Block.Paragraph).inlines
        assertEquals(Inline.Link("https://example.com/a_(b)", "https://example.com/a_(b)"), sentence[1])
    }

    @Test
    fun `a single newline is a line break, a blank line a new paragraph`() {
        assertEquals(site("<p>one<br>two</p>\n<p>three</p>"), body("one\ntwo\n\nthree"))
    }

    @Test
    fun `codes match their nearest closer, across lines but not past the paragraph`() {
        assertEquals(site("<p><strong><em>both</em></strong> and [*open</p>"), body("[*[/both/]*] and [*open"))
        // An unclosed code takes everything to the next closer, on a later line too (edge-case post, lines 1 to 5)...
        assertEquals(site("<p><strong>a<br>[*b<br>c</strong> d*]</p>"), body("[*a\n[*b\nc*] d*]"))
        // ...but a blank line ends the paragraph, and the code with it (a guess: the post never tried this).
        assertEquals(site("<p>[*a</p>\n<p>b*]</p>"), body("[*a\n\nb*]"))
        assertEquals(site("<p>array[i] costs \$5 * 2</p>"), body("array[i] costs \$5 * 2"))
        assertEquals(site("<p>&gt;not a quote</p>"), body(">not a quote"))
        assertEquals(site("<p>#hashtag</p>"), body("#hashtag"))
    }

    @Test
    fun `an entity stays an entity, so a pipe can sit in a table cell`() {
        assertEquals(site("<table><tr><td>a | b</td><td>c</td></tr></table>"), body("| a &#124; b | c"))
        assertEquals(site("<p>Fish &amp; chips &lt;3</p>"), body("Fish & chips <3"))
    }

    @Test
    fun `codes over the limits are text, a rule needs a blank line after it, and $ lines run together`() {
        // Edge-case post, lines 15, 14 and the end.
        assertEquals(
            site("<blockquote class=\"wcrep1\"><blockquote class=\"wcrep1\"><blockquote class=\"wcrep1\"><p>&gt; deep</p></blockquote></blockquote></blockquote>"),
            body(">>>> deep"),
        )
        assertEquals(site("<p>-----<br>Last line.</p>"), body("-----\nLast line."))
        assertEquals(site("<p>a</p><hr><p>b</p>"), body("a\n\n-----\n\nb"))
        assertEquals(site("<pre>one\ntwo</pre>"), body("$ one\n$ two"))
    }

    @Test
    fun `a less-than typed as an entity never becomes a tag`() {
        assertEquals(site("<p>&lt;b&gt;x&lt;/b&gt;</p>"), body("&lt;b&gt;x&lt;/b&gt;"))
    }

    @Test
    fun `nothing but the allowed markup reaches the HTML`() {
        val hostile = listOf(
            "<script>alert(1)</script>",
            "<a href=\"javascript:alert(1)\">x</a>",
            "<a href=\"https://e.com\" onclick=\"alert(1)\">x</a>",
            "<img src=x onerror=alert(1)>",
            "<b onmouseover=\"alert(1)\">x</b>",
            "[[x\" onclick=\"alert(1)]]",
            "[?x\" onclick=\"alert(1)?]",
            "<a href=\"https://e.com\">[*<script>*]</a>",
            "@1 <script>alert(1)</script>",
            "$ <script>alert(1)</script>",
            "| <script> | x",
        )
        for (source in hostile) {
            val html = WhirlCode.toHtml(source)
            val doc = Jsoup.parseBodyFragment(html)
            assertTrue(doc.select("script, img, iframe, style").isEmpty(), "$source → $html")
            doc.allElements.forEach { el ->
                el.attributes().forEach { a ->
                    assertTrue(a.key in setOf("href", "class", "rel", "target", "title", "style", "onclick"), "$source → $html")
                    if (a.key == "onclick") assertEquals("p", el.parent()?.tagName(), "only a jump link's own onclick: $html")
                    if (a.key == "href") assertFalse(a.value.trim().startsWith("javascript", ignoreCase = true), "$source → $html")
                }
            }
        }
    }

    @Test
    fun `allowed tags work only as pairs, and anything else shows as typed`() {
        // Edge-case post, line 12.
        assertEquals(site("<p><strong>bold<br>plain</strong></p>"), body("<b>bold\nplain</b>"))
        assertEquals(site("<p>&lt;b&gt;open and &lt;/i&gt; closed</p>"), body("<b>open and </i> closed"))
        assertEquals(site("<p>&lt;u&gt;u&lt;/u&gt; &lt;a href=\"javascript:x\"&gt;j&lt;/a&gt;</p>"), body("<u>u</u> <a href=\"javascript:x\">j</a>"))
    }

    @Test
    fun `a long run of unclosed codes stays fast`() {
        val source = "[*".repeat(5_000)
        val started = System.nanoTime()
        WhirlCode.toHtml(source)
        assertTrue(System.nanoTime() - started < 2_000_000_000L, "linear, not exponential")
    }

    private fun Block.Paragraph.text() = inlines.joinToString("") { (it as? Inline.Text)?.text ?: "" }
}
