package au.howesthat.whirl.core.whirlcode

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.ListItem
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.QuoteKind
import au.howesthat.whirl.core.model.Style
import au.howesthat.whirl.core.model.TableCell
import au.howesthat.whirl.core.scraper.PostBodyParser
import au.howesthat.whirl.core.scraper.ThreadPageParser
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/** Quotes for the post writer: `@id name writes...`, then the poster's own lines as `["…"]` (plan §0, §3). */
class QuoteTest {

    private val page by lazy {
        val dir = File(System.getProperty("whirl.fixtures") ?: error("whirl.fixtures not set"), "html")
        ThreadPageParser.parse(File(dir, "thread_9244p8v1_whirlcode.html").readText())
    }

    private fun quote(replyId: Long): String {
        val post = page.posts.single { it.replyId == replyId }
        return Quote.of(post.replyId, post.author.name, PostBodyParser.parse(post.bodyHtml))
    }

    private fun rendered(code: String) = PostBodyParser.parse(WhirlCode.toHtml(code)).blocks
    private val q = Style(quote = QuoteKind.REPLIED_USER)

    @Test
    fun `the poster's own words, their quoted post left out`() {
        // BRK quotes their own earlier post, then adds one line.
        val code = quote(76884295)
        assertEquals("@76884295 BRK writes...\n\n[\"Don't know if you missed this suggestion. Reposting in case.\"]", code)
        assertEquals(
            listOf(
                Block.QuoteAttribution("BRK writes...", 76884295),
                Block.Paragraph(listOf(Inline.Text("Don't know if you missed this suggestion. Reposting in case.", q))),
            ),
            rendered(code),
        )
    }

    @Test
    fun `formatting, links and lists come along, each line quoted on its own`() {
        val body = PostBody(
            listOf(
                Block.Paragraph(
                    listOf(
                        Inline.Text("See "), Inline.Text("this", Style(bold = true)), Inline.Text(" at "),
                        Inline.Link("https://example.com", "https://example.com"), Inline.LineBreak, Inline.Text("Second line"),
                    ),
                ),
                Block.ListBlock(ordered = false, items = listOf(au.howesthat.whirl.core.model.ListItem(listOf(Inline.Text("a point"))))),
            ),
        )
        val code = Quote.of(1, "Someone", body)
        assertEquals("@1 Someone writes...\n\n[\"See [*this*] at https://example.com\"]\n[\"Second line\"]\n\n* [\"a point\"]", code)
        val blocks = rendered(code)
        assertEquals(
            Block.Paragraph(
                listOf(
                    Inline.Text("See ", q), Inline.Text("this", q.copy(bold = true)), Inline.Text(" at ", q),
                    Inline.Link("https://example.com", "https://example.com", q), Inline.LineBreak, Inline.Text("Second line", q),
                ),
            ),
            blocks[1],
        )
    }

    @Test
    fun `a post that is only someone else's quote gives just the attribution`() {
        val body = PostBody(
            listOf(
                Block.QuoteAttribution("BRK writes...", 5),
                Block.Paragraph(listOf(Inline.Text("their words", q))),
                Block.Quote(QuoteKind.REPLIED_USER, listOf(Block.Paragraph(listOf(Inline.Text("a block quote", q))))),
            ),
        )
        assertEquals("@9 Name writes...", Quote.of(9, "Name", body))
    }

    @Test
    fun `text that looks like code is quoted as text`() {
        val body = PostBody(listOf(Block.Paragraph(listOf(Inline.Text("Type [*x*] or \"] to end, - like so")))))
        val code = Quote.of(1, "A", body)
        assertEquals(
            listOf(Inline.Text("Type [*x*] or \"] to end, - like so", q)),
            (rendered(code)[1] as Block.Paragraph).inlines,
        )
    }

    @Test
    fun `quoted text stays readable, with no entities where none are needed and no stray spaces`() {
        // From the phone (2026-11-20): leo27's list came out as `[" &#45; the from wallpaper "]`, and `d@llin` as d&#064;llin.
        val body = PostBody(
            listOf(
                Block.Paragraph(listOf(Inline.Text("d@llin needs testers"))),
                Block.Paragraph(listOf(Inline.Text("Notes: "), Inline.LineBreak, Inline.Text(" - the wallpaper colour "), Inline.LineBreak, Inline.Text(" - a purple icon"))),
            ),
        )
        assertEquals(
            "@1 A writes...\n\n[\"d@llin needs testers\"]\n\n[\"Notes:\"]\n[\"- the wallpaper colour\"]\n[\"- a purple icon\"]",
            Quote.of(1, "A", body),
        )
        // An address still is broken up, so it stays text rather than becoming a link.
        assertEquals("@1 A writes...\n\n[\"mail me at a&#064;b.com\"]", Quote.of(1, "A", PostBody(listOf(Block.Paragraph(listOf(Inline.Text("mail me at a@b.com")))))))
    }

    @Test
    fun `lists, tables and indents keep their structure, their text quoted`() {
        // From the phone (2026-11-26): a quoted list came out as the quoter's own list.
        fun item(text: String, vararg sub: Block.ListBlock) = ListItem(listOf(Inline.Text(text)), sub.toList())
        val body = PostBody(
            listOf(
                Block.ListBlock(ordered = false, items = listOf(item("one", Block.ListBlock(ordered = true, items = listOf(item("one a")))), item("two"))),
                Block.Table(
                    listOf(
                        listOf(TableCell(listOf(Inline.Text("Name", Style(bold = true))), header = true), TableCell(listOf(Inline.Text("Size", Style(bold = true))), header = true)),
                        listOf(TableCell(listOf(Inline.Text("a"))), TableCell(listOf(Inline.Text("1")))),
                    ),
                ),
                Block.Quote(null, listOf(Block.Paragraph(listOf(Inline.Text("indented"))), Block.ListBlock(ordered = false, items = listOf(item("in an indent"))))),
                Block.Rule,
                Block.Code("$ not quoted\n* nor this"),
            ),
        )
        val code = Quote.of(1, "A", body)
        assertEquals(
            listOf(
                "@1 A writes...",
                "* [\"one\"]\n*# [\"one a\"]\n* [\"two\"]",
                "| [\"Name\"] | [\"Size\"]\n| --- | ---\n| [\"a\"] | [\"1\"]",
                ": [\"indented\"]",
                ":* [\"in an indent\"]",
                "-----",
                "$$\n$ not quoted\n* nor this\n$$",
            ).joinToString("\n\n"),
            code,
        )
        // Read back, every word is quoted text and the structure is as it was.
        val blocks = rendered(code)
        val list = blocks[1] as Block.ListBlock
        assertEquals(listOf(Inline.Text("one", q)), list.items[0].inlines)
        assertEquals(listOf(Inline.Text("one a", q)), list.items[0].sublists.single().items.single().inlines)
        val table = blocks[2] as Block.Table
        assertEquals(listOf(Inline.Text("1", q)), table.rows[1][1].inlines)
        assertEquals(Block.Rule, blocks[4])
        assertEquals(Block.Code("$ not quoted\n* nor this"), blocks[5])
    }
}
