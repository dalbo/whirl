package au.howesthat.whirl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.network.WhirlpoolApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainState(val settings: Settings, val setupDone: Boolean)

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository, api: WhirlpoolApi) : ViewModel() {
    /** Null until settings are read, which keeps the splash screen up. */
    val state: StateFlow<MainState?> = combine(settings.settings, settings.isSetupDone, ::MainState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val account = settings.account.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whirlpool's site notice from the last API call. */
    val alert = api.alert
}
