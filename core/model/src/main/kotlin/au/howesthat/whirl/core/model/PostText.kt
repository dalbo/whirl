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
