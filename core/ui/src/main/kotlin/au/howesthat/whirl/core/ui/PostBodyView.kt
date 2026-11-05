package au.howesthat.whirl.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.CellAlign
import au.howesthat.whirl.core.model.TableCell
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.QuoteKind
import au.howesthat.whirl.core.model.Style
import au.howesthat.whirl.core.model.TextQuery

private val BULLETS = listOf("•", "◦", "▪")

private data class BodyColors(val repliedUser: Color, val other: Color, val alternate: Color, val link: Color, val spoiler: Color, val codeBackground: Color) {
    fun quote(kind: QuoteKind): Color = when (kind) {
        QuoteKind.REPLIED_USER -> repliedUser
        QuoteKind.OTHER -> other
        QuoteKind.ALTERNATE -> alternate
    }
}

@Composable
private fun bodyColors(): BodyColors {
    val scheme = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    return BodyColors(
        repliedUser = style.quoteRepliedUser,
        other = style.quoteOther,
        alternate = scheme.onSurfaceVariant,
        link = scheme.primary,
        spoiler = scheme.onSurfaceVariant,
        codeBackground = scheme.surfaceContainerHighest,
    )
}

/**
 * Renders a post body natively. [onLink] receives every link (the caller routes Whirlpool links in the app and the
 * rest to the browser), [onQuote] the reply id of a quote attribution. Spoilers stay hidden until tapped. With
 * [highlight], find on page marks its words (never inside a spoiler).
 */
