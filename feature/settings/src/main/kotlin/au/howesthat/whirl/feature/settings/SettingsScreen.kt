package au.howesthat.whirl.feature.settings

import android.app.KeyguardManager
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.Homepage
import au.howesthat.whirl.core.data.NotifyStyle
import au.howesthat.whirl.core.data.PostFontSize
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.TextScale
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.ui.LocalAppStyle
import au.howesthat.whirl.core.ui.Palette
import au.howesthat.whirl.core.ui.SectionHeading
import au.howesthat.whirl.core.ui.Styles
import au.howesthat.whirl.core.ui.Surfaces
import au.howesthat.whirl.core.ui.whirlTopBarColors
import au.howesthat.whirl.feature.setup.NotificationOptions
import au.howesthat.whirl.feature.setup.themeLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                colors = whirlTopBarColors(),
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(top = 8.dp), content = content)
    }
}

/** Settings: a list of its pages, each with a line on what it holds. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenPage: (SettingsPage) -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    SettingsScaffold(stringResource(R.string.settings_title), onBack) {
        Group(null) {
            SettingsPage.entries.forEach { page ->
                val summary = when (page) {
                    SettingsPage.ACCOUNT -> account?.let { stringResource(R.string.settings_logged_in_as, it.userName, it.userId) }
                    SettingsPage.NOTIFICATIONS -> settings?.let {
                        if (it.watchedNotify) pluralStringResource(R.plurals.settings_notifications_every, it.notifyIntervalMinutes, it.notifyIntervalMinutes)
                        else stringResource(R.string.settings_notifications_off)
                    }
                    else -> pageSummary(page)?.let { stringResource(it) }
                }
                SettingRow(stringResource(pageTitle(page)), summary) { onOpenPage(page) }
            }
        }
    }
}

/** One page of Settings. */
@Composable
fun SettingsPageScreen(
    page: SettingsPage,
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    onCheckHistory: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val loaded by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val layouts = remember(context) { Styles.layouts(context) }
    val palettes = remember(context) { Styles.palettes(context) }
    val account by viewModel.account.collectAsStateWithLifecycle()
    val update = viewModel::update

    SettingsScaffold(stringResource(pageTitle(page)), onBack) {
        // Shown once read from disk (a moment): toggles drawn from the defaults would act on the wrong values.
        val settings = loaded ?: return@SettingsScaffold
        Group(null) {
            when (page) {
                SettingsPage.ACCOUNT -> {
                    account?.let { SettingRow(stringResource(R.string.settings_logged_in_as, it.userName, it.userId)) }
                    SettingRow(stringResource(R.string.settings_sign_out), stringResource(R.string.settings_sign_out_summary)) { viewModel.signOut(onSignedOut) }
                }
                SettingsPage.GENERAL -> {
                    Choice(stringResource(R.string.settings_homepage), Homepage.entries, settings.homepage, { homepageLabel(it) }) { v -> update { it.copy(homepage = v) } }
                    Choice(stringResource(R.string.settings_theme), ThemeMode.entries, settings.theme, { themeLabel(it) }) { v -> update { it.copy(theme = v) } }
                    Choice(stringResource(R.string.settings_layout), layouts, Styles.layout(context, settings.layout), { styleLabel(it.name, it.credit) }) { v ->
                        update { it.copy(layout = v.id) }
                    }
                    // Wallpaper (Material You) is offered from Android 12; older phones list only the palettes.
                    Choice(stringResource(R.string.settings_palette), palettes, Styles.palette(context, settings.palette), { paletteLabel(it) }) { v ->
                        update { it.copy(palette = v.id) }
                    }
                    if (settings.theme == ThemeMode.DARK_AT_NIGHT) {
                        TimeSetting(stringResource(R.string.settings_night_start), settings.nightStart) { v -> update { it.copy(nightStart = v) } }
                        TimeSetting(stringResource(R.string.settings_night_end), settings.nightEnd) { v -> update { it.copy(nightEnd = v) } }
                    }
                    Choice(stringResource(R.string.settings_text_scale), TextScale.entries, settings.textScale, { scaleLabel(it) }) { v -> update { it.copy(textScale = v) } }
                    Choice(stringResource(R.string.settings_font_size), PostFontSize.entries, settings.postFontSize, { fontLabel(it) }) { v -> update { it.copy(postFontSize = v) } }
                    Toggle(stringResource(R.string.settings_screen_animations), settings.screenAnimations) { v -> update { it.copy(screenAnimations = v) } }
                    Toggle(stringResource(R.string.settings_two_pane), settings.twoPane, stringResource(R.string.settings_two_pane_summary)) { v -> update { it.copy(twoPane = v) } }
                    if (settings.twoPane) {
                        Toggle(stringResource(R.string.settings_two_pane_landscape), settings.twoPaneLandscapeOnly) { v -> update { it.copy(twoPaneLandscapeOnly = v) } }
                    }
                    Toggle(stringResource(R.string.settings_browser_app), settings.browserApp) { v -> update { it.copy(browserApp = v) } }
                }
                SettingsPage.NOTIFICATIONS -> {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        NotificationOptions(
                            settings,
                            onWatchedNotify = { v -> update { it.copy(watchedNotify = v) } },
                            onInterval = { v -> update { it.copy(notifyIntervalMinutes = v) } },
                        )
                    }
                    if (settings.watchedNotify) {
                        Choice(stringResource(R.string.settings_notify_style), NotifyStyle.entries, settings.notifyStyle, { styleLabel(it) }) { v ->
                            update { it.copy(notifyStyle = v) }
                        }
                        if (settings.notifyStyle == NotifyStyle.SUMMARY) {
                            Toggle(stringResource(R.string.settings_notify_keep), settings.notifyKeepUntilRead) { v ->
                                update { it.copy(notifyKeepUntilRead = v) }
                            }
                        }
                    }
                    SettingRow(stringResource(R.string.settings_check_history), stringResource(R.string.settings_check_history_summary), onCheckHistory)
                }
                SettingsPage.THREADS -> {
                    Toggle(stringResource(R.string.settings_open_in_browser), settings.openThreadsInBrowser, stringResource(R.string.settings_open_in_browser_summary)) { v -> update { it.copy(openThreadsInBrowser = v) } }
                    Toggle(stringResource(R.string.settings_public_at_bottom), settings.loadPublicAtBottom) { v -> update { it.copy(loadPublicAtBottom = v) } }
                    Toggle(stringResource(R.string.settings_private_at_bottom), settings.loadPrivateAtBottom) { v -> update { it.copy(loadPrivateAtBottom = v) } }
                    Toggle(stringResource(R.string.settings_read_as_scroll), settings.readThreadsAsScroll, stringResource(R.string.settings_read_as_scroll_summary)) { v -> update { it.copy(readThreadsAsScroll = v) } }
                }
                SettingsPage.RECENT -> {
                    Toggle(stringResource(R.string.settings_recent_at_top), settings.loadRecentAtTop) { v -> update { it.copy(loadRecentAtTop = v) } }
                    Toggle(stringResource(R.string.settings_ignore_own_recent), settings.ignoreOwnRepliesRecent) { v -> update { it.copy(ignoreOwnRepliesRecent = v) } }
                }
                SettingsPage.WATCHED -> {
                    Toggle(stringResource(R.string.settings_watched_at_top), settings.loadWatchedAtTop) { v -> update { it.copy(loadWatchedAtTop = v) } }
                    Toggle(stringResource(R.string.settings_ignore_own_watched), settings.ignoreOwnRepliesWatched) { v -> update { it.copy(ignoreOwnRepliesWatched = v) } }
                    Toggle(stringResource(R.string.settings_auto_mark_read), settings.watchedAutoMarkAsRead) { v -> update { it.copy(watchedAutoMarkAsRead = v) } }
                    Toggle(stringResource(R.string.settings_back_to_list), settings.watchedBackToList) { v -> update { it.copy(watchedBackToList = v) } }
                    Toggle(stringResource(R.string.settings_watch_undo), settings.watchUndo, stringResource(R.string.settings_watch_undo_summary)) { v -> update { it.copy(watchUndo = v) } }
                    Choice(stringResource(R.string.settings_undo_time), Settings.UNDO_SECONDS, settings.undoSeconds, { pluralStringResource(R.plurals.settings_seconds, it, it) }) { v ->
                        update { it.copy(undoSeconds = v) }
                    }
                }
                SettingsPage.BACKUP -> BackupPage()
            }
        }
    }
}

