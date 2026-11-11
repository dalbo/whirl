package au.howesthat.whirl

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** The top edge the key banner pads for, which the screens under it then leave alone. */
internal val BannerTopInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)

/**
 * Over every screen while Whirlpool turns down the stored key. Never signs out by itself: the key may be fine and the
 * reply a passing fault, and signing out would also drop it on the phone it came from.
 */
@Composable
internal fun KeyRejectedBanner(onNewKey: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.key_rejected),
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onNewKey,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) { Text(stringResource(R.string.key_rejected_new_key)) }
        }
    }
}

/**
 * Asks once for the notification permission (Android 13+) when watched-thread alerts are on without it. Setup is the
 * only other place that asks, and a restored key skips setup; Android does not restore the grant.
 */
@Composable
internal fun NotificationPermissionPrompt(onAsked: () -> Unit) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onAsked() }
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    AlertDialog(
        onDismissRequest = onAsked,
        title = { Text(stringResource(R.string.notify_prompt_title)) },
        text = { Text(stringResource(R.string.notify_prompt_body)) },
        confirmButton = { TextButton(onClick = { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(stringResource(R.string.notify_prompt_allow)) } },
        dismissButton = { TextButton(onClick = onAsked) { Text(stringResource(R.string.notify_prompt_later)) } },
    )
}
