package au.howesthat.whirl.feature.threadlist

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import au.howesthat.whirl.core.data.WatchAction
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.ui.WhirlTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThreadRowAccessibilityTest {

    @get:Rule val compose = createComposeRule()

    private val item = ThreadItem(
        id = ThreadId("3pxj858k"),
        openId = ThreadId("3pxj858k"),
        title = "Simple, ad-free NBN plan comparison tool I built",
        forum = ForumRef(92, "Choosing an ISP"),
        unreadCount = 3,
        replies = 256,
        pageCount = 13,
        lastPoster = "samuria",
        lastPostedText = "Friday at 10:19 am",
    )

    private fun row(fontScale: Float = 1f) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            WhirlTheme(darkTheme = false) {
                ThreadRowItem(
                    item = item,
                    now = Instant.now(),
                    onOpen = {},
                    onLastPost = {},
                    onOpenInBrowser = {},
                    onOpenForum = {},
                    watchActions = listOf(WatchAction.MARK_READ, WatchAction.UNWATCH),
                )
            }
        }
    }

    @Test
    fun `the long-press menu is offered as custom actions`() {
        row()
        // The row merges its texts; its actions live on the clickable (merged) node.
        val merged = compose.onNodeWithText(item.title).fetchSemanticsNode()
        val labels = merged.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
        assertEquals(listOf("Mark as read", "Unwatch", "Jump to last post", "Open in browser", "Go to Choosing an ISP"), labels)
    }

    @Test
    fun `the unread badge is read as a count of posts`() {
        row()
        compose.onNodeWithContentDescription("3 unread posts", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `at 200 percent font scale the title and details still show`() {
        row(fontScale = 2f)
        compose.onNodeWithText(item.title, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("samuria", substring = true, useUnmergedTree = true).assertIsDisplayed()
    }
}
