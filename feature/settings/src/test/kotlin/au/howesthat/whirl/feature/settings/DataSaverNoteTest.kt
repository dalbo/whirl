package au.howesthat.whirl.feature.settings

import android.app.Application
import android.content.Intent
import android.net.ConnectivityManager
import android.provider.Settings
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.ui.WhirlTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

/** The note under the notification settings while Data Saver holds the background check back. */
@RunWith(RobolectricTestRunner::class)
class DataSaverNoteTest {
    @get:Rule val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()

    private fun restrict(status: Int) = shadowOf(app.getSystemService(ConnectivityManager::class.java)).setRestrictBackgroundStatus(status)

    @Test
    fun `shown while Data Saver restricts Whirl, and opens its data setting`() {
        restrict(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)
        compose.setContent { WhirlTheme(darkTheme = false) { DataSaverNote() } }
        compose.onNodeWithText("Checks wait for Wi-Fi").performClick()
        val opened: Intent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, opened.action)
        assertEquals("package:${app.packageName}", opened.dataString)
    }

    @Test
    fun `hidden when Whirl may use data in the background, or Data Saver is off`() {
        restrict(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED)
        compose.setContent { WhirlTheme(darkTheme = false) { DataSaverNote() } }
        compose.onNodeWithText("Checks wait for Wi-Fi").assertDoesNotExist()
    }
}
