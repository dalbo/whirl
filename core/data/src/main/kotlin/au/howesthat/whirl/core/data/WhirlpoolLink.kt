package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.model.ThreadId
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A link from a post or a deep link, classified so Whirlpool pages open in the app and the rest in the browser. */
sealed interface WhirlpoolLink {
    /**
     * `/thread/<id>[?p=N][#rM]`. The site's anchors are `#r<post number>` on page links and `#r<reply id>` on
     * user-filtered links; [anchor] keeps whichever it was (see [isReplyIdAnchor]).
     */
    data class Thread(val id: ThreadId, val page: Int? = null, val anchor: Long? = null) : WhirlpoolLink {
        /** Reply ids are in the tens of millions; post numbers in the largest threads are far below that. */
        val isReplyIdAnchor: Boolean get() = (anchor ?: 0) >= REPLY_ID_MIN
    }
    data class Archive(val id: ThreadId) : WhirlpoolLink
    data class Forum(val id: Int, val groupId: Int? = null) : WhirlpoolLink
    data class User(val id: Long) : WhirlpoolLink
    /** `/forum/?action=replies&r=<reply id>`: redirects to the page holding the post. */
    data class Reply(val replyId: Long) : WhirlpoolLink
    /** `whrl.pl/<code>`: resolve by following the redirect. */
    data class Short(val url: String) : WhirlpoolLink
    data class External(val url: String) : WhirlpoolLink

    companion object {
        internal const val REPLY_ID_MIN = 1_000_000L
        private val FORUM_HOSTS = setOf("forums.whirlpool.net.au", "www.forums.whirlpool.net.au")
        private val ID = Regex("""[A-Za-z0-9]+""")

        fun parse(url: String): WhirlpoolLink {
            val http = url.trim().toHttpUrlOrNull() ?: return External(url)
            if (http.host == "whrl.pl" || http.host == "www.whrl.pl") return Short(http.toString())
            if (http.host !in FORUM_HOSTS) return External(url)
            return parseForums(http) ?: External(url)
        }

        private fun parseForums(url: HttpUrl): WhirlpoolLink? {
            val segments = url.pathSegments.filter(String::isNotEmpty)
            val second = segments.getOrNull(1)
            return when (segments.firstOrNull()) {
                "thread" -> second?.takeIf(ID::matches)?.let {
                    Thread(ThreadId(it), url.queryParameter("p")?.toIntOrNull(), url.fragment?.removePrefix("r")?.toLongOrNull())
                }
                "archive" -> second?.takeIf(ID::matches)?.let { Archive(ThreadId(it)) }
                "user" -> second?.toLongOrNull()?.let(::User)
                "forum" -> when {
                    second != null -> second.toIntOrNull()?.let { Forum(it, url.queryParameter("g")?.toIntOrNull()) }
                    url.queryParameter("action") == "replies" -> url.queryParameter("r")?.toLongOrNull()?.let(::Reply)
                    else -> null
                }
                // Legacy forms that still redirect: forum-replies.cfm?t=<id>, forum-threads.cfm?f=<id>.
                "forum-replies.cfm" -> url.queryParameter("t")?.takeIf(ID::matches)?.let {
                    Thread(ThreadId(it), url.queryParameter("p")?.toIntOrNull(), url.fragment?.removePrefix("r")?.toLongOrNull())
                } ?: url.queryParameter("r")?.toLongOrNull()?.let(::Reply)
                "forum-threads.cfm" -> url.queryParameter("f")?.toIntOrNull()?.let { Forum(it, url.queryParameter("g")?.toIntOrNull()) }
                null -> null
                else -> null
            }
        }
    }
}
