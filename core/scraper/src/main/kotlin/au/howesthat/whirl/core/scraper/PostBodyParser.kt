package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.QuoteKind
import au.howesthat.whirl.core.model.Style
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts a post body (`Post.bodyHtml`) into blocks for native rendering. Unknown elements fall back to their text,
 * so new markup degrades to plain text rather than disappearing.
 */
object PostBodyParser {

    private val JUMP_TO_REPLY = Regex("""jumpToReplyId\((\d+)\)""")
    private val BLOCK_TAGS = setOf("p", "div", "blockquote", "ul", "ol", "pre", "h1", "h2", "h3", "h4", "h5", "h6", "table", "hr")

    fun parse(bodyHtml: String): PostBody {
        val root = Jsoup.parseBodyFragment(bodyHtml, "$FORUMS_BASE/").body()
        val out = Builder()
        blocks(root, out, Style())
        out.flush()
        return PostBody(out.blocks)
    }

    private class Builder {
        val blocks = mutableListOf<Block>()
        private val inlines = mutableListOf<Inline>()

        fun add(inline: Inline) {
            val last = inlines.lastOrNull()
            if (inline is Inline.Text && last is Inline.Text && last.style == inline.style) {
                inlines[inlines.lastIndex] = Inline.Text(last.text + inline.text, inline.style)
            } else {
                inlines += inline
            }
        }

        fun block(block: Block) {
            flush()
            blocks += block
        }

        fun flush() {
            val trimmed = trim(inlines)
            if (trimmed.isNotEmpty()) blocks += Block.Paragraph(trimmed)
            inlines.clear()
        }
    }

    private fun blocks(parent: Element, out: Builder, style: Style) {
        for (node in parent.childNodes()) {
            if (node !is Element) {
                inline(node, out::add, style)
                continue
            }
            when {
                node.tagName() == "p" && node.hasClass("reference") -> {
                    val a = node.selectFirst("a")
                    val postId = a?.attr("title")?.toLongOrNull()
                        ?: JUMP_TO_REPLY.find(a?.attr("onclick").orEmpty())?.groupValues?.get(1)?.toLong()
                    out.block(Block.QuoteAttribution(node.text().squash(), postId))
                }
                node.tagName() == "ul" || node.tagName() == "ol" -> {
                    val items = node.select("> li").map { li -> trim(collectInlines(li, style)) }
                    out.block(Block.ListBlock(ordered = node.tagName() == "ol", items = items))
                }
                node.tagName() == "pre" -> out.block(Block.Code(node.wholeText().trimEnd()))
                node.tagName() == "img" -> out.block(Block.Image(absoluteUrl(node.attr("src")), node.attr("alt").ifEmpty { null }))
                node.tagName() == "hr" -> out.flush()
                node.tagName() == "blockquote" -> {
                    out.flush()
                    blocks(node, out, style.copy(quote = QuoteKind.OTHER))
                    out.flush()
                }
                // Block elements, and wrappers holding blocks (archive pages wrap bodies in span[itemprop=text]).
                node.tagName() in BLOCK_TAGS || node.children().any { it.tagName() in BLOCK_TAGS } -> {
                    out.flush()
                    blocks(node, out, styleFor(node, style))
                    out.flush()
                }
                else -> inline(node, out::add, style)
            }
        }
    }

    private fun collectInlines(element: Element, style: Style): List<Inline> {
        val b = Builder()
        for (child in element.childNodes()) inline(child, b::add, style)
        return b.run { flushInlinesForList() }
    }

    // Lists keep their items' inlines rather than splitting them into paragraphs.
    private fun Builder.flushInlinesForList(): List<Inline> {
        flush()
        return blocks.flatMapIndexed { i, block ->
            val inl = (block as? Block.Paragraph)?.inlines.orEmpty()
            if (i == 0) inl else listOf(Inline.LineBreak) + inl
        }
    }

    private fun inline(node: Node, emit: (Inline) -> Unit, style: Style) {
        when (node) {
            is TextNode -> {
                val text = node.text().replace(' ', ' ')
                if (text.isNotEmpty()) emit(Inline.Text(text, style))
            }
            is Element -> {
                val tag = node.tagName()
                when {
                    tag == "br" -> emit(Inline.LineBreak)
                    tag == "wbr" -> Unit
                    tag == "a" && node.hasAttr("href") ->
                        emit(Inline.Link(absoluteUrl(node.attr("href")), node.text().squash().ifEmpty { node.attr("href") }, style))
                    tag == "img" -> emit(Inline.Link(absoluteUrl(node.attr("src")), node.attr("alt").ifEmpty { "[image]" }, style))
                    tag == "span" && node.hasClass("wcspoil") -> {
                        val inner = mutableListOf<Inline>()
                        node.childNodes().forEach { inline(it, inner::add, style) }
                        emit(Inline.Spoiler(inner))
                    }
                    else -> {
                        val childStyle = styleFor(node, style)
                        // Block elements nested inside inline context (e.g. <p> in <li>) become line breaks.
                        val isBlock = tag in BLOCK_TAGS
                        if (isBlock) emit(Inline.LineBreak)
                        node.childNodes().forEach { inline(it, emit, childStyle) }
                        if (isBlock) emit(Inline.LineBreak)
                    }
                }
            }
        }
    }

    private fun styleFor(el: Element, s: Style): Style = when (el.tagName()) {
        "b", "strong" -> s.copy(bold = true)
        "i", "em" -> s.copy(italic = true)
        "u", "ins" -> s.copy(underline = true)
        "s", "strike", "del" -> s.copy(strike = true)
        "code", "tt", "kbd", "samp" -> s.copy(code = true)
        "span" -> when {
            el.hasClass("wcrep1") -> s.copy(quote = QuoteKind.REPLIED_USER)
            el.hasClass("wcrep2") -> s.copy(quote = QuoteKind.OTHER)
            else -> s
        }
        else -> s
    }

    /** Drops leading/trailing whitespace and line breaks of a paragraph. */
    private fun trim(inlines: List<Inline>): List<Inline> {
        val list = inlines.toMutableList()
        while (list.isNotEmpty()) {
            val first = list.first()
            when {
                first is Inline.LineBreak -> list.removeAt(0)
                first is Inline.Text && first.text.isBlank() -> list.removeAt(0)
                first is Inline.Text -> { list[0] = first.copy(text = first.text.trimStart()); break }
                else -> break
            }
        }
        while (list.isNotEmpty()) {
            val last = list.last()
            when {
                last is Inline.LineBreak -> list.removeAt(list.lastIndex)
                last is Inline.Text && last.text.isBlank() -> list.removeAt(list.lastIndex)
                last is Inline.Text -> { list[list.lastIndex] = last.copy(text = last.text.trimEnd()); break }
                else -> break
            }
        }
        return list
    }
}
