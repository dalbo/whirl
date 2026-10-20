package au.howesthat.whirl.core.model

/** One page of a scraped thread table: a forum page, Popular, search list view, or a user's recent activity. */
data class ThreadListPage(
    /** The forum this page belongs to; null for cross-forum lists (popular, search, user activity). */
    val forum: ForumRef?,
    val page: Int,
    val pageCount: Int,
    /** Groups offered by the forum's group selector (empty when the forum has none). */
    val groups: List<ForumGroup>,
    /** Forum section headers, in page order, for cross-forum lists. */
    val sections: List<ForumRef>,
    val rows: List<ThreadRow>,
)

enum class RowKind { THREAD, MOVED, DELETED }

data class ThreadRow(
    /** The id this row is listed under (from `tr#tr<id>`). */
    val id: ThreadId,
    val kind: RowKind,
    val title: String,
    /** Where the title links. For moved/deleted rows this can be a different thread. */
    val linkId: ThreadId,
    /** "moved to Modems/Routers", "use existing thread" on moved/deleted rows. */
    val note: String? = null,
    /** For deleted rows pointing at an existing thread, that thread. */
    val redirectId: ThreadId? = null,
    val isSticky: Boolean = false,
    val isClosed: Boolean = false,
    /** Server-rendered page count; 1 when the row has no page span. */
    val pageCount: Int = 1,
    val replies: Int? = null,
    val views: Int? = null,
    val firstPoster: UserRef? = null,
    val firstPostedText: String? = null,
    val lastPoster: UserRef? = null,
    val lastPostedText: String? = null,
    /** The section header this row sits under on cross-forum lists. */
    val section: ForumRef? = null,
    /** The group shown before the title on cross-forum lists (`a.group`). */
    val group: ForumRef? = null,
)
