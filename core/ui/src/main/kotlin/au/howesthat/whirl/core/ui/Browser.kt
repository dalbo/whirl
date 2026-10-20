package au.howesthat.whirl.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/**
 * Opens [url] in a Custom Tab (the user's browser, with its Whirlpool login), falling back to any app that handles
 * the link. Used for private forums, posting and "Open in browser".
 */
fun openInBrowser(context: Context, url: String) {
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}

/** [openInBrowser] bound to the current context. */
@Composable
fun rememberOpenInBrowser(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { url: String -> openInBrowser(context, url) } }
}
