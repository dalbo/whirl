package au.howesthat.whirl.feature.thread

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * A post's text for copying. Posts are not selectable in place (a long press opens the post's menu), so the menu offers
 * Copy text, and Select text to copy part of it from this dialog.
 */
internal fun copyPostText(context: Context, author: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.post_text_clip, author), text))
    // Android 13 and later show their own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, R.string.post_text_copied, Toast.LENGTH_SHORT).show()
}

@Composable
internal fun SelectTextDialog(author: String, text: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.post_select_text_title, author)) },
        text = {
            SelectionContainer(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.post_select_text_done)) } },
        dismissButton = {
            TextButton(onClick = {
                copyPostText(context, author, text)
                onDismiss()
            }) { Text(stringResource(R.string.post_copy_all)) }
        },
    )
}
