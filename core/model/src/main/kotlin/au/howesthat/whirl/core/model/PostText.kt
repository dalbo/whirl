package au.howesthat.whirl.core.model

/**
 * Find on page: the text of a post that can be searched, which is exactly the text the post view can highlight:
 * paragraphs, quote attributions, list items, table cells and code. Spoilers are left out, revealed or not, since a
 * highlight would show where a hidden word is; so are image placeholders.
 */
fun PostBody.searchText(): String = buildString {
    fun inlines(list: List<Inline>) {
        for (inline in list) {
            when (inline) {
                is Inline.Text -> append(inline.text)
                is Inline.Link -> append(inline.text)
                Inline.LineBreak -> append('\n')
                is Inline.Spoiler -> append(' ')
            }
        }
        append('\n')
    }
    fun list(block: Block.ListBlock) {
        block.items.forEach { item ->
            inlines(item.inlines)
            item.sublists.forEach(::list)
        }
    }
    fun blocks(list: List<Block>) {
        for (block in list) {
            when (block) {
                is Block.QuoteAttribution -> append(block.text).append('\n')
                is Block.Paragraph -> inlines(block.inlines)
                is Block.ListBlock -> list(block)
                is Block.Code -> append(block.text).append('\n')
                is Block.Quote -> blocks(block.blocks)
                is Block.Table -> block.rows.flatten().forEach { inlines(it.inlines) }
                is Block.Image, Block.Rule -> Unit
            }
        }
    }
    blocks(blocks)
}

/**
 * A post as plain text to copy or select: blocks a blank line apart, quotes behind `> `, list items behind `•` or their
 * number (indented a level at a time), a table's cells a tab apart, code as it is, spoilers shown. Images and rules
 * are left out.
 */
fun PostBody.plainText(): String {
    fun inlines(list: List<Inline>): String = buildString {
        for (inline in list) {
            when (inline) {
                is Inline.Text -> append(inline.text)
                is Inline.Link -> append(inline.text)
                Inline.LineBreak -> append('\n')
                is Inline.Spoiler -> append(inlines(inline.inlines))
            }
        }
    }.trimEnd()
    fun list(block: Block.ListBlock, depth: Int): List<String> = block.items.flatMapIndexed { i, item ->
        val marker = if (block.ordered) "${i + 1}." else "•"
        val line = "  ".repeat(depth) + marker + " " + inlines(item.inlines).replace("\n", " ")
        listOf(line) + item.sublists.flatMap { list(it, depth + 1) }
    }
    fun blocks(list: List<Block>): List<String> = list.mapNotNull { block ->
        when (block) {
            is Block.QuoteAttribution -> block.text
            is Block.Paragraph -> inlines(block.inlines)
            is Block.ListBlock -> list(block, 0).joinToString("\n")
            is Block.Code -> block.text
            is Block.Quote -> blocks(block.blocks).joinToString("\n\n").lines().joinToString("\n") { if (it.isEmpty()) ">" else "> $it" }
            is Block.Table -> block.rows.joinToString("\n") { row -> row.joinToString("\t") { inlines(it.inlines).replace("\n", " ") } }
            is Block.Image, Block.Rule -> null
        }?.takeIf { it.isNotBlank() }
    }
    return blocks(blocks).joinToString("\n\n")
}
