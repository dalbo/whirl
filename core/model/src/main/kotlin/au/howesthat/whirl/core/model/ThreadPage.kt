package au.howesthat.whirl.core.model

import java.time.LocalDateTime

/** One page of posts in a thread (or the whole thread for an `/archive/` page). */
data class ThreadPage(
    /** The canonical slug, from the breadcrumb (legacy numeric URLs resolve to it). */
    val threadId: ThreadId,
    val title: String,
    val forum: ForumRef?,
    val page: Int,
    val pageCount: Int,
    /** Moderator note shown above the posts (`#top_notebar_public`), as HTML. */
    val moderatorNoteHtml: String?,
    /** Links to every part of a multi-part thread (`.notebar.parts`). */
    val parts: List<ThreadPart>,
    val posts: List<Post>,
)

data class ThreadPart(val number: Int, val threadId: ThreadId, val isCurrent: Boolean)

data class Post(
    val replyId: Long,
    /** 1-based position of the post in the thread. */
    val number: Int,
    val author: UserRef,
    val userGroup: String?,
    val quip: String?,
    val postCount: Int?,
    /** As shown on the page, e.g. `2025-Jun-23, 11:11 am`. Null for a deleted post. */
    val postedText: String?,
    /** [postedText] parsed as Sydney local time. */
    val postedAt: LocalDateTime?,
    val editedText: String?,
    val isOp: Boolean,
    /** Set when a moderator hid the post ("pointless", "personal attack"); the body is then empty. */
    val hiddenReason: String?,
    /** A post with no date and no hidden notice (2.14.12's definition of deleted; not yet seen in a capture). */
    val isDeleted: Boolean,
    val shortUrl: String?,
    /** The post body's inner HTML without the O.P. and "edited" badges; render via `PostBodyParser`. */
    val bodyHtml: String,
) {
    val isEdited: Boolean get() = editedText != null
    val isHidden: Boolean get() = hiddenReason != null
}
