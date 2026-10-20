package au.howesthat.whirl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.PendingWatch
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.WatchedRepository
import au.howesthat.whirl.core.network.WhirlpoolApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainState(val settings: Settings, val setupDone: Boolean)

@HiltViewModel
class MainViewModel @Inject constructor(
    settings: SettingsRepository,
    api: WhirlpoolApi,
    private val watched: WatchedRepository,
) : ViewModel() {
    /** Null until settings are read, which keeps the splash screen up. */
    val state: StateFlow<MainState?> = combine(settings.settings, settings.isSetupDone, ::MainState)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val account = settings.account.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whirlpool's site notice from the last API call. */
    val alert = api.alert

    private val _link = MutableStateFlow<Any?>(null)
    /** A route from a link the app was opened with, waiting for the NavHost (which may still be behind setup). */
    val link: StateFlow<Any?> = _link.asStateFlow()

    fun openLink(route: Any?) {
        if (route != null) _link.value = route
    }

    fun linkHandled() {
        _link.value = null
    }

    /** Watch actions waiting for Undo, and failures, for the app-wide snackbar. */
    val watchEvents = watched.events

    suspend fun undo(p: PendingWatch) = watched.undo(p)

    fun commit(p: PendingWatch) = watched.commit(p)
}
