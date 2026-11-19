package au.howesthat.whirl.feature.settings

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import au.howesthat.whirl.core.data.Homepage
import au.howesthat.whirl.core.data.NotifyStyle
import au.howesthat.whirl.core.data.PostFontSize
import au.howesthat.whirl.core.data.SettingChange
import au.howesthat.whirl.core.data.TextScale
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.ui.Styles
import au.howesthat.whirl.feature.setup.themeLabel
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import au.howesthat.whirl.feature.setup.R as SetupR

/** A setting's place in Settings: its page's title and its own, as an import's preview names it. */
internal data class SettingLabel(val page: Int, val title: Int)

/** Every stored setting name ([au.howesthat.whirl.core.data.SETTING_KEYS]; a test checks they match). */
internal val SETTING_LABELS: Map<String, SettingLabel> = mapOf(
    "homepage" to SettingLabel(R.string.settings_general, R.string.settings_homepage),
    "theme" to SettingLabel(R.string.settings_general, R.string.settings_theme),
    "palette" to SettingLabel(R.string.settings_general, R.string.settings_palette),
    "layout" to SettingLabel(R.string.settings_general, R.string.settings_layout),
    "night_start" to SettingLabel(R.string.settings_general, R.string.settings_night_start),
    "night_end" to SettingLabel(R.string.settings_general, R.string.settings_night_end),
    "post_font_size" to SettingLabel(R.string.settings_general, R.string.settings_font_size),
    "text_scale" to SettingLabel(R.string.settings_general, R.string.settings_text_scale),
    "screen_animations" to SettingLabel(R.string.settings_general, R.string.settings_screen_animations),
    "two_pane" to SettingLabel(R.string.settings_general, R.string.settings_two_pane),
    "two_pane_landscape_only" to SettingLabel(R.string.settings_general, R.string.settings_two_pane_landscape),
    "browser_app" to SettingLabel(R.string.settings_general, R.string.settings_browser_app),
    "watched_notify" to SettingLabel(R.string.settings_notifications, SetupR.string.setup_notify_watched),
    "notify_interval" to SettingLabel(R.string.settings_notifications, SetupR.string.setup_notify_interval),
    "notify_style" to SettingLabel(R.string.settings_notifications, R.string.settings_notify_style),
    "notify_keep_until_read" to SettingLabel(R.string.settings_notifications, R.string.settings_notify_keep),
    "open_threads_in_browser" to SettingLabel(R.string.settings_threads, R.string.settings_open_in_browser),
    "load_public_at_bottom" to SettingLabel(R.string.settings_threads, R.string.settings_public_at_bottom),
    "load_private_at_bottom" to SettingLabel(R.string.settings_threads, R.string.settings_private_at_bottom),
    "read_threads_as_scroll" to SettingLabel(R.string.settings_threads, R.string.settings_read_as_scroll),
    "recent_days" to SettingLabel(R.string.settings_recent, R.string.backup_recent_days),
    "load_recent_at_top" to SettingLabel(R.string.settings_recent, R.string.settings_recent_at_top),
    "ignore_own_replies_recent" to SettingLabel(R.string.settings_recent, R.string.settings_ignore_own_recent),
    "load_watched_at_top" to SettingLabel(R.string.settings_watched, R.string.settings_watched_at_top),
    "ignore_own_replies_watched" to SettingLabel(R.string.settings_watched, R.string.settings_ignore_own_watched),
    "watched_auto_mark_read" to SettingLabel(R.string.settings_watched, R.string.settings_auto_mark_read),
    "watched_back_to_list" to SettingLabel(R.string.settings_watched, R.string.settings_back_to_list),
    "watch_undo" to SettingLabel(R.string.settings_watched, R.string.settings_watch_undo),
    "undo_seconds" to SettingLabel(R.string.settings_watched, R.string.settings_undo_time),
)

/** "General › Theme: System → Dark". */
@Composable
internal fun changeLine(change: SettingChange): String =
    stringResource(R.string.backup_change, settingName(change.key), valueLabel(change.key, change.from), valueLabel(change.key, change.to))

/** "General › Theme", or the stored name for one with no label (a test keeps that from happening). */
@Composable
internal fun settingName(key: String): String =
    SETTING_LABELS[key]?.let { stringResource(R.string.backup_setting_name, stringResource(it.page), stringResource(it.title)) } ?: key

@Composable
private fun valueLabel(key: String, value: Any): String {
    val context = LocalContext.current
    return when {
        key == "notify_interval" && value is Int -> pluralStringResource(R.plurals.backup_minutes, value, value)
        key == "recent_days" && value is Int -> pluralStringResource(R.plurals.backup_days, value, value)
        key == "undo_seconds" && value is Int -> pluralStringResource(R.plurals.settings_seconds, value, value)
        key == "palette" && value is String -> paletteLabel(Styles.palette(context, value))
        key == "layout" && value is String -> Styles.layout(context, value).let { styleLabel(it.name, it.credit) }
        value is Boolean -> stringResource(if (value) R.string.backup_on else R.string.backup_off)
        value is Homepage -> homepageLabel(value)
        value is ThemeMode -> themeLabel(value)
        value is PostFontSize -> fontLabel(value)
        value is TextScale -> scaleLabel(value)
        value is NotifyStyle -> styleLabel(value)
        value is LocalTime -> timeLabel(value)
        else -> value.toString()
    }
}

/** In the phone's 12/24-hour setting, as the time pickers show it. */
@Composable
internal fun timeLabel(time: LocalTime): String {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val pattern = DateFormat.getBestDateTimePattern(LocalConfiguration.current.locales[0], if (is24Hour) "Hm" else "hma")
    return time.format(DateTimeFormatter.ofPattern(pattern))
}
