package au.howesthat.whirl.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ScrollThumbDragTest {
    @get:Rule val compose = createComposeRule()

    private lateinit var list: LazyListState

    private fun content() = compose.setContent {
        list = rememberLazyListState()
        val thumb = rememberScrollThumb(list) { "#${it + 1}" }
        Box(Modifier.size(300.dp, 600.dp).testTag("box").scrollThumbDrag(thumb)) {
            LazyColumn(Modifier.fillMaxSize(), state = list) {
                items(1000) { Text("Post $it", Modifier.fillMaxWidth().height(60.dp)) }
            }
            ScrollThumb(thumb, Modifier.align(Alignment.CenterEnd))
        }
    }

    @Test
    fun `dragging the visible thumb to the bottom jumps through the list`() {
        content()
        // Scrolling shows the thumb at the top; grab it while it shows and drag it down.
        compose.onNodeWithTag("box").performTouchInput {
            swipe(start = center, end = center.copy(y = center.y - 200f), durationMillis = 300)
        }
        val before = list.firstVisibleItemIndex
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("box").performTouchInput {
            // The thumb is near the top at the right edge: 4 dp in from the edge, a few posts down.
            val x = width - 6.dp.toPx()
            down(androidx.compose.ui.geometry.Offset(x, 4.dp.toPx() + 10f))
            moveTo(androidx.compose.ui.geometry.Offset(x, height * 0.5f))
            moveTo(androidx.compose.ui.geometry.Offset(x, height - 1f))
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("#1000", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("box").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue(list.firstVisibleItemIndex > 900, "jumped from $before to ${list.firstVisibleItemIndex}")
    }

    @Test
    fun `a drag away from the thumb scrolls the list as usual`() {
        content()
        compose.onNodeWithTag("box").performTouchInput {
            swipe(start = center, end = center.copy(y = center.y - 300f), durationMillis = 300)
        }
        compose.waitForIdle()
        val index = list.firstVisibleItemIndex
        assertTrue(index in 1..20, "an ordinary scroll, at $index")
        // When the thumb has faded, the edge is the list's again.
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithTag("box").performTouchInput {
            val x = width - 6.dp.toPx()
            swipe(start = androidx.compose.ui.geometry.Offset(x, height * 0.8f), end = androidx.compose.ui.geometry.Offset(x, height * 0.3f), durationMillis = 300)
        }
        compose.waitForIdle()
        assertTrue(list.firstVisibleItemIndex in index + 1..index + 30, "scrolled normally to ${list.firstVisibleItemIndex}")
        assertEquals(false, list.firstVisibleItemIndex > 500)
    }
}
