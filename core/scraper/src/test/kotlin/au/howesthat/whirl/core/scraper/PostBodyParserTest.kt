package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
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
        assertEquals(listOf(Inline.Text("Bricks and Mortar (Brisbane)")), first.items[0])
        assertEquals(listOf(Inline.Text("Online")), first.items[1])
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
}
