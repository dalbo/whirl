package au.howesthat.whirl.core.ui

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TextScaleTest {

    @get:Rule val compose = createComposeRule()

    private fun fontScaleUnder(textScale: Float): Pair<Float, Float> {
        var outside = 0f
        var inside = 0f
        compose.setContent {
            outside = LocalDensity.current.fontScale
            WhirlTheme(darkTheme = false, dynamicColor = false, textScale = textScale) { inside = LocalDensity.current.fontScale }
        }
        compose.waitForIdle()
        return outside to inside
    }

    @Test
    fun `text size multiplies the system font scale`() {
        val (outside, inside) = fontScaleUnder(1.3f)
        assertEquals(outside * 1.3f, inside, 0.001f)
    }

    @Test
    fun `the default leaves the system density alone`() {
        val (outside, inside) = fontScaleUnder(1f)
        assertEquals(outside, inside)
    }
}
