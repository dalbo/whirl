package au.howesthat.whirl.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Where the list is: a thin thumb at the end edge that shows while scrolling and fades once it stops, like
 * Whirldroid's. Only an indicator, with no touch handling, so it never takes a page swipe, a tap or the Back gesture.
 * TalkBack scrolls lists itself, so it is hidden from accessibility.
 */
@Composable
fun ScrollThumb(state: LazyListState, modifier: Modifier = Modifier) {
    val progress by remember(state) { derivedStateOf { state.layoutInfo.scrollProgress() } }
    val scrolling = state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (scrolling) 1f else 0f,
        animationSpec = if (scrolling) tween(150) else tween(durationMillis = 300, delayMillis = 1200),
        label = "scroll thumb",
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Spacer(
        modifier.fillMaxHeight().width(ThumbWidth + ThumbMargin).padding(vertical = ThumbMargin).clearAndSetSemantics {}.drawBehind {
            val p = progress ?: return@drawBehind
            if (alpha == 0f) return@drawBehind
            val length = ThumbLength.toPx().coerceAtMost(size.height)
            val width = ThumbWidth.toPx()
            // The margin goes on the screen edge's side.
            val x = if (layoutDirection == LayoutDirection.Rtl) ThumbMargin.toPx() else 0f
            drawRoundRect(
                color = color.copy(alpha = color.alpha * 0.6f * alpha),
                topLeft = Offset(x, p * (size.height - length)),
                size = Size(width, length),
                cornerRadius = CornerRadius(width / 2),
            )
        },
    )
}

private val ThumbWidth = 4.dp
private val ThumbMargin = 4.dp

/** Fixed: a length from the content's height would change as posts of different heights scroll in. */
private val ThumbLength = 48.dp

/** [scrollProgress] for a laid-out list; null when it all fits, so there is nothing to show. */
internal fun LazyListLayoutInfo.scrollProgress(): Float? {
    val first = visibleItemsInfo.firstOrNull() ?: return null
    val last = visibleItemsInfo.last()
    fun hidden(px: Int, size: Int) = if (size <= 0) 0f else (px.toFloat() / size).coerceIn(0f, 1f)
    return scrollProgress(
        count = totalItemsCount,
        first = first.index,
        firstHidden = hidden(viewportStartOffset - first.offset, first.size),
        last = last.index,
        lastHidden = hidden(last.offset + last.size - viewportEndOffset, last.size),
    )
}

/**
 * How far through the list, 0 at the top and 1 at the bottom, counting items: [first] and [last] are the visible
 * ends, [firstHidden] and [lastHidden] the fractions of them out of view. It needs no estimate of the content's
 * height, so it moves smoothly over posts of any length, slower through long ones, and is exact at both ends.
 */
internal fun scrollProgress(count: Int, first: Int, firstHidden: Float, last: Int, lastHidden: Float): Float? {
    val above = first + firstHidden
    val below = (count - 1 - last) + lastHidden
    if (above + below <= 0f) return null
    return above / (above + below)
}
