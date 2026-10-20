package au.howesthat.whirl.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.data.AccountRepository
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.model.WhirlpoolError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { INTRO, KEY, NOTIFICATIONS, THEME, DONE }

data class SetupState(
    val step: SetupStep = SetupStep.INTRO,
    val key: String = "",
    val checking: Boolean = false,
    /** Whirlpool rejected the key: show the "include the dashes" dialog. */
    val rejected: WhirlpoolError.BadKey? = null,
    /** Any other failure while checking the key (network, Cloudflare). */
    val error: Throwable? = null,
    val account: Account? = null,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    val settings: StateFlow<Settings> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    fun next() = _state.update { it.copy(step = SetupStep.entries[minOf(it.step.ordinal + 1, SetupStep.entries.lastIndex)]) }

    fun back() = _state.update { it.copy(step = SetupStep.entries[maxOf(it.step.ordinal - 1, 0)]) }

    fun setKey(key: String) = _state.update { it.copy(key = key, error = null) }

    fun dismissRejected() = _state.update { it.copy(rejected = null) }

    /** One `get=user` call; on success moves on to notifications, on a bad key clears the field (2.14.12 behaviour). */
    fun checkKey() {
        val key = _state.value.key.trim()
        if (key.isEmpty() || _state.value.checking) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            try {
                val account = accounts.signIn(key)
                _state.update { it.copy(checking = false, account = account, step = SetupStep.NOTIFICATIONS) }
            } catch (e: WhirlpoolError.BadKey) {
                _state.update { it.copy(checking = false, rejected = e, key = "") }
            } catch (e: WhirlpoolError) {
                _state.update { it.copy(checking = false, error = e) }
            }
        }
    }

    fun setWatchedNotify(on: Boolean) = viewModelScope.launch { settingsRepository.update { it.copy(watchedNotify = on) } }

    fun setInterval(minutes: Int) = viewModelScope.launch { settingsRepository.update { it.copy(notifyIntervalMinutes = minutes) } }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settingsRepository.update { it.copy(theme = mode) } }

    fun finish(onDone: () -> Unit) = viewModelScope.launch {
        settingsRepository.setSetupDone()
        onDone()
    }
}
