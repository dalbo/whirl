package au.howesthat.whirl.core.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import au.howesthat.whirl.core.model.Block
import au.howesthat.whirl.core.model.Inline
import au.howesthat.whirl.core.model.PostBody
import au.howesthat.whirl.core.model.QuoteKind
import au.howesthat.whirl.core.model.Style

private class BodyColors(val repliedUser: Color, val other: Color, val link: Color, val spoiler: Color, val codeBackground: Color)

@Composable
private fun bodyColors(): BodyColors {
    val scheme = MaterialTheme.colorScheme
    val style = LocalAppStyle.current
    return BodyColors(
        repliedUser = style.quoteRepliedUser,
        other = style.quoteOther,
        link = scheme.primary,
        spoiler = scheme.onSurfaceVariant,
        codeBackground = scheme.surfaceContainerHighest,
    )
}

/**
 * Renders a post body natively. [onLink] receives every link (the caller routes Whirlpool links in the app and the
 * rest to the browser), [onQuote] the reply id of a quote attribution. Spoilers stay hidden until tapped.
 */
@Composable
fun PostBodyView(
    body: PostBody,
    onLink: (String) -> Unit,
    onQuote: (Long) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val colors = bodyColors()
    // Spoilers are numbered in body order; this holds the revealed ones.
    val revealed = remember(body) { mutableStateListOf<Int>() }
    val reveal: (Int) -> Unit = { if (it !in revealed) revealed += it }
    val counter = SpoilerCounter()
    val boxedQuotes = LocalAppStyle.current.layout.quotes == Quotes.BOXED
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        body.blocks.forEach { block ->
            when (block) {
                is Block.QuoteAttribution -> Text(
                    block.text,
                    modifier = block.postId?.let { id -> Modifier.clickable(role = Role.Button) { onQuote(id) } } ?: Modifier,
                    style = textStyle,
                    fontWeight = FontWeight.Bold,
                    color = colors.repliedUser,
                )
                is Block.Paragraph -> {
                    val text = annotate(block.inlines, colors, onLink, revealed, reveal, counter)
                    val quote = if (boxedQuotes) wholeQuote(block.inlines) else null
                    if (quote == null) {
                        Text(text, style = textStyle)
                    } else {
                        QuoteBox(if (quote == QuoteKind.REPLIED_USER) colors.repliedUser else colors.other) { Text(text, style = textStyle) }
                    }
                }
                is Block.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (block.ordered) "${i + 1}." else "•", style = textStyle)
                            Text(annotate(item, colors, onLink, revealed, reveal, counter), style = textStyle)
                        }
                    }
                }
                is Block.Code -> Surface(color = colors.codeBackground, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        block.text,
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
        else -> QuoteKind.OTHER
    }
}

/** Numbers spoilers in the order they are rendered, so reveal state survives recomposition. */
private class SpoilerCounter {
    var next = 0
}

private fun annotate(
    inlines: List<Inline>,
    colors: BodyColors,
    onLink: (String) -> Unit,
    revealed: List<Int>,
    reveal: (Int) -> Unit,
    counter: SpoilerCounter,
): AnnotatedString = buildAnnotatedString {
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
                    val index = counter.next++
                    if (index in revealed) {
                        append(inline.inlines, hidden)
                    } else {
                        withLink(LinkAnnotation.Clickable("spoiler-$index") { reveal(index) }) {
                            withStyle(SpanStyle(background = colors.spoiler, color = Color.Transparent)) { append(inline.inlines, hidden = true) }
                        }
                    }
                }
            }
        }
    }
    append(inlines, hidden = false)
}

private fun span(style: Style, colors: BodyColors, hidden: Boolean): SpanStyle {
    val decorations = listOfNotNull(
        TextDecoration.Underline.takeIf { style.underline },
        TextDecoration.LineThrough.takeIf { style.strike },
    )
    return SpanStyle(
        fontWeight = if (style.bold) FontWeight.Bold else null,
        fontStyle = if (style.italic) FontStyle.Italic else null,
        fontFamily = if (style.code) FontFamily.Monospace else null,
        textDecoration = if (decorations.isEmpty()) null else TextDecoration.combine(decorations),
        color = when {
            hidden -> Color.Transparent
            style.quote == QuoteKind.REPLIED_USER -> colors.repliedUser
            style.quote == QuoteKind.OTHER -> colors.other
            else -> Color.Unspecified
        },
        background = if (style.code && !hidden) colors.codeBackground else Color.Unspecified,
    )
}
