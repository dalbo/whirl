package au.howesthat.whirl.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.notifications.CheckHistory
import au.howesthat.whirl.core.notifications.CheckRecord
import au.howesthat.whirl.core.notifications.DeviceState
import au.howesthat.whirl.core.notifications.stopReasonName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import javax.inject.Inject

data class CheckHistoryState(
    /** Newest first. */
    val records: List<CheckRecord>,
    val scheduled: Boolean,
    val intervalMinutes: Int,
    val nextRun: Instant?,
    /** Why Android last stopped the scheduled check, if it did. */
    val lastStop: String?,
    val device: DeviceState,
)

@HiltViewModel
class CheckHistoryViewModel @Inject constructor(
    private val history: CheckHistory,
    settings: SettingsRepository,
) : ViewModel() {
    /** Bumped on resume: the battery and notification settings can change while Whirl is in the background. */
    private val reads = MutableStateFlow(0)

    val state: StateFlow<CheckHistoryState?> = combine(history.records, history.work, settings.settings, reads) { records, work, s, _ ->
        CheckHistoryState(
            records = records,
            scheduled = work != null,
            intervalMinutes = s.notifyIntervalMinutes,
            // Long.MAX_VALUE while it waits on a constraint (no network) rather than a time.
            nextRun = work?.nextScheduleTimeMillis?.takeIf { it != Long.MAX_VALUE }?.let(Instant::ofEpochMilli),
            lastStop = work?.let { stopReasonName(it.stopReason) },
            device = history.device(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun reread() = reads.update { it + 1 }
}
