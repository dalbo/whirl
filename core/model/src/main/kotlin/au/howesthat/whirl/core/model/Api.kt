package au.howesthat.whirl.core.model

import java.time.OffsetDateTime

/** The user the API key belongs to (`AUTH` in every API response). */
data class ApiUser(val id: Long, val name: String)

/** A forum from `get=forum`. */
data class Forum(val id: Int, val title: String, val section: String)

/** An item from `get=news`. Opens at `https://whirlpool.net.au/news/go.cfm?article=<id>`. */
data class NewsItem(val id: Long, val title: String, val source: String, val blurb: String, val date: OffsetDateTime)

/** A thread from `get=threads` (and, assumed, `get=recent`). `isUnread` is the API's boolean flag. */
data class ThreadSummary(
    val id: ThreadId,
    val title: String,
    val replies: Int,
    val forumId: Int,
    val forumName: String,
    /** Sub-forum group name; null when the thread is not in a group. */
    val group: String?,
    val firstDate: OffsetDateTime,
    val firstPoster: UserRef,
    val lastDate: OffsetDateTime,
    val lastPoster: UserRef,
    val isSubscribed: Boolean,
    val isUnread: Boolean,
    val hasParticipated: Boolean,
)

/** A thread from `get=watched`. Open it at [lastPage]; the first unread post is [lastReadPost] + 1. */
data class WatchedThread(
    val id: ThreadId,
    val title: String,
    val replies: Int,
    val forumId: Int,
    val forumName: String,
    val firstDate: OffsetDateTime,
    val firstPoster: UserRef,
    val lastDate: OffsetDateTime,
    val lastPoster: UserRef,
    val unreadCount: Int,
    val lastReadPost: Int,
    val lastPage: Int,
)
