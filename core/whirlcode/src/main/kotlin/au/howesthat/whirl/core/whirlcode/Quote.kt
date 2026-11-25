package au.howesthat.whirl.core.whirlcode

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.PostBody

/**
 * A quote of a post for the post writer, in the form Dallin chose (`docs/plans/post-writer.md` §0, §3): a jump link
 * `@<postid> <name> writes...`, then each line the poster wrote as a `["…"]` forum quote. Inner quotes are dropped (an
 * attribution and the quote-only paragraphs after it, `>` quote blocks, quoted spans in a paragraph), so only the
 * poster's own words are quoted. Lists, tables and indents keep their structure with their text quoted (`* ["a point"]`,
 * `| ["a"] | ["b"]`); code stays as code, as a code block cannot hold codes. `["` cannot cross a line, so each line
 * is quoted on its own.
 */
object Quote {
    fun of(postId: Long, author: String, body: PostBody): String {
        val own = PostBody(ownWords(body.blocks))
        val lines = own.blocks.map { block ->
            when (block) {
                is Block.Paragraph -> WhirlCode.fromBody(PostBody(listOf(block))).lines().joinToString("\n") { forumQuote(it) }
                is Block.Code, Block.Rule -> WhirlCode.fromBody(PostBody(listOf(block)))
                else -> quoteStructure(WhirlCode.fromBody(PostBody(listOf(block))))
            }
        }.filter { it.isNotBlank() }
        val attribution = "@$postId ${author.replace('\n', ' ').trim()} writes..."
        return (listOf(attribution) + lines).joinToString("\n\n")
    }

    /** `["line"]`, trimmed (old posts carry spaces around their line breaks); an empty line stays empty. */
    private fun forumQuote(line: String) = line.trim().let { if (it.isEmpty()) it else "[\"$it\"]" }

    /**
     * The text of each line of a list, table or indent quoted, its structural codes left in front: `:* item` →
     * `:* ["item"]`, `| a | b` → `| ["a"] | ["b"]`. A code block inside an indent (`$$` lines) is left alone.
     */
    private fun quoteStructure(code: String): String {
        var inCode = false
        return code.lines().joinToString("\n") { line ->
            if (line.trim() == "$$") inCode = !inCode
            when {
                inCode || line.trim() == "$$" || line.startsWith("$ ") || RULE.matches(line) -> line
                line.startsWith("|") -> "| " + line.substring(1).split('|').joinToString(" | ") { cell ->
                    cell.trim().let { if (ALIGNMENT.matches(it)) it else forumQuote(it) }
                }
                else -> STRUCTURE.matchEntire(line)?.let { it.groupValues[1] + forumQuote(it.groupValues[2]) } ?: forumQuote(line)
            }
        }
    }

    /** The blocks the poster wrote: inner quotes and their attributions left out. */
    private fun ownWords(blocks: List<Block>): List<Block> {
        val out = mutableListOf<Block>()
        for (block in blocks) {
            when (block) {
                is Block.QuoteAttribution -> Unit
                is Block.Quote -> if (block.kind == null) out += Block.Quote(null, ownWords(block.blocks)) // an indent is the poster's
                is Block.Paragraph -> {
                    val own = block.inlines.filterNot(::isQuoted).dropWhile { it == Inline.LineBreak }.dropLastWhile { it == Inline.LineBreak }
                    val onlyQuote = own.all { it == Inline.LineBreak || (it is Inline.Text && it.text.isBlank()) }
                    // A paragraph that is all quote belongs to the attribution before it.
                    if (!onlyQuote) out += Block.Paragraph(trimLines(own))
                }
                else -> out += block
            }
        }
        return out
    }

    /**
     * Spaces at the start and end of each line dropped before the text is written as WhirlCode, so a quoted line reads
     * `["- the colour"]`, not `[" &#45; the colour "]` (old posts carry spaces around their line breaks).
     */
    private fun trimLines(inlines: List<Inline>): List<Inline> {
        val out = inlines.toMutableList()
        for (i in out.indices) {
            val text = out[i] as? Inline.Text ?: continue
            val startsLine = i == 0 || out[i - 1] == Inline.LineBreak
            val endsLine = i == out.lastIndex || out[i + 1] == Inline.LineBreak
            var t = text.text
            if (startsLine) t = t.trimStart()
            if (endsLine) t = t.trimEnd()
            out[i] = text.copy(text = t)
        }
        return out.filterNot { it is Inline.Text && it.text.isEmpty() }
    }

    /** A table's alignment cell (`---`, `:---:`, `---:`). */
    private val ALIGNMENT = Regex(":?-{3,}:?")
    /** An indent's or list's codes: `: `, `:: `, `* `, `:# `. */
    private val STRUCTURE = Regex("([:>]*[*#]+ |[:>]+ )(.*)")
    private val RULE = Regex("-{5,}")

    private fun isQuoted(inline: Inline) = when (inline) {
        is Inline.Text -> inline.style.quote != null
        is Inline.Link -> inline.style.quote != null
        else -> false
    }
}
