package au.howesthat.whirl.about

import android.content.ActivityNotFoundException
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.compose.foundation.clickable
import au.howesthat.whirl.BuildConfig
import au.howesthat.whirl.R
import au.howesthat.whirl.core.ui.NavIcon
import au.howesthat.whirl.core.ui.NavIconButton
import au.howesthat.whirl.core.ui.rememberOpenInBrowser
import au.howesthat.whirl.core.ui.whirlTopBarColors

const val REPO_URL = "https://github.com/dalbo/whirl"
const val ISSUES_URL = "$REPO_URL/issues"
private const val WHIRLDROID_THREAD_URL = "https://forums.whirlpool.net.au/thread/97q1vrm9"
/** Whirl's own thread on Whirlpool (Android › Apps), opened in the app. */
const val WHIRL_THREAD_ID = "9244p8v1"
/** The listing's address never changes; until Whirl leaves closed testing only testers can see it. */
private const val PLAY_PACKAGE = "au.howesthat.whirl"
private const val PLAY_URL = "https://play.google.com/store/apps/details?id=$PLAY_PACKAGE"
private const val CONTACT_EMAIL = "whirl@howesthat.net.au"
/** Google Play's Child Safety Standards (Social apps): published standards and an in-app way to report. */
private const val CHILD_SAFETY_URL = "https://dalbo.github.io/whirl/child-safety/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navIcon: NavIcon, onOpenThread: (String) -> Unit) {
    val open = rememberOpenInBrowser()
    val context = LocalContext.current
    val subject = stringResource(R.string.about_concern_subject)
    Scaffold(
        topBar = { TopAppBar(colors = whirlTopBarColors(), title = { Text(stringResource(R.string.nav_about)) }, navigationIcon = { NavIconButton(navIcon) }) },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.about_summary), style = MaterialTheme.typography.bodyMedium)
            }
            Link(stringResource(R.string.about_thread), stringResource(R.string.about_thread_summary)) { onOpenThread(WHIRL_THREAD_ID) }
            Link(stringResource(R.string.about_play), stringResource(R.string.about_play_summary)) {
                // The Play Store app if there is one, else its web page.
                val market = Intent(Intent.ACTION_VIEW, "market://details?id=$PLAY_PACKAGE".toUri()).setPackage("com.android.vending")
                try {
                    context.startActivity(market)
                } catch (_: ActivityNotFoundException) {
                    open(PLAY_URL)
                }
            }
            Link(stringResource(R.string.about_source), REPO_URL) { open(REPO_URL) }
            Link(stringResource(R.string.about_issues), ISSUES_URL) { open(ISSUES_URL) }
            Link(stringResource(R.string.about_concern), stringResource(R.string.about_concern_summary, CONTACT_EMAIL)) {
                val email = Intent(Intent.ACTION_SENDTO, "mailto:$CONTACT_EMAIL".toUri()).putExtra(Intent.EXTRA_SUBJECT, subject)
                // Without an email app, the standards page shows the address and the other ways to report.
                try {
                    context.startActivity(email)
                } catch (_: ActivityNotFoundException) {
                    open(CHILD_SAFETY_URL)
                }
            }
            Link(stringResource(R.string.about_child_safety), CHILD_SAFETY_URL) { open(CHILD_SAFETY_URL) }
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
