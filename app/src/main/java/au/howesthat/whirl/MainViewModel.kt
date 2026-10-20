package au.howesthat.whirl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainState(val settings: Settings, val setupDone: Boolean)

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** Null until settings are read, which keeps the splash screen up. */
    val state: StateFlow<MainState?> = combine(settings.settings, settings.isSetupDone, ::MainState)
        // Only the start destination needs setupDone, and it must not re-route an open NavHost.
        .distinctUntilChangedBy { it.settings }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
