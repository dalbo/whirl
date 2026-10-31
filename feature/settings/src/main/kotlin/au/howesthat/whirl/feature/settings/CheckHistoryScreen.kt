package au.howesthat.whirl.feature.settings

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.notifications.CheckRecord
import au.howesthat.whirl.core.notifications.CheckResult
import au.howesthat.whirl.core.notifications.standbyBucketName
import au.howesthat.whirl.core.ui.SectionHeading
import au.howesthat.whirl.core.ui.whirlTopBarColors
import java.time.Duration
import java.time.ZoneId

/** Settings › Notifications › Check history: the recent background checks and what can hold them back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckHistoryScreen(onBack: () -> Unit, viewModel: CheckHistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.reread()
        onPauseOrDispose {}
    }
    Scaffold(
        topBar = {
            TopAppBar(
                colors = whirlTopBarColors(),
                title = { Text(stringResource(R.string.check_history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    state?.let { s ->
                        IconButton(onClick = { share(context, s) }) { Icon(Icons.Default.Share, stringResource(R.string.check_history_share)) }
                    }
                },
            )
        },
    ) { padding ->
        val s = state ?: return@Scaffold
        val gaps = gaps(s.records)
        LazyColumn(Modifier.padding(padding)) {
            item { Status(s) }
            item {
                OutlinedButton(onClick = { openAppInfo(context) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.check_history_app_info))
                }
                Text(
                    stringResource(R.string.check_history_explain),
                    Modifier.padding(16.dp, 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionHeading(stringResource(R.string.check_history_recent)) }
            if (s.records.isEmpty()) {
                item { Text(stringResource(R.string.check_history_empty), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            items(s.records.zip(gaps)) { (record, gap) -> Record(record, gap) }
        }
    }
}

@Composable
private fun Status(s: CheckHistoryState) {
    val d = s.device
    Column(Modifier.padding(vertical = 8.dp)) {
        Fact(
            stringResource(R.string.check_history_checks),
            if (s.scheduled) stringResource(R.string.check_history_every, s.intervalMinutes) else stringResource(R.string.check_history_not_scheduled),
            warn = !s.scheduled,
        )
        if (s.scheduled) {
            Fact(
                stringResource(R.string.check_history_next),
                s.nextRun?.let { formatTime(LocalContext.current, it.toEpochMilli()) } ?: stringResource(R.string.check_history_waiting_network),
            )
        }
        s.lastStop?.let { Fact(stringResource(R.string.check_history_last_stop), it, warn = true) }
        Fact(
            stringResource(R.string.check_history_notifications),
            stringResource(if (d.notificationsAllowed) R.string.check_history_allowed else R.string.check_history_blocked),
            warn = !d.notificationsAllowed,
        )
        Fact(stringResource(R.string.check_history_channel), stringResource(if (d.channelEnabled) R.string.check_history_on else R.string.check_history_off), warn = !d.channelEnabled)
        Fact(
            stringResource(R.string.check_history_battery),
            stringResource(
                when {
                    d.backgroundRestricted -> R.string.check_history_battery_restricted
                    d.batteryOptimised -> R.string.check_history_battery_optimised
                    else -> R.string.check_history_battery_unrestricted
                },
            ),
            warn = d.backgroundRestricted,
        )
        Fact(stringResource(R.string.check_history_standby), standbyBucketName(d.standbyBucket), warn = isLowBucket(d.standbyBucket))
        Fact(stringResource(R.string.check_history_battery_saver), stringResource(if (d.powerSave) R.string.check_history_on else R.string.check_history_off), warn = d.powerSave)
        Fact(stringResource(R.string.check_history_data_saver), stringResource(if (d.dataSaver) R.string.check_history_on else R.string.check_history_off), warn = d.dataSaver)
    }
}

/** A label and its value; [warn] marks a value that delays or hides notifications. */
@Composable
private fun Fact(label: String, value: String, warn: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f).padding(end = 16.dp), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Record(r: CheckRecord, gap: Duration?) {
    val context = LocalContext.current
    val o = r.outcome
    val result = when (o.result) {
        CheckResult.OK -> stringResource(R.string.check_result_ok, o.unreadThreads ?: 0, o.newThreads ?: 0) +
            if ((o.newThreads ?: 0) > 0) stringResource(R.string.check_result_alerted) else ""
        CheckResult.OFF -> stringResource(R.string.check_result_off)
        CheckResult.BLOCKED -> stringResource(R.string.check_result_blocked)
        CheckResult.NETWORK -> stringResource(R.string.check_result_network)
        CheckResult.MAINTENANCE -> stringResource(R.string.check_result_maintenance)
        CheckResult.RATE_LIMITED -> stringResource(R.string.check_result_rate_limited)
        CheckResult.ERROR -> stringResource(R.string.check_result_error, o.detail.orEmpty())
        CheckResult.STOPPED -> o.detail?.let { stringResource(R.string.check_result_stopped_why, it) } ?: stringResource(R.string.check_result_stopped)
    }
    val conditions = buildList {
        if (r.attempt > 0) add(stringResource(R.string.check_history_retry, r.attempt))
        add(standbyBucketName(r.standbyBucket))
        if (r.powerSave) add(stringResource(R.string.check_history_battery_saver))
        if (r.deviceIdle) add(stringResource(R.string.check_history_idle))
    }.joinToString(" · ")
    val late = gap != null && gap > Duration.ofMinutes(r.intervalMinutes * 3L / 2)
    ListItem(
        headlineContent = {
            Text(formatTime(context, r.startedAt.toEpochMilli()) + (gap?.let { "  +" + shortDuration(it) } ?: ""))
        },
        supportingContent = {
            Column {
                Text(result, color = if (o.result == CheckResult.OK) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                Text(conditions, style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = if (late) {
            { Text(stringResource(R.string.check_history_late), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium) }
        } else {
            null
        },
    )
}

private fun isLowBucket(bucket: Int?) = bucket != null && bucket >= UsageStatsManager.STANDBY_BUCKET_RARE

private fun formatTime(context: Context, millis: Long): String =
    DateUtils.formatDateTime(context, millis, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY or DateUtils.FORMAT_SHOW_TIME)

private fun share(context: Context, state: CheckHistoryState) {
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    val text = checkReport(
        state,
        app = "Whirl $version",
        device = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
        zone = ZoneId.systemDefault(),
    )
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/** App info, which leads to Whirl's battery and notification settings on every Android version. */
private fun openAppInfo(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
}
