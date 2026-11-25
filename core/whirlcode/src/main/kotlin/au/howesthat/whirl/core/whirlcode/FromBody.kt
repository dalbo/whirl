package au.howesthat.whirl.core.whirlcode

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.CellAlign
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.ListItem
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.QuoteKind
import au.howesthat.whirl.core.model.Style

/**
 * A parsed post back to WhirlCode, so a quote keeps the post's formatting and links (`docs/plans/post-writer.md` §3).
 * Aim: `PostBodyParser.parse(WhirlCode.toHtml(fromBody(b))) == b`. What WhirlCode cannot write (images, underline, a
 * line break inside a list item, code inside a quote) comes out as near as it can; `FromBodyTest` lists the posts
 * that do not survive and why.
 */
internal object FromBody {

    fun write(body: PostBody): String = blocks(body.blocks, Context()).trimEnd('\n')

    /** Where a block sits: the structural codes before each of its lines, and styles it inherits (not its own). */
    private data class Context(val prefix: String = "", val inherited: Style = Style())

    /** Blocks one after another, a blank line between: a blank line ends a paragraph and closes no container. */
    private fun blocks(blocks: List<Block>, ctx: Context): String =
        blocks.mapNotNull { block(it, ctx) }.filter { it.isNotEmpty() }.joinToString("\n\n")

    private fun block(block: Block, ctx: Context): String? = when (block) {
        is Block.Paragraph -> lines(block.inlines, ctx).joinToString("\n") { ctx.prefix + it }
        is Block.QuoteAttribution -> block.postId?.let { "@$it ${plain(block.text)}" } ?: (ctx.prefix + escapeLine(plain(block.text)))
        is Block.Quote -> {
            val code = if (block.kind == null) ":" else ">"
            val inherited = block.kind?.let { ctx.inherited.copy(quote = it) } ?: ctx.inherited
            blocks(block.blocks, Context(ctx.prefix.trimEnd() + code + " ", inherited))
        }
        is Block.ListBlock -> list(block, ctx, ctx.prefix.trimEnd())
        is Block.Code -> if ('\n' in block.text) "$$\n${block.text}\n$$" else "$ ${block.text}"
        is Block.Rule -> "-----"
        is Block.Table -> table(block)
        is Block.Image -> null // WhirlCode has no image.
    }

    private fun list(list: Block.ListBlock, ctx: Context, codes: String): String {
        val marker = if (list.ordered) '#' else '*'
        val here = codes + marker
        return list.items.joinToString("\n") { item -> item(item, ctx, here) }
    }

    private fun item(item: ListItem, ctx: Context, codes: String): String = buildList {
        if (item.inlines.isNotEmpty()) {
            // A list item is one line: a break inside one cannot be written, so it becomes a space.
            add("$codes " + lines(item.inlines, ctx).joinToString(" "))
        }
        item.sublists.forEach { add(list(it, ctx, codes)) }
    }.joinToString("\n")

    private fun table(table: Block.Table): String {
        val header = table.rows.firstOrNull()?.all { it.header } == true
        val width = table.rows.maxOfOrNull { it.size } ?: 0
        return buildList {
            table.rows.forEachIndexed { r, row ->
                add("| " + row.joinToString(" | ") { cell ->
                    val own = if (cell.header) Context(inherited = Style(bold = true)) else Context()
                    lines(cell.inlines, own).joinToString(" ").replace("|", "&#124;")
                })
                if (r == 0 && header) {
                    add("| " + (0 until width).joinToString(" | ") { c ->
                        when (table.rows.getOrNull(1)?.getOrNull(c)?.align ?: row.getOrNull(c)?.align) {
                            CellAlign.CENTER -> ":---:"
                            CellAlign.END -> "---:"
                            else -> "---"
                        }
                    })
                }
            }
        }.joinToString("\n")
    }

    /** Inlines split at line breaks into lines of WhirlCode. */
    private fun lines(inlines: List<Inline>, ctx: Context): List<String> {
        val lines = mutableListOf(StringBuilder())
        inlines.forEachIndexed { i, inline ->
            when (inline) {
                Inline.LineBreak -> lines += StringBuilder()
                is Inline.Text -> lines.last().append(styled(text(inline.text), inline.style, ctx.inherited))
                is Inline.Link -> lines.last().append(styled(link(inline, inlines.getOrNull(i + 1)), inline.style, ctx.inherited))
                is Inline.Spoiler -> lines.last().append("[_").append(lines(inline.inlines, ctx).joinToString(" ")).append("_]")
            }
        }
        return lines.map { escapeLine(it.toString()) }
    }

