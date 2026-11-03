package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.Block
import kotlin.test.assertFalse
import au.howesthat.whirl.core.model.searchText
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.CellAlign
import au.howesthat.whirl.core.model.TableCell
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.ListItem
import au.howesthat.whirl.core.model.QuoteKind
import au.howesthat.whirl.core.model.Style
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PostBodyParserTest {

    private fun postBody(fixture: String, replyId: Long) =
        PostBodyParser.parse(ThreadPageParser.parse(Fixtures.html(fixture)).posts.single { it.replyId == replyId }.bodyHtml)

    @Test
    fun `quote attribution, quoted text and reply`() {
        val body = postBody("thread_3pxj858k_p2.html", 75543951)
        assertEquals(3, body.blocks.size)
        assertEquals(Block.QuoteAttribution("samuria writes...", 75542543), body.blocks[0])
        val quoted = assertIs<Block.Paragraph>(body.blocks[1]).inlines.single()
        assertIs<Inline.Text>(quoted)
        assertEquals(QuoteKind.REPLIED_USER, quoted.style.quote)
        assertTrue(quoted.text.startsWith(". Do you happen to know the frequency"))
        val reply = assertIs<Inline.Text>(assertIs<Block.Paragraph>(body.blocks[2]).inlines.single())
        assertEquals(Style(), reply.style)
        assertTrue(reply.text.startsWith("Correct me it I'm wrong"))
    }

    @Test
    fun `unordered list from a real post`() {
        val lists = ThreadPageParser.parse(Fixtures.html("thread_sticky_3q6vxnj9.html")).posts
            .flatMap { PostBodyParser.parse(it.bodyHtml).blocks }
            .filterIsInstance<Block.ListBlock>()
        assertTrue(lists.isNotEmpty())
        val first = lists.first()
        assertEquals(false, first.ordered)
        assertEquals(ListItem(listOf(Inline.Text("Bricks and Mortar (Brisbane)"))), first.items[0])
        assertEquals(ListItem(listOf(Inline.Text("Online"))), first.items[1])
    }

    @Test
    fun `a nested list beside its item belongs to the item before it`() {
        val post = ThreadPageParser.parse(Fixtures.html("thread_9244p8v1_p2.html")).posts.single { it.number == 34 }
        val lists = PostBodyParser.parse(post.bodyHtml).blocks.filterIsInstance<Block.ListBlock>()
        val people = lists[0].items
        assertEquals(listOf("RayMondo:", "leo27:"), people.map { (it.inlines.single() as Inline.Text).text })
        assertEquals(listOf(2, 2), people.map { it.sublists.single().items.size })
        val ray = people[0].sublists.single().items
        assertTrue((ray[1].inlines.single() as Inline.Text).text.contains("\"Watched threads\" now"))

        val next = lists[1].items
        assertEquals(3, next.size)
        assertEquals(emptyList(), next[0].sublists)
        assertEquals(1, next[1].sublists.single().items.size, "the undo item's note")
    }

    /** The Whirlcode test post: every code on the wiki's Whirlcode page. */
    private val whirlcode by lazy {
        val post = ThreadPageParser.parse(Fixtures.html("thread_9244p8v1_whirlcode.html")).posts.single { it.number == 37 }
        PostBodyParser.parse(post.bodyHtml).blocks
    }

    private fun paragraph(startsWith: String) =
        whirlcode.filterIsInstance<Block.Paragraph>().single { (it.inlines.first() as? Inline.Text)?.text?.startsWith(startsWith) == true }.inlines

    @Test
    fun `whirlcode inline styles`() {
        val inline = paragraph("Inline:")
        fun styleOf(text: String) = inline.filterIsInstance<Inline.Text>().single { it.text == text }.style
        assertEquals(Style(small = true), styleOf("small"))
        assertEquals(Style(serif = true), styleOf("serif"))
        // H[\2\]O and 500m[^2^]
        assertEquals(listOf(Style(sub = true), Style(sup = true)), inline.filterIsInstance<Inline.Text>().filter { it.text == "2" }.map { it.style })
        assertEquals(Style(bold = true, italic = true), styleOf("bold italic"))
        val quotes = paragraph("Quotes inline:").filterIsInstance<Inline.Text>().associate { it.text to it.style.quote }
        assertEquals(QuoteKind.REPLIED_USER, quotes["forum quote"])
        assertEquals(QuoteKind.OTHER, quotes["external quote"])
        assertEquals(QuoteKind.ALTERNATE, quotes["alternate quote"])
    }

    @Test
    fun `whirlcode quotes nest and take the site's colour for each depth, and an indent is no quote`() {
        val quotes = whirlcode.filterIsInstance<Block.Quote>()
        assertEquals(listOf(null, QuoteKind.REPLIED_USER, null), quotes.map { it.kind })
        assertEquals(Block.Paragraph(listOf(Inline.Text("An indented line"))), quotes[0].blocks.single())

        val reply = quotes[1].blocks
        val two = assertIs<Block.Quote>(reply[1])
        assertEquals(QuoteKind.OTHER, two.kind)
        assertEquals(QuoteKind.ALTERNATE, assertIs<Block.Quote>(two.blocks[1]).kind)
        assertEquals(Block.Paragraph(listOf(Inline.Text("Reply quote, three deep", Style(quote = QuoteKind.ALTERNATE)))), assertIs<Block.Quote>(two.blocks[1]).blocks.single())
        assertIs<Block.ListBlock>(reply[3])
        assertEquals(QuoteKind.OTHER, assertIs<Block.Quote>(reply[4]).kind, "the numbered list's two-deep quote")

        // ": >": a reply quote inside an indent is the first reply quote level.
        assertEquals(QuoteKind.REPLIED_USER, assertIs<Block.Quote>(quotes[2].blocks.single()).kind)
    }

    @Test
    fun `whirlcode lists, rule and table`() {
        val lists = whirlcode.filterIsInstance<Block.ListBlock>()
        // "##" straight after a "*" list: a numbered list with no item above it.
        val orphan = lists.last()
        assertEquals(emptyList(), orphan.items.single().inlines)
        assertEquals("a numbered list under it", (orphan.items.single().sublists.single().items.single().inlines.single() as Inline.Text).text)

        assertTrue(Block.Rule in whirlcode)
        val table = whirlcode.filterIsInstance<Block.Table>().single()
        assertEquals(3, table.rows.size)
        assertEquals(
            listOf(
                TableCell(listOf(Inline.Text("Left", Style(bold = true))), header = true),
                TableCell(listOf(Inline.Text("Centre", Style(bold = true))), header = true, align = CellAlign.CENTER),
                TableCell(listOf(Inline.Text("Right", Style(bold = true))), header = true, align = CellAlign.END),
            ),
            table.rows[0],
        )
        assertEquals(4, table.rows[2].size)
    }

    @Test
    fun `a list nested inside its item is kept too`() {
        val list = PostBodyParser.parse("<ol><li>one<ul><li>a</li><li>b</li></ul></li><li>two</li></ol>").blocks.single() as Block.ListBlock
        assertEquals(listOf(Inline.Text("one")), list.items[0].inlines)
        assertEquals(listOf(ListItem(listOf(Inline.Text("a"))), ListItem(listOf(Inline.Text("b")))), list.items[0].sublists.single().items)
        assertEquals(ListItem(listOf(Inline.Text("two"))), list.items[1])
    }

    @Test
    fun `ordered list from a real post`() {
        val lists = ThreadPageParser.parse(Fixtures.html("thread_big_9w88m1qr_p1.html")).posts
            .flatMap { PostBodyParser.parse(it.bodyHtml).blocks }
            .filterIsInstance<Block.ListBlock>()
        assertTrue(lists.any { it.ordered && it.items.size >= 2 })
    }

    @Test
    fun `archive bodies wrapped in a span keep their paragraphs`() {
        val post = ThreadPageParser.parse(Fixtures.html("thread_archive_numeric.html")).posts.first()
        val blocks = PostBodyParser.parse(post.bodyHtml).blocks
        assertTrue(blocks.size > 1, "expected several paragraphs, got $blocks")
        assertEquals(listOf(Inline.Text("Hi everyone,")), assertIs<Block.Paragraph>(blocks.first()).inlines)
    }

    @Test
    fun `every fixture post body parses without losing text`() {
        for (file in Fixtures.allRawHtml().filter { it.name.startsWith("thread_") && "private" !in it.name }) {
            for (post in ThreadPageParser.parse(file.readText()).posts.filterNot { it.isHidden }) {
                val body = PostBodyParser.parse(post.bodyHtml)
                assertTrue(body.blocks.isNotEmpty(), "${file.name} #${post.number} has an empty body")
            }
        }
    }

    @Test
    fun `inline styles, links, spoilers and line breaks`() {
        val html = """<p>a <b>bold</b> <i>it</i> <span class="wcrep2">other</span><br>
            |<a href="/thread/3rvvvlwj">t</a> <a href="//example.com/x">e</a> <a class="inter" href="/wiki/wp_rules">w</a>
            |<span class="wcspoil">secret <b>x</b></span></p>""".trimMargin()
        val inlines = assertIs<Block.Paragraph>(PostBodyParser.parse(html).blocks.single()).inlines
        assertEquals(
            listOf(
                Inline.Text("a "),
                Inline.Text("bold", Style(bold = true)),
                Inline.Text(" "),
                Inline.Text("it", Style(italic = true)),
                Inline.Text(" "),
                Inline.Text("other", Style(quote = QuoteKind.OTHER)),
                Inline.LineBreak,
                Inline.Text(" "),
                Inline.Link("https://forums.whirlpool.net.au/thread/3rvvvlwj", "t"),
                Inline.Text(" "),
                Inline.Link("https://example.com/x", "e"),
                Inline.Text(" "),
                Inline.Link("https://whirlpool.net.au/wiki/wp_rules", "w"),
                Inline.Text(" "),
                Inline.Spoiler(listOf(Inline.Text("secret "), Inline.Text("x", Style(bold = true)))),
            ),
            inlines,
        )
    }

    @Test
    fun `images become placeholders`() {
        val blocks = PostBodyParser.parse("""<p>see</p><img src="//i.imgur.com/a.png" alt="pic">""").blocks
        assertEquals(Block.Image("https://i.imgur.com/a.png", "pic"), blocks.last())
    }

    @Test
    fun `find on page searches the text a post shows, but not its spoilers`() {
        val text = PostBody(whirlcode).searchText()
        val find = { q: String -> TextQuery(q).matches(text) }
        assertTrue(find("Bullet three deep"), "nested list items")
        assertTrue(find("fenced code block"), "code")
        assertTrue(find("Reply quote, three deep"), "nested quotes")
        assertTrue(find("Centre"), "table cells")
        assertTrue(find("Google"), "link text")
        assertTrue(find("bold italic"), "a word split across styles")
        assertFalse(find("hidden text"), "spoilers are never searched")
        assertTrue(find("Spoiler:"), "the text around a spoiler is")
    }
}
