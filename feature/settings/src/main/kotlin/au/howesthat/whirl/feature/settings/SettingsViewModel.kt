package au.howesthat.whirl.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import au.howesthat.whirl.core.data.Account
import au.howesthat.whirl.core.data.AccountRepository
import au.howesthat.whirl.core.data.AppInfo
import au.howesthat.whirl.core.data.Import
import au.howesthat.whirl.core.data.IoDispatcher
import au.howesthat.whirl.core.data.LibraryRepository
import au.howesthat.whirl.core.data.SettingChange
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.SettingsFile
import au.howesthat.whirl.core.data.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/** Opens the files the system picker returned; a seam so tests need no content provider. */
interface BackupFiles {
    /** Null when the provider gave no stream. */
    fun openInput(uri: Uri): InputStream?
    fun openOutput(uri: Uri): OutputStream?
}

class ContentBackupFiles @Inject constructor(@ApplicationContext private val context: Context) : BackupFiles {
    override fun openInput(uri: Uri) = context.contentResolver.openInputStream(uri)
    // "wt": replace a file picked again rather than overwrite its start.
    override fun openOutput(uri: Uri) = context.contentResolver.openOutputStream(uri, "wt")
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class BackupFilesModule {
    @Binds abstract fun backupFiles(files: ContentBackupFiles): BackupFiles
}

/** What a picked file would change, waiting for Apply. Counts are of favourites and mutes not already there. */
data class ImportPreview(
    val import: Import,
    val changes: List<SettingChange>,
    val newFavourites: Int,
    val newMutes: Int,
) {
    val changesNothing: Boolean get() = changes.isEmpty() && newFavourites == 0 && newMutes == 0
}

/** One-off results of Export and Import, shown as a snackbar or (for a refused file) a dialog. */
sealed interface BackupResult {
    data object Exported : BackupResult
    data object ExportFailed : BackupResult
    data object Imported : BackupResult
    data object ReadFailed : BackupResult
    data class Refused(val problem: SettingsFile.Problem) : BackupResult
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val accounts: AccountRepository,
    private val library: LibraryRepository,
    private val files: BackupFiles,
    private val app: AppInfo,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {
    /** Null until read from disk: the screen waits for it, so a first tap never acts on the defaults. */
    val settings: StateFlow<Settings?> = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val account: StateFlow<Account?> = accounts.account.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _preview = MutableStateFlow<ImportPreview?>(null)
    /** An import read and waiting for Apply or Cancel; nothing is written before Apply. */
    val preview: StateFlow<ImportPreview?> = _preview.asStateFlow()

    private val _results = Channel<BackupResult>(Channel.BUFFERED)
    val results: Flow<BackupResult> = _results.receiveAsFlow()

    fun update(change: (Settings) -> Settings) {
        viewModelScope.launch { repository.update(change) }
    }

    fun signOut(onSignedOut: () -> Unit) {
        viewModelScope.launch {
            accounts.signOut()
            onSignedOut()
        }
    }

    /** Writes settings, favourites and mutes to [uri]; never the key or account (SettingsFile). */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val text = SettingsFile.write(repository.settings.first(), library.favouriteForums.first(), library.mutedThreads.first(), app.versionName)
            val ok = withContext(io) {
                try {
                    files.openOutput(uri)?.use { it.write(text.toByteArray()) } != null
                } catch (_: IOException) {
                    false
                } catch (_: SecurityException) {
                    false
                }
            }
            _results.send(if (ok) BackupResult.Exported else BackupResult.ExportFailed)
        }
    }

    /** Reads [uri] and shows what it would change ([preview]); a file that is not Whirl's is refused. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val read = withContext(io) {
                try {
                    files.openInput(uri)?.use { input -> SettingsFile.readText(input)?.let(SettingsFile::read) ?: SettingsFile.Read.Rejected(SettingsFile.Problem.TOO_LARGE) }
                } catch (_: IOException) {
                    null
                } catch (_: SecurityException) {
                    null
                }
            }
            when (read) {
                null -> _results.send(BackupResult.ReadFailed)
                is SettingsFile.Read.Rejected -> _results.send(BackupResult.Refused(read.problem))
                is SettingsFile.Read.Imported -> _preview.value = previewOf(read.import)
            }
        }
    }

    private suspend fun previewOf(import: Import): ImportPreview {
        val favourites = library.favouriteForums.first()
        val muted = library.mutedThreads.first()
        return ImportPreview(
            import,
            import.changes(repository.settings.first()),
            newFavourites = (import.favouriteForums - favourites).size,
            newMutes = (import.mutedThreads - muted).size,
        )
    }

    /**
     * Applies the previewed import through the same stores as any change, so the notification check re-arms on a new
     * interval as it would from the switch. Favourites and mutes are added, never removed.
     */
    fun applyImport() {
        val preview = _preview.value ?: return
        _preview.value = null
        viewModelScope.launch {
            val import = preview.import
            repository.update { import.applyTo(it) }
            if (import.favouriteForums.isNotEmpty()) library.addFavourites(import.favouriteForums)
            if (import.mutedThreads.isNotEmpty()) library.setMuted(import.mutedThreads, muted = true)
            _results.send(BackupResult.Imported)
        }
    }

    fun cancelImport() {
        _preview.value = null
    }
}
