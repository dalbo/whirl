package au.howesthat.whirl.core.scraper

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.CellAlign
import au.howesthat.whirl.core.model.TableCell
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.ListItem
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
        return PostBody(numberSpoilers(out.blocks))
    }

    /** Gives each spoiler its index in body order (block by block; list items before their sublists; cells by row). */
    private fun numberSpoilers(blocks: List<Block>): List<Block> {
        var next = 0
        fun inlines(list: List<Inline>): List<Inline> = list.map { inline ->
            if (inline is Inline.Spoiler) Inline.Spoiler(inlines(inline.inlines), index = next++) else inline
        }
        fun list(block: Block.ListBlock): Block.ListBlock =
            block.copy(items = block.items.map { item -> ListItem(inlines(item.inlines), item.sublists.map(::list)) })
        fun blocks(list: List<Block>): List<Block> = list.map { block ->
            when (block) {
                is Block.Paragraph -> Block.Paragraph(inlines(block.inlines))
                is Block.Quote -> block.copy(blocks = blocks(block.blocks))
                is Block.ListBlock -> list(block)
                is Block.Table -> Block.Table(block.rows.map { row -> row.map { it.copy(inlines = inlines(it.inlines)) } })
                is Block.QuoteAttribution, is Block.Code, is Block.Image, Block.Rule -> block
            }
        }
        return blocks(blocks)
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
                node.isList() -> out.block(listBlock(node, style))
                node.tagName() == "pre" -> out.block(Block.Code(node.wholeText().trimEnd()))
                node.tagName() == "img" -> out.block(Block.Image(absoluteUrl(node.attr("src")), node.attr("alt").ifEmpty { null }))
                node.tagName() == "hr" -> out.block(Block.Rule)
                node.tagName() == "table" -> out.block(table(node, style))
                node.tagName() == "blockquote" -> {
                    val kind = quoteKind(node)
                    val inner = Builder()
                    blocks(node, inner, if (kind != null) style.copy(quote = kind) else style)
                    inner.flush()
                    out.block(Block.Quote(kind, inner.blocks))
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

    /**
     * `>` quotes are `blockquote.wcrep1` at every depth; the site colours them by depth (the replied-to user's colour,
     * then the other quote colours). A plain `blockquote` is a `:` indent, null here.
     */
    private fun quoteKind(quote: Element): QuoteKind? {
        if (!quote.isReplyQuote()) return null
        val depth = quote.parents().count { it.tagName() == "blockquote" && it.isReplyQuote() }
        return QUOTE_DEPTHS[depth.coerceAtMost(QUOTE_DEPTHS.lastIndex)]
    }

    private fun Element.isReplyQuote() = hasClass("wcrep1") || hasClass("wcrep2")

    private val QUOTE_DEPTHS = listOf(QuoteKind.REPLIED_USER, QuoteKind.OTHER, QuoteKind.ALTERNATE)

    /** A table's rows; a cell's `text-align` style gives its alignment. */
    private fun table(table: Element, style: Style): Block.Table = Block.Table(
        table.select("tr").map { row ->
            row.children().filter { it.tagName() == "td" || it.tagName() == "th" }.map { cell ->
                val header = cell.tagName() == "th"
                val align = when (TEXT_ALIGN.find(cell.attr("style"))?.groupValues?.get(1)) {
                    "center" -> CellAlign.CENTER
                    "right" -> CellAlign.END
                    else -> CellAlign.START
                }
                TableCell(trim(collectInlines(cell.childNodes(), if (header) style.copy(bold = true) else style)), header, align)
            }
        },
    )

    private val TEXT_ALIGN = Regex("""text-align:\s*(\w+)""")

    private fun Element.isList() = tagName() == "ul" || tagName() == "ol"

    /**
     * A list and the lists nested in it. Whirlpool's editor puts a nested list beside the items rather than inside one
     * (`<ul><li>a</li><ul><li>b</li></ul></ul>`), so a list there belongs to the item before it.
     */
    private fun listBlock(list: Element, style: Style): Block.ListBlock {
        val items = mutableListOf<ListItem>()
        for (child in list.children()) {
            when {
                child.tagName() == "li" -> {
                    val (nested, text) = child.childNodes().partition { it is Element && it.isList() }
                    items += ListItem(trim(collectInlines(text, style)), nested.map { listBlock(it as Element, style) })
                }
                child.isList() -> {
                    val sub = listBlock(child, style)
                    val last = items.removeLastOrNull() ?: ListItem(emptyList())
                    items += last.copy(sublists = last.sublists + sub)
                }
            }
        }
        return Block.ListBlock(ordered = list.tagName() == "ol", items = items)
    }

    private fun collectInlines(nodes: List<Node>, style: Style): List<Inline> {
        val b = Builder()
        for (child in nodes) inline(child, b::add, style)
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
        "small" -> s.copy(small = true)
        "sub" -> s.copy(sub = true)
        "sup" -> s.copy(sup = true)
        "span" -> when {
            el.hasClass("wcrep1") -> s.copy(quote = QuoteKind.REPLIED_USER)
            el.hasClass("wcrep2") -> s.copy(quote = QuoteKind.OTHER)
            el.hasClass("wcgrey") -> s.copy(quote = QuoteKind.ALTERNATE)
            el.hasClass("wcserif") -> s.copy(serif = true)
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
