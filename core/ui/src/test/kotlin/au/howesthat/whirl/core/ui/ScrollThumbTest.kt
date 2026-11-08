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
    fun `a drag maps back to the position the thumb shows, part way into an item`() {
        // 20 posts of 100 px; the screen shows 4.5 of them, so 15.5 are out of view wherever the list is.
        val at = { f: Float -> positionFor(f, span = 15.5f, count = 20) { 100 } }
        assertEquals(ScrollPosition(0, 0), at(0f))
        assertEquals(ScrollPosition(15, 50), at(1f), "the end: the last post at the bottom, not at the top")
        assertEquals(ScrollPosition(7, 75), at(0.5f))
        // Small moves of the finger move the list a little, not a post at a time.
        val steps = (0..20).map { at(it / 20f) }.map { it.index * 100 + it.offset }
        assertTrue(steps.zipWithNext().all { (a, b) -> b - a in 70..85 }, steps.toString())
        assertEquals(ScrollPosition(0, 0), positionFor(0.7f, 0f, 0) { 0 })
    }
}
