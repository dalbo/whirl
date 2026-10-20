package au.howesthat.whirl.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import au.howesthat.whirl.BuildConfig
import au.howesthat.whirl.R
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.rememberOpenInBrowser

const val REPO_URL = "https://github.com/dalbo/whirl"
const val ISSUES_URL = "$REPO_URL/issues"
private const val WHIRLDROID_THREAD_URL = "https://forums.whirlpool.net.au/thread/97q1vrm9"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navIcon: NavIcon) {
    val open = rememberOpenInBrowser()
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_about)) }, navigationIcon = { NavIconButton(navIcon) }) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_summary), style = MaterialTheme.typography.bodyMedium)
            }
            Link(stringResource(R.string.about_source), REPO_URL) { open(REPO_URL) }
            Link(stringResource(R.string.about_issues), ISSUES_URL) { open(ISSUES_URL) }
            Link(stringResource(R.string.about_whirldroid), stringResource(R.string.about_whirldroid_summary)) { open(WHIRLDROID_THREAD_URL) }
            Text(
                stringResource(R.string.about_disclaimer),
                Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Link(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
