package au.howesthat.whirl.core.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScrollThumbTest {

    @Test
    fun `top and bottom are exact`() {
        assertEquals(0f, scrollProgress(count = 22, first = 0, firstHidden = 0f, last = 3, lastHidden = 0.5f))
        assertEquals(1f, scrollProgress(count = 22, first = 19, firstHidden = 0.2f, last = 21, lastHidden = 0f))
    }

    @Test
    fun `nothing to show when the whole list fits`() {
        assertNull(scrollProgress(count = 3, first = 0, firstHidden = 0f, last = 2, lastHidden = 0f))
    }

    @Test
    fun `halfway through an even list is the middle`() {
        assertEquals(0.5f, scrollProgress(count = 21, first = 9, firstHidden = 0.5f, last = 11, lastHidden = 0.5f))
    }

    @Test
    fun `scrolling down only ever moves it down, as items of different heights come and go`() {
        // One tall post fills the screen, then several short ones do.
        val steps = listOf(
            scrollProgress(20, first = 4, firstHidden = 0.1f, last = 4, lastHidden = 0.8f),
            scrollProgress(20, first = 4, firstHidden = 0.9f, last = 5, lastHidden = 0.3f),
            scrollProgress(20, first = 5, firstHidden = 0.5f, last = 10, lastHidden = 0.2f),
            scrollProgress(20, first = 6, firstHidden = 0f, last = 11, lastHidden = 0.9f),
        ).map { it!! }
        assertTrue(steps.zipWithNext().all { (a, b) -> b > a }, steps.toString())
    }

    @Test
    fun `the rest of the track is the rest of the list, in small steps`() {
        // 1,000 px above and 3,000 px below, the thumb 100 px down a 400 px track.
        assertEquals(3_000f, dragDelta(100f, 400f, 400f, above = 1_000f, below = 3_000f), "to the end of the track: the end of the list")
        assertEquals(-1_000f, dragDelta(100f, 0f, 400f, above = 1_000f, below = 3_000f), "to the top: the top")
        assertEquals(100f, dragDelta(100f, 110f, 400f, above = 1_000f, below = 3_000f), "10 px of track, a 30th of the rest")
        assertEquals(-100f, dragDelta(100f, 90f, 400f, above = 1_000f, below = 3_000f))
        assertEquals(0f, dragDelta(100f, 100f, 400f, above = 1_000f, below = 3_000f))
        // Steps add up to the same as one move: no drift, whatever the finger's speed.
        var at = 100f
        var below = 3_000f
        var moved = 0f
        for (to in listOf(150f, 220f, 300f, 400f)) {
            val d = dragDelta(at, to, 400f, 0f, below)
            moved += d
            below -= d
            at = to
        }
        assertEquals(3_000f, moved, 0.01f)
    }

    @Test
    fun `a long step jumps to the estimated post`() {
        assertEquals(ScrollPosition(12, 50), jumpFor(1_250f, 100f, 800))
        assertEquals(ScrollPosition(0, 0), jumpFor(-30f, 100f, 800))
        assertEquals(ScrollPosition(799, 0), jumpFor(1e9f, 100f, 800).let { it!!.copy(offset = 0) })
        assertEquals(null, jumpFor(10f, 0f, 800))
    }
}
