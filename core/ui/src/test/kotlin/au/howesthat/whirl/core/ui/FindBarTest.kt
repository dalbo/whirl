package au.howesthat.whirl.core.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FindBarTest {

    @get:Rule val compose = createComposeRule()

    private var query by mutableStateOf("")
    private var closed = false
    private var searched: String? = null

    private fun bar(fontScale: Float = 1f, matches: Int? = 3, search: Boolean = true) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            WhirlTheme(darkTheme = false) {
                FindBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClose = { closed = true },
                    label = "Find in Watched",
                    matches = matches,
                    modifier = Modifier.testTag("bar"),
                    onSearchWhirlpool = if (search) { words -> searched = words } else null,
                )
            }
        }
    }

    private val field get() = compose.onNode(hasSetTextAction())

    @Test
    fun `typing filters, the chip searches Whirlpool and the arrow closes`() {
        bar()
        field.assertIsFocused()
        field.performTextInput(" nbn plans ")
        compose.waitForIdle()
        assertEquals(" nbn plans ", query)

        compose.onNodeWithText("Search Whirlpool for “nbn plans”").performClick()
        assertEquals("nbn plans", searched, "the words, trimmed")

        compose.onNodeWithContentDescription("Clear").performClick()
        compose.waitForIdle()
        assertEquals("", query)

        compose.onNodeWithContentDescription("Close find").performClick()
        assertTrue(closed)
    }

    @Test
    fun `no chip without a search, or before anything is typed`() {
        bar(search = false)
        field.performTextInput("nbn")
        compose.waitForIdle()
        compose.onNodeWithText("Search Whirlpool for “nbn”").assertDoesNotExist()
        assertNull(searched)
    }

    @Test
    fun `the match count is read out once typing pauses`() {
        bar()
        compose.mainClock.autoAdvance = false
        field.performTextInput("nbn")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("3 matches").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("3 matches").assertExists()
    }

    @Test
    fun `the bar grows with a 200 percent font instead of clipping the field`() {
        query = "nbn"
        bar(fontScale = 2f)
        compose.onNodeWithText("Search Whirlpool for “nbn”").assertIsDisplayed()
        val bar = compose.onNodeWithTag("bar").getUnclippedBoundsInRoot()
        val input = field.getUnclippedBoundsInRoot()
        assertTrue(input.bottom <= bar.bottom, "field ${input.bottom} inside bar ${bar.bottom}")
    }
}
