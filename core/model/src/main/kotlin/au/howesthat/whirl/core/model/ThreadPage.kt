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
    /** The company a representative posts for (`span.reptag`, e.g. "Leaptel"). */
    val repOf: String? = null,
) {
    val isEdited: Boolean get() = editedText != null
    val isHidden: Boolean get() = hiddenReason != null
    val role: PostRole? get() = PostRole.of(userGroup, repOf)
}

/** Posters the site's "Posts by moderators" and "Posts by reps" views pick out, read from the user group. */
enum class PostRole {
    MODERATOR,
    REP,
    ;

    companion object {
        /** Groups seen in captures (2026-11-08); an unknown group is neither. */
        val MODERATOR_GROUPS = setOf("Moderator", "Section Moderator", "Benevolent Dictator")
        const val REP_GROUP = "ISP Representative"

        fun of(userGroup: String?, repOf: String?): PostRole? = when {
            repOf != null || userGroup == REP_GROUP -> REP
            userGroup in MODERATOR_GROUPS -> MODERATOR
            else -> null
        }
    }
}

/**
 * One `/archive/` page: up to [POSTS_PER_PAGE] posts. [pages] lists every archive page in order (one entry for a thread
 * that fits on one page); [current] is this page's index in it.
 *
 * A multi-part thread lists the pages of every part, and post numbers restart at 1 in each part
 * (`archive_3mnkq4v9_chunks.html`: parts of 3, 3 and 31 pages). Reading, finding and filtering stay within [partPages].
 */
data class ArchivePage(
    val threadId: ThreadId,
    val title: String,
    val forum: ForumRef?,
    val pages: List<ArchivePageRef>,
    val current: Int,
    val posts: List<Post>,
) {
    private val partStart: Int get() = (current downTo 0).first { it == 0 || pages[it].startsPart }
    private val partEnd: Int get() = (current + 1 until pages.size).firstOrNull { pages[it].startsPart } ?: pages.size

    /** The pages of the part this page belongs to; all of [pages] for a thread in one part. */
    val partPages: List<ArchivePageRef> get() = pages.subList(partStart, partEnd)

    /** 1-based number of this page within its part. */
    val pageInPart: Int get() = current - partStart + 1

    /**
     * The page of this part holding post [number]. Exact: every page but the last holds [POSTS_PER_PAGE] numbers, and
     * posts the archive leaves out still use theirs (97q1vrm9 page 2 starts at 801 after two gaps on page 1). Null
     * past the end.
     */
    fun pageFor(number: Int): ArchivePageRef? =
        partPages.getOrNull((number - 1) / POSTS_PER_PAGE)?.takeIf { number >= 1 }

    companion object {
        const val POSTS_PER_PAGE = 800
    }
}

/**
 * An archive page's address and its date range. [path] is used as given: the first page of a part links by numeric
 * id (or bare slug), later ones by `<slug>-N`, and a multi-part thread's pages span several thread ids.
 */
data class ArchivePageRef(val path: String, val label: String?) {
    /** The first page of a part: an address without a `-N` page suffix. */
    val startsPart: Boolean get() = !LATER_PAGE.containsMatchIn(path)

    private companion object {
        val LATER_PAGE = Regex("""-\d+$""")
    }
}
