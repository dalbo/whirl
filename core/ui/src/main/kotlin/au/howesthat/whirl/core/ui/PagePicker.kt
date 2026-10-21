package au.howesthat.whirl.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt

/** "Go to page" for forums and threads: a slider, since both run to hundreds or thousands of pages. */
@Composable
fun PagePickerDialog(pageCount: Int, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    val page = value.roundToInt().coerceIn(1, pageCount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.go_to_page)) },
        text = {
            Column {
                Text(stringResource(R.string.page_of, page, pageCount), style = MaterialTheme.typography.titleMedium)
                // Discrete steps only while they stay usable; beyond that the slider is continuous and rounds.
                val label = stringResource(R.string.page_of, page, pageCount)
                Slider(
                    value = value,
                    onValueChange = { value = it },
                    // TalkBack reads "Page N of M" rather than a percentage.
                    modifier = Modifier.semantics { stateDescription = label },
                    valueRange = 1f..pageCount.toFloat(),
                    steps = if (pageCount <= 100) pageCount - 2 else 0,
                )
                Row {
                    TextButton(onClick = { onPick(1) }) { Text(stringResource(R.string.first_page)) }
                    TextButton(onClick = { onPick(pageCount) }) { Text(stringResource(R.string.last_page)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(page) }) { Text(stringResource(R.string.go)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
