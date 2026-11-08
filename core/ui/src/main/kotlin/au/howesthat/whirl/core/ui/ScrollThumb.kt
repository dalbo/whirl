package au.howesthat.whirl.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Where the list is: a thin thumb at the end edge that shows while scrolling and fades once it stops, like
 * Whirldroid's. Only an indicator, with no touch handling, so it never takes a page swipe, a tap or the Back gesture.
 * TalkBack scrolls lists itself, so it is hidden from accessibility.
 */
@Composable
fun ScrollThumb(state: LazyListState, modifier: Modifier = Modifier) {
    val thumb = rememberScrollThumb(state)
    ScrollThumb(thumb, modifier)
}

/**
 * A scroll thumb that can be dragged: [rememberScrollThumb] holds it, [scrollThumbDrag] on the box holding the list
 * takes a drag that starts on the visible thumb, and [ScrollThumb] draws it, with [ScrollThumbState.label] (say, the
 * post number) beside it while dragging.
 */
@Stable
class ScrollThumbState internal constructor(val list: LazyListState) {
    /** Shown beside the thumb while dragging, for the item at the top. */
    internal var label: (index: Int) -> String? by mutableStateOf({ null })
    /** 0 hidden … 1 shown; written by [ScrollThumb] as it fades. */
    internal var alpha by mutableFloatStateOf(0f)
    internal var dragging by mutableStateOf(false)
    /** The track's height in pixels (the box's, less the margins), set by [ScrollThumb]. */
    internal var track by mutableFloatStateOf(0f)
    internal val progress by derivedStateOf { list.layoutInfo.scrollProgress() }
    /** The item the drag has put at the top, for the label. */
    internal var dragIndex by mutableStateOf<Int?>(null)
    /** While dragging, where the finger has put the thumb (0 top … 1 bottom); the thumb is drawn there. */
    internal var dragFraction by mutableStateOf<Float?>(null)
    /** Where the thumb is drawn: under the finger while dragging, else where the list is. */
    internal val shownAt: Float? get() = dragFraction ?: progress
}

@Composable
fun rememberScrollThumb(state: LazyListState, label: (index: Int) -> String? = { null }): ScrollThumbState =
    remember(state) { ScrollThumbState(state) }.also { it.label = label }