@Composable
fun PostBodyView(
    body: PostBody,
    onLink: (String) -> Unit,
    onQuote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    highlight: FindHighlight? = null,
) {
    val colors = bodyColors()
    val marks = highlight?.let { it.query to it.style() }
    // The revealed spoilers, by their index in the body.
    val revealed = remember(body) { mutableStateOf(emptySet<Int>()) }
    val reveal: (Int) -> Unit = remember(revealed) { { revealed.value += it } }
    val boxedQuotes = LocalAppStyle.current.layout.quotes == Quotes.BOXED
    // A quote's blocks are drawn inside it; boxed quotes box a whole quote once, not each paragraph in it.
    @Composable
    fun Blocks(blocks: List<Block>, inQuote: Boolean, modifier: Modifier = Modifier) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is Block.QuoteAttribution -> Text(
                        highlighted(block.text, marks),
                        modifier = block.postId?.let { id -> Modifier.clickable(role = Role.Button) { onQuote(id) } } ?: Modifier,
                        style = textStyle,
                        fontWeight = FontWeight.Bold,
                        color = colors.repliedUser,
                    )
                    is Block.Paragraph -> {
                        val text = rememberAnnotated(block.inlines, colors, onLink, revealed.value, reveal, marks)
                        val quote = if (boxedQuotes && !inQuote) wholeQuote(block.inlines) else null
                        if (quote == null) {
                            Text(text, style = textStyle)
                        } else {
                            QuoteBox(colors.quote(quote)) { Text(text, style = textStyle) }
                        }
                    }
                    is Block.Quote -> when (val kind = block.kind) {
                        // A `:` indent.
                        null -> Blocks(block.blocks, inQuote, Modifier.padding(start = 24.dp))
                        else -> if (boxedQuotes && !inQuote) {
                            QuoteBox(colors.quote(kind)) { Blocks(block.blocks, inQuote = true) }
                        } else {
                            Row(Modifier.height(IntrinsicSize.Min)) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(colors.quote(kind).copy(alpha = 0.5f)))
                                Blocks(block.blocks, inQuote = true, Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                    is Block.ListBlock -> {
                        // Nested lists sit under their item, indented past its bullet, with the next bullet shape. A
                        // list with no item above it (`##` straight after a `*` list) is only indented.
                        @Composable
                        fun ListView(list: Block.ListBlock, depth: Int) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                list.items.forEachIndexed { i, item ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val marker = if (list.ordered) "${i + 1}." else BULLETS[depth % BULLETS.size]
                                        if (item.inlines.isEmpty()) Box(Modifier.width(16.dp)) else Text(marker, style = textStyle)
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            if (item.inlines.isNotEmpty()) Text(rememberAnnotated(item.inlines, colors, onLink, revealed.value, reveal, marks), style = textStyle)
                                            item.sublists.forEach { ListView(it, depth + 1) }
                                        }
                                    }
                                }
                            }
                        }
                        ListView(block, 0)
                    }
                    is Block.Code -> Surface(color = colors.codeBackground, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            highlighted(block.text, marks),
                            Modifier.horizontalScroll(rememberScrollState()).padding(8.dp),
                            style = textStyle.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    // Images are not loaded yet (no fixture with one); a tappable placeholder opens it.
                    is Block.Image -> Surface(
                        color = colors.codeBackground,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.widthIn(min = 120.dp).clickable(role = Role.Button) { onLink(block.url) },
                    ) {
                        Text(
                            block.alt?.let { stringResource(R.string.image_named, it) } ?: stringResource(R.string.image),
                            Modifier.padding(12.dp),
                            style = textStyle,
                            color = colors.link,
                        )
                    }
                    Block.Rule -> HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    is Block.Table -> TableView(block) { cell ->
                        Text(
                            rememberAnnotated(cell.inlines, colors, onLink, revealed.value, reveal, marks),
                            style = textStyle,
                            textAlign = when (cell.align) {
                                CellAlign.START -> TextAlign.Start
                                CellAlign.CENTER -> TextAlign.Center
                                CellAlign.END -> TextAlign.End
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
    Blocks(body.blocks, inQuote = false, modifier)
}

/** A table as a grid of equal columns (enough for the short tables in posts), the header row shaded. */
@Composable
private fun TableView(table: Block.Table, cell: @Composable (TableCell) -> Unit) {
    val columns = table.rows.maxOfOrNull { it.size } ?: return
    val line = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth().border(1.dp, line)) {
        table.rows.forEach { row ->
            val header = row.isNotEmpty() && row.all { it.header }
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                    .background(if (header) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Unspecified),
            ) {
                for (i in 0 until columns) {
                    Box(Modifier.weight(1f).fillMaxHeight().border(0.5.dp, line).padding(horizontal = 8.dp, vertical = 6.dp)) {
                        row.getOrNull(i)?.let { cell(it) }
                    }
                }
            }
        }
    }
}

/** A quoted paragraph in a recessed box with a rule in the quote's colour ([Quotes.BOXED]). */
@Composable
private fun QuoteBox(rule: Color, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(rule))
            Box(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) { content() }
        }
    }
}

/**
 * The quote kind of a paragraph that is all quote (ignoring blank text), the replied-to user's if any part is theirs;
 * null for a paragraph with any unquoted text, which keeps its inline quote colours.
 */
internal fun wholeQuote(inlines: List<Inline>): QuoteKind? {
    val kinds = mutableListOf<QuoteKind?>()
    fun collect(list: List<Inline>) {
        list.forEach { inline ->
            when (inline) {
                is Inline.Text -> if (inline.text.isNotBlank()) kinds += inline.style.quote
                is Inline.Link -> if (inline.text.isNotBlank()) kinds += inline.style.quote
                is Inline.Spoiler -> collect(inline.inlines)
                Inline.LineBreak -> Unit
            }
        }
    }
    collect(inlines)
    return when {
        kinds.isEmpty() || null in kinds -> null
        QuoteKind.REPLIED_USER in kinds -> QuoteKind.REPLIED_USER
        QuoteKind.OTHER in kinds -> QuoteKind.OTHER
        else -> QuoteKind.ALTERNATE
    }
}

/** Find on page in a post: [query]'s words marked, more strongly in the [current] match. */
class FindHighlight(val query: TextQuery, val current: Boolean = false)

@Composable
fun FindHighlight.style(): SpanStyle = MaterialTheme.colorScheme.let {
    if (current) SpanStyle(background = it.tertiary, color = it.onTertiary) else SpanStyle(background = it.tertiaryContainer, color = it.onTertiaryContainer)
}

