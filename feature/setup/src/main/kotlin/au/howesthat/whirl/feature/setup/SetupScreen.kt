package au.howesthat.whirl.feature.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalAutofillManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.ui.errorMessage

private val PROFILE_URL = WhirlpoolUrls().profile().toString()

/**
 * The first-run wizard. Finishing it sets `setupDone`, and MainActivity swaps it for the app.
 *
 * With [onNewKeyClosed], only the key step shows, for replacing a key Whirlpool rejected: back cancels it, a key that
 * passes stores it (switching accounts if it is another user's) and closes it.
 */
@Composable
fun SetupScreen(
    onNewKeyClosed: (() -> Unit)? = null,
    // Activity-scoped, so the new-key screen has its own, apart from a setup finished earlier in this activity.
    viewModel: SetupViewModel = hiltViewModel(key = if (onNewKeyClosed != null) "new-key" else null),
) {
    val newKeyOnly = onNewKeyClosed != null
    LaunchedEffect(newKeyOnly) { if (newKeyOnly) viewModel.startNewKey() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Before the effect above has run, the view model is still at the intro.
    val step = if (newKeyOnly) SetupStep.KEY else state.step
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val autofill = LocalAutofillManager.current

    BackHandler(enabled = step != SetupStep.INTRO) { if (onNewKeyClosed != null) onNewKeyClosed() else viewModel.back() }
    // A key that passed is worth saving: offers it to the password manager, which can fill it in on another phone.
    LaunchedEffect(state.account) { if (state.account != null) autofill?.commit() }
    LaunchedEffect(state.closed) {
        // The live value: a check that passed after an earlier Cancel left it set, and opening again has cleared it.
        if (viewModel.state.value.closed && onNewKeyClosed != null) {
            viewModel.startNewKey() // Ready for the next time, with closed cleared.
            onNewKeyClosed()
        }
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                SetupStep.INTRO -> Page(stringResource(R.string.setup_intro_title)) {
                    Text(stringResource(R.string.setup_intro_body))
                    NextButton(viewModel::next)
                }
                SetupStep.KEY -> KeyStep(state, viewModel, onCancel = onNewKeyClosed)
                SetupStep.NOTIFICATIONS -> Page(stringResource(R.string.setup_notify_title)) {
                    state.account?.let { Text(stringResource(R.string.setup_logged_in_as, it.userName, it.userId)) }
                    NotificationOptions(settings, viewModel::setWatchedNotify, viewModel::setInterval)
                    NextButton(viewModel::next)
                }
                SetupStep.THEME -> Page(stringResource(R.string.setup_theme_title)) {
                    ThemeOptions(settings.theme, viewModel::setTheme)
                    NextButton(viewModel::next)
                }
                SetupStep.DONE -> Page(stringResource(R.string.setup_done_title)) {
                    Text(stringResource(R.string.setup_done_body))
                    Button(onClick = viewModel::finish, Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_start)) }
                }
            }
        }
    }

    state.rejected?.let { rejected ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRejected,
            title = { Text(stringResource(R.string.setup_key_rejected_title)) },
            text = { Text(stringResource(R.string.setup_key_rejected_body, rejected.message.orEmpty())) },
            confirmButton = { TextButton(onClick = viewModel::dismissRejected) { Text(stringResource(R.string.ok)) } },
        )
    }
}

@Composable
private fun KeyStep(state: SetupState, viewModel: SetupViewModel, onCancel: (() -> Unit)?) {
    val context = LocalContext.current
    Page(stringResource(if (onCancel != null) R.string.setup_new_key_title else R.string.setup_key_title)) {
        if (onCancel != null) Text(stringResource(R.string.setup_new_key_body))
        Text(stringResource(R.string.setup_key_body))
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, PROFILE_URL.toUri())) }) {
            Text(stringResource(R.string.setup_key_open_profile))
        }
        OutlinedTextField(
            value = state.key,
            onValueChange = viewModel::setKey,
            label = { Text(stringResource(R.string.setup_key_label)) },
            singleLine = true,
            enabled = !state.checking,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { viewModel.checkKey() }),
            // Password managers offer to save the key and fill it in: the way back on a phone that restored no key
            // (Android 8, no screen lock, backup off). Left visible: it is long, with dashes, and usually pasted.
            modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password },
        )
        state.error?.let { Text(errorMessage(it), color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = viewModel::checkKey,
            enabled = state.key.isNotBlank() && !state.checking,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.setup_key_check))
        }
        if (onCancel != null) TextButton(onClick = onCancel, Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_new_key_cancel)) }
    }
}

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}

@Composable
private fun NextButton(onClick: () -> Unit) {
    Spacer(Modifier.size(8.dp))
    Button(onClick = onClick, Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_next)) }
}

/**
 * Shared with the settings screen's Notifications section. Turning alerts on asks for the notification permission
 * (Android 13+) first and stays off if it is refused; when the system blocks Whirl's notifications, a line says so and
 * opens the system settings.
 */
@Composable
fun NotificationOptions(settings: Settings, onWatchedNotify: (Boolean) -> Unit, onInterval: (Int) -> Unit) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose {}
    }
    val openSystemSettings = {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName),
        )
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = notificationsAllowed(context)
        when {
            granted -> onWatchedNotify(true)
            // Refused from the "blocked" line (or refused for good, so no dialog showed): the system settings can still allow it.
            settings.watchedNotify -> openSystemSettings()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.setup_notify_watched), Modifier.weight(1f))
        Switch(
            checked = settings.watchedNotify,
            onCheckedChange = { on ->
                if (on && Build.VERSION.SDK_INT >= 33 && !hasPermission(context)) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    onWatchedNotify(on)
                }
            },
        )
    }
    if (settings.watchedNotify && !allowed) {
        TextButton(
            onClick = {
                if (Build.VERSION.SDK_INT >= 33 && !hasPermission(context)) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else openSystemSettings()
            },
        ) { Text(stringResource(R.string.setup_notify_blocked)) }
    }
    if (settings.watchedNotify) {
        Text(stringResource(R.string.setup_notify_interval), style = MaterialTheme.typography.labelLarge)
        // Wraps rather than scrolls, so large text cannot hide the longer intervals off screen.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Settings.NOTIFY_INTERVALS.forEach { minutes ->
                FilterChip(
                    selected = settings.notifyIntervalMinutes == minutes,
                    onClick = { onInterval(minutes) },
                    label = { Text(intervalLabel(minutes)) },
                )
            }
        }
    }
}

private fun hasPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun notificationsAllowed(context: Context): Boolean =
    hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled()

@Composable
private fun intervalLabel(minutes: Int): String =
    if (minutes < 60) stringResource(R.string.minutes_short, minutes) else stringResource(R.string.hours_short, minutes / 60)

/** Shared with the settings screen. */
@Composable
fun ThemeOptions(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = mode == selected, onClick = { onSelect(mode) }, role = Role.RadioButton).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == selected, onClick = null)
                Text(themeLabel(mode), Modifier.padding(start = 16.dp))
            }
        }
    }
}

@Composable
fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.DARK_AT_NIGHT -> R.string.theme_dark_at_night
    },
)
