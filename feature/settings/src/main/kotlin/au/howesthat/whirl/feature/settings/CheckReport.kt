package au.howesthat.whirl.feature.settings

import au.howesthat.whirl.core.notifications.CheckRecord
import au.howesthat.whirl.core.notifications.CheckResult
import au.howesthat.whirl.core.notifications.standbyBucketName
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Time since the check before each one (null for the oldest), for [records] newest first. */
fun gaps(records: List<CheckRecord>): List<Duration?> =
    records.mapIndexed { i, r -> records.getOrNull(i + 1)?.let { Duration.between(it.startedAt, r.startedAt) } }

/** "62m", "3h05m". */
fun shortDuration(d: Duration): String {
    val minutes = d.toMinutes()
    return if (minutes < 60) "${minutes}m" else "%dh%02dm".format(Locale.ROOT, minutes / 60, minutes % 60)
}

/**
 * The check history as plain text for a tester to paste into the Whirl thread or an issue. Fixed English and
 * 24-hour times, since it is read by whoever looks into the report, not the person sending it. Nothing in it
 * identifies the account or its threads.
 */
fun checkReport(state: CheckHistoryState, app: String, device: String, zone: ZoneId): String = buildString {
    // English, not ROOT: Android's root locale has no month or day names ("M10").
    val time = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH).withZone(zone)
    val d = state.device
    appendLine("$app check history")
    appendLine(device)
    appendLine(
        if (state.scheduled) {
            "Checks every ${state.intervalMinutes}m, next ${state.nextRun?.let(time::format) ?: "waiting for network"}"
        } else {
            "Checks not scheduled"
        },
    )
    state.lastStop?.let { appendLine("Last stopped by Android: $it") }
    appendLine("Notifications ${if (d.notificationsAllowed) "allowed" else "BLOCKED"}, channel ${if (d.channelEnabled) "on" else "OFF"}")
    appendLine(
        "Battery ${batteryText(d.backgroundRestricted, d.batteryOptimised)}, standby ${standbyBucketName(d.standbyBucket)}, " +
            "battery saver ${onOff(d.powerSave)}, data saver ${onOff(d.dataSaver)}",
    )
    appendLine()
    if (state.records.isEmpty()) appendLine("No checks recorded yet")
    state.records.zip(gaps(state.records)).forEach { (r, gap) ->
        val parts = buildList {
            add(time.format(r.startedAt))
            add(gap?.let { "+" + shortDuration(it) } ?: "-")
            add(outcomeText(r))
            if (r.attempt > 0) add("retry ${r.attempt}")
            if (r.intervalMinutes != state.intervalMinutes) add("interval ${r.intervalMinutes}m")
            add(standbyBucketName(r.standbyBucket))
            add(r.network.name.lowercase(Locale.ROOT))
            if (r.powerSave) add("battery saver")
            if (r.deviceIdle) add("idle")
            add("%.1fs".format(Locale.ROOT, r.durationMs / 1000.0))
        }
        appendLine(parts.joinToString("  "))
    }
}.trimEnd()

private fun outcomeText(r: CheckRecord): String {
    val o = r.outcome
    val base = o.result.name.lowercase(Locale.ROOT)
    return when (o.result) {
        CheckResult.OK -> "ok ${o.unreadThreads ?: 0} unread ${o.newThreads ?: 0} new" + if ((o.newThreads ?: 0) > 0) " (alerted)" else ""
        else -> o.detail?.let { "$base ($it)" } ?: base
    }
}

private fun batteryText(restricted: Boolean, optimised: Boolean) = when {
    restricted -> "RESTRICTED"
    optimised -> "optimised"
    else -> "unrestricted"
}

private fun onOff(on: Boolean) = if (on) "ON" else "off"