/** [text] with the find marks; for text drawn outside [annotate] (code, quote attributions, the poster's name). */
fun highlighted(text: String, marks: Pair<TextQuery, SpanStyle>?): AnnotatedString = buildAnnotatedString {
    append(text)
    marks?.let { (query, style) -> query.ranges(text).forEach { addStyle(style, it.first, it.last + 1) } }
}

/** [annotate], rebuilt only when the inlines, the revealed spoilers, the find marks or the colours change. */
@Composable
private fun rememberAnnotated(
    inlines: List<Inline>,
    colors: BodyColors,
    onLink: (String) -> Unit,
    revealed: Set<Int>,
    reveal: (Int) -> Unit,
    marks: Pair<TextQuery, SpanStyle>?,
): AnnotatedString {
    val currentOnLink by rememberUpdatedState(onLink)
    return remember(inlines, colors, revealed, reveal, marks) {
        annotate(inlines, colors, { currentOnLink(it) }, revealed, reveal, marks)
    }
}

private fun annotate(
    inlines: List<Inline>,
    colors: BodyColors,
    onLink: (String) -> Unit,
    revealed: Set<Int>,
    reveal: (Int) -> Unit,
    marks: Pair<TextQuery, SpanStyle>?,
): AnnotatedString = buildAnnotatedString {
    // Spoiler text, revealed or not, is never marked: a mark over hidden text would show where the word is.
    val spoilers = mutableListOf<IntRange>()
    fun append(list: List<Inline>, hidden: Boolean) {
        for (inline in list) {
            when (inline) {
                is Inline.Text -> withStyle(span(inline.style, colors, hidden)) { append(inline.text) }
                is Inline.LineBreak -> append('\n')
                is Inline.Link -> if (hidden) {
                    withStyle(span(inline.style, colors, hidden = true)) { append(inline.text) }
                } else {
                    val link = LinkAnnotation.Clickable(
                        tag = inline.href,
                        styles = TextLinkStyles(SpanStyle(color = colors.link, textDecoration = TextDecoration.Underline)),
                    ) { onLink(inline.href) }
                    withLink(link) { withStyle(span(inline.style, colors, hidden = false)) { append(inline.text) } }
                }
                is Inline.Spoiler -> {
                    val from = length
                    val index = inline.index
                    if (index in revealed) {
                        append(inline.inlines, hidden)
                    } else {
                        withLink(LinkAnnotation.Clickable("spoiler-$index") { reveal(index) }) {
                            withStyle(SpanStyle(background = colors.spoiler, color = Color.Transparent)) { append(inline.inlines, hidden = true) }
                        }
                    }
                    spoilers += from until length
                }
            }
        }
    }
    append(inlines, hidden = false)
    // Over the finished text, so a word split across styles is found, and last, so the mark wins over link and code
    // styles.
    marks?.let { (query, style) ->
        query.ranges(toAnnotatedString().text).forEach { r ->
            if (spoilers.none { it.first <= r.last && r.first <= it.last }) addStyle(style, r.first, r.last + 1)
        }
    }
}

private fun span(style: Style, colors: BodyColors, hidden: Boolean): SpanStyle {
    val decorations = listOfNotNull(
        TextDecoration.Underline.takeIf { style.underline },
        TextDecoration.LineThrough.takeIf { style.strike },
    )
    return SpanStyle(
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        fontFamily = when {
            style.code -> FontFamily.Monospace
            style.serif -> FontFamily.Serif
            else -> null
        },
        fontSize = when {
            style.sub || style.sup -> 0.75.em
            style.small -> 0.85.em
            else -> TextUnit.Unspecified
        },
        baselineShift = when {
            style.sup -> BaselineShift.Superscript
            style.sub -> BaselineShift.Subscript
            else -> null
        },
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        color = when {
            hidden -> Color.Transparent
            else -> style.quote?.let(colors::quote) ?: Color.Unspecified
        },
        background = if (style.code && !hidden) colors.codeBackground else Color.Unspecified,
    )
}
