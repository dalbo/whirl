package au.howesthat.whirl.core.model

/** A post body as a list of blocks, ready for native rendering. */
data class PostBody(val blocks: List<Block>)

sealed interface Block {
    /** "samuria writes..." linking to the quoted post. */
    data class QuoteAttribution(val text: String, val postId: Long?) : Block
    data class Paragraph(val inlines: List<Inline>) : Block
    data class ListBlock(val ordered: Boolean, val items: List<ListItem>) : Block
    data class Code(val text: String) : Block
    data class Image(val url: String, val alt: String?) : Block
    /**
     * A `>` reply quote ([kind] by depth: the replied-to user's colour, then the other quote colours, as on the site)
     * or, with a null [kind], a `:` indent.
     */
    data class Quote(val kind: QuoteKind?, val blocks: List<Block>) : Block
    /** `-----`: a horizontal rule. */
    data object Rule : Block
    /** A Whirlcode table: rows of cells, a header row first when it has one. */
    data class Table(val rows: List<List<TableCell>>) : Block
}

data class TableCell(val inlines: List<Inline>, val header: Boolean = false, val align: CellAlign = CellAlign.START)

enum class CellAlign { START, CENTER, END }

/** A list item's text, and any lists nested under it. */
data class ListItem(val inlines: List<Inline>, val sublists: List<Block.ListBlock> = emptyList())

sealed interface Inline {
    data class Text(val text: String, val style: Style = Style()) : Inline
    data class Link(val href: String, val text: String, val style: Style = Style()) : Inline
    data object LineBreak : Inline
    /**
     * `span.wcspoil`: hidden until tapped. [index] numbers the spoilers of a body (the parser numbers them in body
     * order), so the view knows which one was revealed however it composes them.
     */
    data class Spoiler(val inlines: List<Inline>, val index: Int = 0) : Inline
}

data class Style(
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val code: Boolean = false,
    /** `[(small)]` */
    val small: Boolean = false,
    /** `[\2\]` subscript and `[^2^]` superscript. */
    val sub: Boolean = false,
    val sup: Boolean = false,
    /** `[~serif~]`: `span.wcserif`. */
    val serif: Boolean = false,
    val quote: QuoteKind? = null,
)

/** `span.wcrep1` quotes the replied-to user; `span.wcrep2` quotes someone else; `span.wcgrey` is the alternate quote. */
enum class QuoteKind { REPLIED_USER, OTHER, ALTERNATE }
