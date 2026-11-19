package au.howesthat.whirl.core.whirlcode

import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.PostBody

/**
 * A quote of a post for the post writer, in the form Dallin chose (`docs/plans/post-writer.md` §0, §3): a jump link
 * `@<postid> <name> writes...`, then each line the poster wrote as a `["…"]` forum quote. Inner quotes are dropped (an
 * attribution and the quote-only paragraphs after it, `>` quote blocks, quoted spans in a paragraph), so only the
 * poster's own words are quoted. Lists, tables and code keep their structure. `["` cannot cross a line, so each line
 * is quoted on its own.
 */
object Quote {
    fun of(postId: Long, author: String, body: PostBody): String {
        val own = PostBody(ownWords(body.blocks))
        val lines = own.blocks.map { block ->
            when (block) {
                is Block.Paragraph -> WhirlCode.fromBody(PostBody(listOf(block))).lines().joinToString("\n") { forumQuote(it) }
                else -> WhirlCode.fromBody(PostBody(listOf(block)))
            }
        }.filter { it.isNotBlank() }
        val attribution = "@$postId ${author.replace('\n', ' ').trim()} writes..."
        return (listOf(attribution) + lines).joinToString("\n\n")
    }

    /** `["line"]`; a line that is empty (or only an escaped structure code) stays as it is. */
    private fun forumQuote(line: String) = if (line.isBlank()) line else "[\"$line\"]"

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
                    if (!onlyQuote) out += Block.Paragraph(own)
                }
                else -> out += block
            }
        }
        return out
    }

    private fun isQuoted(inline: Inline) = when (inline) {
        is Inline.Text -> inline.style.quote != null
        is Inline.Link -> inline.style.quote != null
        else -> false
    }
}
