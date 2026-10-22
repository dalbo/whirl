package au.howesthat.whirl.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** True when the "Open pages in the browser app" setting is on; provided at the top of the app. */
val LocalBrowserApp = staticCompositionLocalOf { false }

/**
 * Opens [url] in a Custom Tab (the user's browser, with its Whirlpool login), or in the browser app itself when
 * [browserApp] is set, falling back to any app that handles the link. Used for private forums, posting and "Open in
 * browser".
 */
fun openInBrowser(context: Context, url: String, browserApp: Boolean = false) {
    val uri = url.toUri()
    val view = { pkg: String? -> context.startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    try {
        // Whirl may be the handler for Whirlpool links, so the browser app is named rather than left to Android.
        if (browserApp) view(defaultBrowser(context)) else CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            view(null)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_browser, Toast.LENGTH_SHORT).show()
        }
    }
}

/** The default browser's package, or null when there is none or Android would ask (no default set). */
private fun defaultBrowser(context: Context): String? {
    val probe = Intent(Intent.ACTION_VIEW, "https://example.com/".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val pkg = context.packageManager.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    return pkg?.takeIf { it != "android" && it != context.packageName }
}

/** [openInBrowser] bound to the current context and setting. */
@Composable
fun rememberOpenInBrowser(): (String) -> Unit {
    val context = LocalContext.current
    val browserApp = LocalBrowserApp.current
    return remember(context, browserApp) { { url: String -> openInBrowser(context, url, browserApp) } }
}