@Composable
private fun BackupPage() {
    val context = LocalContext.current
    fun read() = backupNote(Build.VERSION.SDK_INT, context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
    var note by remember(context) { mutableStateOf(read()) }
    // Read again on return: the user may have just set a screen lock in the phone's settings.
    LifecycleResumeEffect(context) {
        note = read()
        onPauseOrDispose {}
    }
    SettingRow(stringResource(R.string.settings_backup_what), stringResource(R.string.settings_backup_what_summary))
    val (title, summary) = when (note) {
        BackupNote.ALL -> R.string.settings_backup_all to R.string.settings_backup_all_summary
        BackupNote.NOTHING_WITHOUT_LOCK -> R.string.settings_backup_no_lock to R.string.settings_backup_nothing_summary
        BackupNote.NO_KEY_WITHOUT_LOCK -> R.string.settings_backup_no_lock to R.string.settings_backup_no_key_summary
        BackupNote.NO_KEY_ON_THIS_VERSION -> R.string.settings_backup_old_android to R.string.settings_backup_old_android_summary
    }
    SettingRow(stringResource(title), stringResource(summary))
}

private fun pageTitle(page: SettingsPage) = when (page) {
    SettingsPage.ACCOUNT -> R.string.settings_account
    SettingsPage.GENERAL -> R.string.settings_general
    SettingsPage.NOTIFICATIONS -> R.string.settings_notifications
    SettingsPage.THREADS -> R.string.settings_threads
    SettingsPage.RECENT -> R.string.settings_recent
    SettingsPage.WATCHED -> R.string.settings_watched
    SettingsPage.BACKUP -> R.string.settings_backup
}

/** The list's line under each page; null for Account and Notifications, which show their state instead. */
private fun pageSummary(page: SettingsPage): Int? = when (page) {
    SettingsPage.ACCOUNT, SettingsPage.NOTIFICATIONS -> null
    SettingsPage.GENERAL -> R.string.settings_general_summary
    SettingsPage.THREADS -> R.string.settings_threads_summary
    SettingsPage.RECENT -> R.string.settings_recent_summary
    SettingsPage.WATCHED -> R.string.settings_watched_summary
    SettingsPage.BACKUP -> R.string.settings_backup_summary
}

/** A section of settings: a heading and its rows, as one card in styles with [Surfaces.CARDS]. */
@Composable
private fun styleLabel(name: String, credit: String?): String = credit?.let { stringResource(R.string.settings_style_credit, name, it) } ?: name

@Composable
private fun paletteLabel(palette: Palette): String =
    if (palette.isWallpaper) stringResource(R.string.settings_palette_wallpaper) else styleLabel(palette.name, palette.credit)

@Composable
private fun Group(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val layout = LocalAppStyle.current.layout
    if (layout.lists != Surfaces.CARDS) {
        title?.let { Section(it) }
        Column(content = content)
        return
    }
    val shape = RoundedCornerShape(layout.cornerDp)
    title?.let { SectionHeading(it) }
    Column(
        Modifier.padding(horizontal = layout.gutterDp).padding(bottom = 12.dp).clip(shape)
            .background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        content = content,
    )
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(title, Modifier.semantics { heading() }.padding(16.dp, 16.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingRow(title: String, summary: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(16.dp, 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, summary: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(checked, role = Role.Switch) { onChange(!checked) }.padding(16.dp, 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, label(selected)) { open = true }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(option == selected, role = Role.RadioButton) { onSelect(option); open = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == selected, onClick = null)
                            Text(label(option), Modifier.padding(start = 16.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSetting(title: String, time: LocalTime, onSet: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    // The phone's 12/24-hour setting, not the locale's default.
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val pattern = DateFormat.getBestDateTimePattern(LocalConfiguration.current.locales[0], if (is24Hour) "Hm" else "hma")
    SettingRow(title, time.format(DateTimeFormatter.ofPattern(pattern))) { open = true }
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = { onSet(LocalTime.of(state.hour, state.minute)); open = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun homepageLabel(h: Homepage) = stringResource(
    when (h) {
        Homepage.NEWS -> R.string.home_news
        Homepage.RECENT -> R.string.home_recent
        Homepage.LATEST -> R.string.home_latest
        Homepage.WATCHED -> R.string.home_watched
        Homepage.POPULAR -> R.string.home_popular
        Homepage.FORUMS -> R.string.home_forums
    },
)

@Composable
private fun fontLabel(f: PostFontSize) = stringResource(
    when (f) {
        PostFontSize.SMALLEST -> R.string.font_smallest
        PostFontSize.SMALLER -> R.string.font_smaller
        PostFontSize.NORMAL -> R.string.font_normal
        PostFontSize.LARGER -> R.string.font_large
        PostFontSize.LARGE -> R.string.font_larger
        PostFontSize.LARGEST -> R.string.font_largest
    },
)

@Composable
private fun styleLabel(s: NotifyStyle) = stringResource(
    when (s) {
        NotifyStyle.SUMMARY -> R.string.notify_style_summary
        NotifyStyle.PER_THREAD -> R.string.notify_style_per_thread
    },
)

@Composable
private fun scaleLabel(t: TextScale) = stringResource(
    when (t) {
        TextScale.DEFAULT -> R.string.font_normal
        TextScale.LARGE -> R.string.font_large
        TextScale.LARGER -> R.string.font_larger
        TextScale.LARGEST -> R.string.font_largest
    },
)
