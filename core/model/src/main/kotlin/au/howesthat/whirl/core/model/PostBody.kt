package au.howesthat.whirl.core.model

/** A post body as a list of blocks, ready for native rendering. */
data class PostBody(val blocks: List<Block>)

sealed interface Block {
    /** "samuria writes..." linking to the quoted post. */
    data class QuoteAttribution(val text: String, val postId: Long?) : Block
    data class Paragraph(val inlines: List<Inline>) : Block
    data class ListBlock(val ordered: Boolean, val items: List<List<Inline>>) : Block
    data class Code(val text: String) : Block
    data class Image(val url: String, val alt: String?) : Block
}

sealed interface Inline {
    data class Text(val text: String, val style: Style = Style()) : Inline
    data class Link(val href: String, val text: String, val style: Style = Style()) : Inline
    data object LineBreak : Inline
    /** `span.wcspoil`: hidden until tapped. */
    data class Spoiler(val inlines: List<Inline>) : Inline
}

data class Style(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    val quote: QuoteKind? = null,
)

/** `span.wcrep1` quotes the replied-to user; `span.wcrep2` quotes someone else. */
enum class QuoteKind { REPLIED_USER, OTHER }
