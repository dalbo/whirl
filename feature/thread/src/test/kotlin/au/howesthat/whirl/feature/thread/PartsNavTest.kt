package au.howesthat.whirl.feature.thread

import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadPart
import au.howesthat.whirl.core.scraper.ThreadPageParser
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PartsNavTest {

    private fun parts(fixture: String) =
        ThreadPageParser.parse(File(System.getProperty("whirl.fixtures"), "html/$fixture").readText()).parts

    @Test
    fun `last of 58 parts has a previous part and no next`() {
        val nav = partsNav(parts("thread_big_9nznpky9_last.html"))!!
        assertEquals(58, nav.all.size)
        assertEquals(58, nav.current?.number)
        assertEquals(ThreadId("98kmn563"), nav.previous?.threadId)
        assertNull(nav.next)
    }

    @Test
    fun `middle part has both neighbours`() {
        val nav = partsNav(
            listOf(
                ThreadPart(3, ThreadId("c"), isCurrent = false),
                ThreadPart(1, ThreadId("a"), isCurrent = false),
                ThreadPart(2, ThreadId("b"), isCurrent = true),
            ),
        )!!
        assertEquals(listOf(1, 2, 3), nav.all.map { it.number })
        assertEquals(ThreadId("a"), nav.previous?.threadId)
        assertEquals(ThreadId("c"), nav.next?.threadId)
    }

    @Test
    fun `no current part still lists them all`() {
        val nav = partsNav(listOf(ThreadPart(1, ThreadId("a"), false), ThreadPart(2, ThreadId("b"), false)))!!
        assertNull(nav.current)
        assertNull(nav.previous)
        assertNull(nav.next)
        assertEquals(2, nav.all.size)
    }

    @Test
    fun `no parts gives no row`() {
        assertNull(partsNav(emptyList()))
    }
}
