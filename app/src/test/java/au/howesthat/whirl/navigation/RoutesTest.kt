package au.howesthat.whirl.navigation

import au.howesthat.whirl.core.data.Homepage
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RoutesTest {
    @Test
    fun `every start screen is a drawer destination`() {
        val drawerRoutes = TOP_LEVEL.map { it.route }
        Homepage.entries.forEach { assertTrue(it.route() in drawerRoutes, "$it has no drawer entry") }
        assertEquals(Homepage.entries.size, drawerRoutes.toSet().size)
    }
}
