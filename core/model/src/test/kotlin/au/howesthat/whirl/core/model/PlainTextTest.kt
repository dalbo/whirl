package au.howesthat.whirl.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** A post as text to copy (a post's Copy text and Select text). */
class PlainTextTest {

    @Test
    fun `blocks a blank line apart, with quotes, lists, tables and spoilers readable`() {
        val body = PostBody(
            listOf(
                Block.QuoteAttribution("BRK writes...", 5),
                Block.Quote(QuoteKind.REPLIED_USER, listOf(Block.Paragraph(listOf(Inline.Text("their words"))), Block.Paragraph(listOf(Inline.Text("more"))))),
                Block.Paragraph(listOf(Inline.Text("See "), Inline.Link("https://e.com", "this", Style(bold = true)), Inline.LineBreak, Inline.Text("and "), Inline.Spoiler(listOf(Inline.Text("a secret"))))),
                Block.ListBlock(
                    ordered = true,
                    items = listOf(
                        ListItem(listOf(Inline.Text("one")), listOf(Block.ListBlock(ordered = false, items = listOf(ListItem(listOf(Inline.Text("one a"))))))),
                        ListItem(listOf(Inline.Text("two"))),
                    ),
                ),
                Block.Rule,
                Block.Table(listOf(listOf(TableCell(listOf(Inline.Text("a")), header = true), TableCell(listOf(Inline.Text("b")), header = true)), listOf(TableCell(listOf(Inline.Text("1"))), TableCell(listOf(Inline.Text("2")))))),
                Block.Code("$ x  y"),
            ),
        )
        assertEquals(
            listOf(
                "BRK writes...",
                "> their words\n>\n> more",
                "See this\nand a secret",
                "1. one\n  • one a\n2. two",
                "a\tb\n1\t2",
                "$ x  y",
            ).joinToString("\n\n"),
            body.plainText(),
        )
    }
}