    /** Wraps [code] in the codes for what [style] adds to [inherited]. */
    private fun styled(code: String, style: Style, inherited: Style): String {
        var out = code
        if (style.sup && !inherited.sup) out = "[^$out^]"
        if (style.sub && !inherited.sub) out = "[\\$out\\]"
        if (style.serif && !inherited.serif) out = "[~$out~]"
        if (style.code && !inherited.code) out = "[#$out#]"
        if (style.small && !inherited.small) out = "[($out)]"
        if (style.strike && !inherited.strike) out = "[-$out-]"
        if (style.italic && !inherited.italic) out = "[/$out/]"
        if (style.bold && !inherited.bold) out = "[*$out*]"
        if (style.quote != null && style.quote != inherited.quote) {
            out = when (style.quote) {
                QuoteKind.REPLIED_USER -> "[\"$out\"]"
                QuoteKind.OTHER -> "['$out']"
                else -> "[`$out`]"
            }
        }
        return out
    }

    /** A link as the site autolinks it when it would come back the same, else `<a href>`. */
    private fun link(link: Inline.Link, next: Inline?): String {
        val href = link.href
        val text = link.text
        // An autolink would run on into text that follows with no space.
        val nextText = (next as? Inline.Text)?.text
        val endsClean = nextText == null || nextText.first().isWhitespace() || nextText.first() in ".,;:!?)'\""
        fun bare(address: String) = if (endsClean && Links.find(address).singleOrNull()?.value == address) address else null
        val written = when {
            href.startsWith("mailto:") && href.removePrefix("mailto:") == text -> bare(text)
            href.startsWith(GOOGLE) && href.removePrefix(GOOGLE) == text && "?]" !in text -> "[?$text?]"
            href.startsWith(WIKI) -> {
                val term = href.removePrefix(WIKI)
                if ("]]" in term || '|' in term || "]]" in text) null else if (term == text) "[[$term]]" else "[[$term|${plain(text)}]]"
            }
            href.startsWith(FORUMS) && text == href.removePrefix(FORUMS) -> bare(href)
            href.startsWith(WHRL) && text == href.removePrefix("https://") -> bare(href)
            href == text -> bare(href)
            else -> null
        }
        return written ?: "<a href=\"${href.replace("\"", "%22")}\">${text(text)}</a>"
    }

    private const val FORUMS = "https://forums.whirlpool.net.au"
    private const val WHRL = "https://whrl.pl/"
    private const val GOOGLE = "http://www.google.com.au/search?q="
    private const val WIKI = "https://forums.whirlpool.net.au/wiki/"

    /**
     * Text as WhirlCode that reads back as the same text: anything the converter would act on is written as an entity
     * ("characters written as entities never become WhirlCode").
     */
    private fun text(raw: String): String {
        val s = raw.replace('\n', ' ')
        // Only what the converter would turn into a link is broken up: `d@llin` and `see: this` stay as typed.
        val links = Links.find(s).map { it.range }
        fun inLink(i: Int) = links.any { i in it }
        val out = StringBuilder()
        for ((i, c) in s.withIndex()) {
            val next = s.getOrNull(i + 1)
            val prev = s.getOrNull(i - 1)
            out.append(
                when {
                    c == '&' -> "&amp;"
                    c == '<' -> "&lt;"
                    c == '>' -> "&gt;"
                    // An opener ([*, [[, [.*) or a closer (*]) would start or end a code.
                    // A bracket at either end of the run could meet a code's bracket in the next run ("[" then ["q"]
                    // reads as [[…]], a wiki link): the run cannot see its neighbours, so it always escapes those.
                    c == '[' && (next == null || next == '[' || next == '.' || next in MARKERS) -> "&#91;"
                    c == ']' && (prev == null || prev in MARKERS || prev == ']' || prev == '.') -> "&#93;"
                    // A hyphen with spaces both sides would become a dash (one at the start of the text has none before).
                    c == '-' && prev != null && prev.isWhitespace() && (next == null || next.isWhitespace() || next == '-') -> "&#45;"
                    // An address the converter would link: one entity inside it is enough.
                    inLink(i) && c == '@' -> "&#064;"
                    inLink(i) && c == ':' && s.startsWith("//", i + 1) -> "&#58;"
                    else -> c.toString()
                },
            )
        }
        return out.toString()
    }

    /** Text with no codes at all (a jump link's label, a wiki label). */
    private fun plain(raw: String) = text(raw)

    /** A line that would read as structure (`* `, `> `, `$ `, `|`, `@1 `, `-----`) starts with an entity instead. */
    private fun escapeLine(line: String): String {
        if (line.isEmpty()) return line
        val first = line[0]
        val structural = first in ":*#>|" || line.startsWith("$ ") || line.trim() == "$$" ||
            Regex("""@\d+ .*""").matches(line) || Regex("-{5,}").matches(line.trim())
        return if (structural) "&#${first.code};" + line.substring(1) else line
    }

    private val MARKERS = setOf('*', '/', '(', ')', '-', '#', '~', '\\', '^', '"', '\'', '`', '_', '?')
}
