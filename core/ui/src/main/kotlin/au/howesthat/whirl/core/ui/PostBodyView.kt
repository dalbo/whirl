package au.howesthat.whirl.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.luminance
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

/** Whirldroid 2.14.12's quote colours (PLAN.md §1.5). */
private object QuoteColors {
    val repliedUserLight = Color(0xFF4455AA)
    val repliedUserDark = Color(0xFF7885C4)
    val other = Color(0xFF9F6E19)
}

private class BodyColors(val repliedUser: Color, val other: Color, val link: Color, val spoiler: Color, val codeBackground: Color)

@Composable
private fun bodyColors(): BodyColors {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return BodyColors(
        repliedUser = if (dark) QuoteColors.repliedUserDark else QuoteColors.repliedUserLight,
        other = QuoteColors.other,
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
                is Block.Paragraph -> Text(annotate(block.inlines, colors, onLink, revealed, reveal, counter), style = textStyle)
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
