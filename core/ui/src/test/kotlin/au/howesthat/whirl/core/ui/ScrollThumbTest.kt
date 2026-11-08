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
    fun `a drag maps to the item it brings to the top`() {
        assertEquals(0, thumbIndex(0f, 800))
        assertEquals(799, thumbIndex(1f, 800))
        assertEquals(400, thumbIndex(0.5f, 801))
        assertEquals(0, thumbIndex(0.7f, 0))
    }
}