@Composable
fun ScrollThumb(thumb: ScrollThumbState, modifier: Modifier = Modifier) {
    val shown = thumb.list.isScrollInProgress || thumb.dragging
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (shown) tween(150) else tween(durationMillis = 300, delayMillis = 1200),
        label = "scroll thumb",
    )
    thumb.alpha = alpha
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Read here so the exclusion below follows the thumb as the list scrolls.
    val progress = thumb.shownAt
    Box(modifier.fillMaxHeight().clearAndSetSemantics {}) {
        Spacer(
            Modifier
                .align(if (rtl) Alignment.TopStart else Alignment.TopEnd)
                .fillMaxHeight()
                .width(ThumbWidth + ThumbMargin)
                .padding(vertical = ThumbMargin)
                .onSizeChanged { thumb.track = it.height.toFloat() }
                // Android's Back gesture takes the screen's edges: give the thumb to the app, only while it shows.
                .then(
                    if (alpha > 0f && progress != null) {
                        val thumbLength = with(density) { ThumbLength.toPx() }
                        val slack = with(density) { TouchSlack.toPx() }
                        Modifier.systemGestureExclusion { coords ->
                            val length = thumbLength.coerceAtMost(coords.size.height.toFloat())
                            val top = progress * (coords.size.height - length)
                            Rect(-slack, top - slack, coords.size.width + slack, top + length + slack)
                        }
                    } else {
                        Modifier
                    },
                )
                .drawBehind {
                    val p = thumb.shownAt ?: return@drawBehind
                    if (alpha == 0f) return@drawBehind
                    val length = ThumbLength.toPx().coerceAtMost(size.height)
                    val width = (if (thumb.dragging) DraggingWidth else ThumbWidth).toPx()
                    // The margin goes on the screen edge's side.
                    val x = if (layoutDirection == LayoutDirection.Rtl) ThumbMargin.toPx() else size.width - ThumbMargin.toPx() - width
                    drawRoundRect(
                        color = if (thumb.dragging) color else color.copy(alpha = color.alpha * 0.6f * alpha),
                        topLeft = Offset(x, p * (size.height - length)),
                        size = Size(width, length),
                        cornerRadius = CornerRadius(width / 2),
                    )
                },
        )
        val label = thumb.dragIndex?.let(thumb.label)
        val p = progress
        if (thumb.dragging && label != null && p != null) {
            val length = with(density) { ThumbLength.toPx() }.coerceAtMost(thumb.track)
            val y = with(density) { ThumbMargin.toPx() } + p * (thumb.track - length)
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .align(if (rtl) Alignment.TopStart else Alignment.TopEnd)
                    .offset { IntOffset(0, y.roundToInt()) }
                    .padding(horizontal = ThumbWidth + ThumbMargin + 8.dp),
            ) {
                Text(label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * Lets [thumb] be dragged: put it on the box that holds both the list and [ScrollThumb] (aligned to its end edge). A
 * touch that starts on the visible thumb is taken before the list sees it and moves the list; any other touch passes
 * through untouched.
 */
@Composable
fun Modifier.scrollThumbDrag(thumb: ScrollThumbState): Modifier {
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(thumb, rtl) { thumbDrags(thumb, scope, rtl) }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.thumbDrags(thumb: ScrollThumbState, scope: CoroutineScope, rtl: Boolean) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val p = thumb.progress
        if (p == null || thumb.alpha < 0.5f) return@awaitEachGesture
        val margin = ThumbMargin.toPx()
        val length = ThumbLength.toPx().coerceAtMost(thumb.track)
        val top = margin + p * (thumb.track - length)
        val fromEdge = if (rtl) down.position.x else size.width - down.position.x
        val slack = TouchSlack.toPx()
        if (fromEdge > ThumbWidth.toPx() + margin + slack || down.position.y !in (top - slack)..(top + length + slack)) {
            return@awaitEachGesture
        }
        down.consume()
        // Where on the thumb it was grabbed, so the thumb does not jump to the finger.
        val grab = down.position.y - top
        thumb.dragging = true
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    change.consume()
                    break
                }
                if (change.positionChange() != Offset.Zero) {
                    change.consume()
                    val fraction = ((change.position.y - grab - margin) / (thumb.track - length)).coerceIn(0f, 1f)
                    thumb.dragFraction = fraction
                    val target = thumb.list.layoutInfo.positionFor(fraction) ?: continue
                    thumb.dragIndex = target.index
                    scope.launch { thumb.list.scrollToItem(target.index, target.offset) }
                }
            }
        } finally {
            thumb.dragging = false
            thumb.dragIndex = null
            thumb.dragFraction = null
        }
    }
}

/** Where to scroll: [index] at the top, [offset] pixels of it scrolled out of view. */
internal data class ScrollPosition(val index: Int, val offset: Int)

/**
 * Where a drag to [fraction] (0 top … 1 bottom) puts the list: the inverse of [scrollProgress], so the thumb the list
 * then shows is where the finger is. Progress counts items out of view above and below (whole and part); their sum is
 * the same wherever the list is, give or take items of different heights, so [fraction] of it is how much to have
 * above: that many whole items, and the rest a part of the next one. Moving within an item, not item by item, is what
 * keeps a short list (20 posts) smooth.
 */
internal fun LazyListLayoutInfo.positionFor(fraction: Float): ScrollPosition? {
    val visible = visibleItemsInfo.ifEmpty { return null }
    val first = visible.first()
    val last = visible.last()
    fun hidden(px: Int, size: Int) = if (size <= 0) 0f else (px.toFloat() / size).coerceIn(0f, 1f)
    val above = first.index + hidden(viewportStartOffset - first.offset, first.size)
    val below = (totalItemsCount - 1 - last.index) + hidden(last.offset + last.size - viewportEndOffset, last.size)
    val average = visible.sumOf { it.size } / visible.size
    return positionFor(fraction, above + below, totalItemsCount) { index -> visible.firstOrNull { it.index == index }?.size ?: average }
}

/** [positionFor] over a [span] of items out of view, with [sizeOf] an item's height (or a guess). */
internal fun positionFor(fraction: Float, span: Float, count: Int, sizeOf: (Int) -> Int): ScrollPosition {
    if (count <= 0) return ScrollPosition(0, 0)
    val target = (fraction.coerceIn(0f, 1f) * span).coerceAtLeast(0f)
    val index = target.toInt().coerceAtMost(count - 1)
    val offset = ((target - index) * sizeOf(index)).roundToInt()
    return ScrollPosition(index, offset)
}

private val ThumbWidth = 4.dp
private val DraggingWidth = 8.dp
private val ThumbMargin = 4.dp
/** How far around the thin thumb a finger still grabs it. */
private val TouchSlack = 16.dp

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
