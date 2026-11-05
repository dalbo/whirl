package au.howesthat.whirl.feature.settings

import android.app.usage.UsageStatsManager
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToIndex
import au.howesthat.whirl.core.notifications.CheckOutcome
import au.howesthat.whirl.core.notifications.CheckRecord
import au.howesthat.whirl.core.notifications.CheckResult
import au.howesthat.whirl.core.notifications.DeviceState
import au.howesthat.whirl.core.notifications.NetworkKind
import au.howesthat.whirl.core.ui.WhirlTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class CheckHistoryListTest {
    @get:Rule val compose = createComposeRule()

    private fun record(id: Long, at: Instant) = CheckRecord(
        id, at, 1200, 0, 60, CheckOutcome(CheckResult.ERROR, detail = "check $id"),
        UsageStatsManager.STANDBY_BUCKET_ACTIVE, powerSave = false, deviceIdle = false, network = NetworkKind.WIFI,
    )

    private val device = DeviceState(
        standbyBucket = UsageStatsManager.STANDBY_BUCKET_ACTIVE, powerSave = false, deviceIdle = false, network = NetworkKind.WIFI,
        batteryOptimised = false, backgroundRestricted = false, dataSaver = false, notificationsAllowed = true, channelEnabled = true,
    )

    @Test
    fun `two checks that started in the same millisecond are both listed`() {
        // A clock set back, or a fixed one: the start time and attempt do not tell the rows apart.
        val at = Instant.parse("2026-11-05T09:00:00Z")
        val state = CheckHistoryState(listOf(record(2, at), record(1, at)), scheduled = true, intervalMinutes = 60, nextRun = null, lastStop = null, device = device)
        compose.setContent { WhirlTheme { CheckHistoryList(state, onAppInfo = {}) } }
        // Past the status, the app info button and the heading.
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(4)
        for (id in 1..2) assertEquals(1, compose.onAllNodesWithText("check $id", substring = true).fetchSemanticsNodes().size)
    }
}
