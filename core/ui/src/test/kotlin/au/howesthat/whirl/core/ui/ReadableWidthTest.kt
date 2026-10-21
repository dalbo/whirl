package au.howesthat.whirl.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReadableWidthTest {

    @get:Rule val compose = createComposeRule()

    private fun layout(available: Int) = compose.setContent {
        Box(Modifier.requiredWidth(available.dp).testTag("window")) {
            Text("Post", Modifier.readableWidth().testTag("post"))
        }
    }

    @Test
    fun `a wide window centres the text at the readable width`() {
        layout(1000)
        // The test window may be narrower than 1000 dp, so measure from the box rather than the root.
        val window = compose.onNodeWithTag("window").getBoundsInRoot().left
        compose.onNodeWithTag("post").assertWidthIsEqualTo(ReadableWidth).assertLeftPositionInRootIsEqualTo(window + (1000.dp - ReadableWidth) / 2)
    }

    @Test
    fun `a phone in portrait uses the whole width`() {
        layout(400)
        compose.onNodeWithTag("post").assertWidthIsEqualTo(400.dp)
    }
}
