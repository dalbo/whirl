package au.howesthat.whirl.feature.settings

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
fun SettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val layouts = remember(context) { Styles.layouts(context) }
    val palettes = remember(context) { Styles.palettes(context) }
    val account by viewModel.account.collectAsStateWithLifecycle()
    val update = viewModel::update

    Scaffold(
        topBar = {
            TopAppBar(
                colors = whirlTopBarColors(),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Group(stringResource(R.string.settings_account)) {
                account?.let { SettingRow(stringResource(R.string.settings_logged_in_as, it.userName, it.userId)) }
                SettingRow(stringResource(R.string.settings_sign_out), stringResource(R.string.settings_sign_out_summary)) { viewModel.signOut(onSignedOut) }
            }

            Group(stringResource(R.string.settings_general)) {
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
                Toggle(stringResource(R.string.settings_browser_app), settings.browserApp) { v -> update { it.copy(browserApp = v) } }
            }

            Group(stringResource(R.string.settings_notifications)) {
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
            }

            Group(stringResource(R.string.settings_threads)) {
                Toggle(stringResource(R.string.settings_open_in_browser), settings.openThreadsInBrowser, stringResource(R.string.settings_open_in_browser_summary)) { v -> update { it.copy(openThreadsInBrowser = v) } }
                Toggle(stringResource(R.string.settings_public_at_bottom), settings.loadPublicAtBottom) { v -> update { it.copy(loadPublicAtBottom = v) } }
                Toggle(stringResource(R.string.settings_private_at_bottom), settings.loadPrivateAtBottom) { v -> update { it.copy(loadPrivateAtBottom = v) } }
            }

            Group(stringResource(R.string.settings_recent)) {
                Toggle(stringResource(R.string.settings_recent_at_top), settings.loadRecentAtTop) { v -> update { it.copy(loadRecentAtTop = v) } }
                Toggle(stringResource(R.string.settings_ignore_own_recent), settings.ignoreOwnRepliesRecent) { v -> update { it.copy(ignoreOwnRepliesRecent = v) } }
            }

            Group(stringResource(R.string.settings_watched)) {
                Toggle(stringResource(R.string.settings_watched_at_top), settings.loadWatchedAtTop) { v -> update { it.copy(loadWatchedAtTop = v) } }
                Toggle(stringResource(R.string.settings_ignore_own_watched), settings.ignoreOwnRepliesWatched) { v -> update { it.copy(ignoreOwnRepliesWatched = v) } }
                Toggle(stringResource(R.string.settings_auto_mark_read), settings.watchedAutoMarkAsRead) { v -> update { it.copy(watchedAutoMarkAsRead = v) } }
                Toggle(stringResource(R.string.settings_back_to_list), settings.watchedBackToList) { v -> update { it.copy(watchedBackToList = v) } }
            }
        }
    }
}

/** A section of settings: a heading and its rows, as one card in styles with [Surfaces.CARDS]. */
@Composable
private fun styleLabel(name: String, credit: String?): String = credit?.let { stringResource(R.string.settings_style_credit, name, it) } ?: name

@Composable
private fun paletteLabel(palette: Palette): String =
    if (palette.isWallpaper) stringResource(R.string.settings_palette_wallpaper) else styleLabel(palette.name, palette.credit)

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    val layout = LocalAppStyle.current.layout
    if (layout.lists != Surfaces.CARDS) {
        Section(title)
        Column(content = content)
        return
    }
    val shape = RoundedCornerShape(layout.cornerDp)
    SectionHeading(title)
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
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp, 12.dp),
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
