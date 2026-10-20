package au.howesthat.whirl.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import au.howesthat.whirl.core.model.WhirlpoolError
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The message for any failure. API `ERROR`/`ALERT` text is shown verbatim. */
@Composable
fun errorMessage(error: Throwable): String = when (error) {
    is WhirlpoolError.Challenge -> stringResource(R.string.error_challenge)
    is WhirlpoolError.BadKey -> error.message?.takeIf { it.isNotBlank() } ?: stringResource(R.string.error_bad_key)
    is WhirlpoolError.RateLimited -> stringResource(R.string.error_rate_limited)
    is WhirlpoolError.Maintenance -> stringResource(R.string.error_maintenance, error.httpCode)
    is WhirlpoolError.Network -> stringResource(R.string.error_network)
    is WhirlpoolError.NotSignedIn -> stringResource(R.string.error_not_signed_in)
    is WhirlpoolError.Drift -> stringResource(R.string.error_drift)
    is WhirlpoolError.Api -> error.message ?: stringResource(R.string.error_unknown)
    else -> stringResource(R.string.error_unknown)
}

/** Every scraped or fetched screen offers Retry, and Open in browser where there is a page to open. */
@Composable
fun ErrorState(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier, onOpenInBrowser: (() -> Unit)? = null) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(errorMessage(error), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            onOpenInBrowser?.let { OutlinedButton(onClick = it) { Text(stringResource(R.string.open_in_browser)) } }
        }
    }
}

/** "Updated N min ago" under a cached list. */
@Composable
fun UpdatedAgo(updatedAt: Instant?, now: Instant, modifier: Modifier = Modifier) {
    val text = when {
        updatedAt == null -> stringResource(R.string.never_updated)
        else -> {
            val minutes = Duration.between(updatedAt, now).toMinutes().coerceAtLeast(0)
            when {
                minutes < 1 -> stringResource(R.string.updated_just_now)
                minutes < 60 -> stringResource(R.string.updated_minutes, minutes.toInt())
                minutes < 60 * 24 -> stringResource(R.string.updated_hours, (minutes / 60).toInt())
                else -> stringResource(R.string.updated_days, (minutes / (60 * 24)).toInt())
            }
        }
    }
    Text(text, modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** "5 min ago", "3 h ago", "2 d ago", then the date, for an API time. Shown in the device's zone. */
@Composable
fun timeAgo(at: Instant, now: Instant): String {
    val minutes = Duration.between(at, now).toMinutes().coerceAtLeast(0)
    return when {
        minutes < 1 -> stringResource(R.string.ago_just_now)
        minutes < 60 -> stringResource(R.string.ago_minutes, minutes.toInt())
        minutes < 60 * 24 -> stringResource(R.string.ago_hours, (minutes / 60).toInt())
        minutes < 60 * 24 * 7 -> stringResource(R.string.ago_days, (minutes / (60 * 24)).toInt())
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(at.atZone(ZoneId.systemDefault()))
    }
}

/** A full-screen message for an empty list. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}
