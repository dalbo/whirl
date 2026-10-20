package au.howesthat.whirl.navigation

import android.net.Uri
import au.howesthat.whirl.core.data.WhirlpoolLink
import au.howesthat.whirl.feature.thread.ThreadRoute
import au.howesthat.whirl.feature.threadlist.ForumRoute
import au.howesthat.whirl.feature.user.UserRoute

/**
 * The route for a link the app was opened with: `https://forums.whirlpool.net.au/thread|archive|forum|user/...`
 * (see the manifest's intent filters) or Whirldroid's old `whirldroid-thread://com.gregdev.whirldroid?threadid=<id>`.
 * Null for anything else.
 */
fun routeForLink(uri: Uri): Any? {
    if (uri.scheme == LEGACY_SCHEME) {
        val id = uri.getQueryParameter("threadid")?.takeIf { it.isNotEmpty() && it.all(Char::isLetterOrDigit) } ?: return null
        return ThreadRoute(id)
    }
    return routeForLink(uri.toString())
}

fun routeForLink(url: String): Any? = when (val link = WhirlpoolLink.parse(url)) {
    is WhirlpoolLink.Thread -> ThreadRoute(
        link.id.value,
        page = link.page ?: 1,
        scrollToPost = link.anchor?.takeIf { !link.isReplyIdAnchor }?.toInt(),
        scrollToReply = link.anchor?.takeIf { link.isReplyIdAnchor },
    )
    is WhirlpoolLink.Archive -> ThreadRoute(link.id.value)
    // The forum screen falls back to the title on the page.
    is WhirlpoolLink.Forum -> ForumRoute(link.id, title = "", groupId = link.groupId)
    is WhirlpoolLink.User -> UserRoute(link.id)
    // The manifest does not claim these; opening them here would bounce straight back to the browser.
    is WhirlpoolLink.Reply, is WhirlpoolLink.Short, is WhirlpoolLink.External -> null
}

private const val LEGACY_SCHEME = "whirldroid-thread"
