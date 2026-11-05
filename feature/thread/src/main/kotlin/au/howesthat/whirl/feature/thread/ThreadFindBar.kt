package au.howesthat.whirl.feature.thread

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import au.howesthat.whirl.core.ui.FindBar
import au.howesthat.whirl.core.ui.topBarSubtleColor

/**
 * Find on page, in place of the thread's top bar: the query, previous and next match, and below it the page and
 * which match is showing ([index] is -1 before the first step).
 */
@Composable
internal fun ThreadFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    pageOf: String,
    matchCount: Int,
    index: Int,
    onStep: (Int) -> Unit,
    windowInsets: WindowInsets,
) {
    FindBar(
        query = query,
        onQueryChange = onQueryChange,
        onClose = onClose,
        label = stringResource(R.string.find_on_page),
        matches = null,
        windowInsets = windowInsets,
        actions = {
            IconButton(onClick = { onStep(-1) }, enabled = matchCount > 0) {
                Icon(Icons.Default.KeyboardArrowUp, stringResource(R.string.find_previous))
            }
            IconButton(onClick = { onStep(1) }, enabled = matchCount > 0) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.find_next))
            }
        },
        footer = {
            val status = when {
                query.isBlank() -> null
                matchCount == 0 -> stringResource(R.string.find_none_on_page)
                index < 0 -> pluralStringResource(R.plurals.find_posts, matchCount, matchCount)
                else -> stringResource(R.string.find_post_of, index + 1, matchCount)
            }
            // Announced as the page or the match changes, as the bar's subtitle is otherwise.
            Text(
                listOfNotNull(pageOf, status).joinToString(" · "),
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.labelSmall,
                color = topBarSubtleColor(),
            )
        },
    )
}
