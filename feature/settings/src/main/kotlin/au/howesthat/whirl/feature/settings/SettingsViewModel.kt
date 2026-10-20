package au.howesthat.whirl.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.data.AccountRepository
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val accounts: AccountRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val account: StateFlow<Account?> = accounts.account.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(change) }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            accounts.signOut()
            onSignedOut()
        }
    }
}
