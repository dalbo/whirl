package au.howesthat.whirl.core.ui

import android.view.KeyEvent
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
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

    @Test
    fun `Escape closes the bar`() {
        bar()
        field.performKeyInput { pressKey(Key.Escape) }
        assertTrue(closed)
    }

    @Test
    fun `Ctrl+F and a lone slash are find shortcuts, other keys are not`() {
        assertTrue(isFindShortcut(KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
        assertTrue(isFindShortcut(KeyEvent.KEYCODE_SLASH, 0))
        assertTrue(!isFindShortcut(KeyEvent.KEYCODE_F, 0), "plain f is typing")
        assertTrue(!isFindShortcut(KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        assertTrue(!isFindShortcut(KeyEvent.KEYCODE_SLASH, KeyEvent.META_SHIFT_ON), "? is not /")
    }

    @Test
    fun `a shortcut reaches the screen on top`() {
        val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var opened = 0
        compose.setContent {
            CompositionLocalProvider(LocalFindRequests provides requests) { OnFindShortcut { opened++ } }
        }
        compose.waitForIdle()
        requests.tryEmit(Unit)
        compose.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun `with a thread open beside the list, Back is left to the thread`() {
        var closeOnBack by mutableStateOf(true)
        var closedBy = 0
        var reachedThread = 0
        var dispatcher: OnBackPressedDispatcher? = null
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            // Stands in for the thread pane's Back, registered before the bar.
            BackHandler { reachedThread++ }
            WhirlTheme(darkTheme = false) {
                FindBar(query = "nbn", onQueryChange = {}, onClose = { closedBy++ }, label = "Find", matches = 1, closeOnBack = closeOnBack)
            }
        }
        field.assertIsFocused()
        closeOnBack = false
        compose.waitForIdle()
        compose.runOnUiThread { dispatcher!!.onBackPressed() }
        compose.waitForIdle()
        assertEquals(0, closedBy, "the bar stays open")
        assertEquals(1, reachedThread)
    }
}
