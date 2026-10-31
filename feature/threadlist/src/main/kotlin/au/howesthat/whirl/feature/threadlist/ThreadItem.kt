package au.howesthat.whirl.feature.threadlist

import au.howesthat.whirl.core.data.WatchedPosition
import au.howesthat.whirl.core.model.ForumRef
import au.howesthat.whirl.core.model.RowKind
import au.howesthat.whirl.core.model.TextQuery
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadRow
import au.howesthat.whirl.core.model.ThreadSummary
import au.howesthat.whirl.core.model.WatchedThread
import java.time.Instant

/** A thread list row, from any source. */
data class ThreadItem(
    val id: ThreadId,
    /** Where tapping goes: the thread, a moved thread's target, a deleted thread's replacement, or null (not openable). */
    val openId: ThreadId?,
    val title: String,
    val kind: RowKind = RowKind.THREAD,
    /** "moved to Modems/Routers" and the like on moved/deleted rows. */
    val note: String? = null,
    val forum: ForumRef?,
    val group: String? = null,
    val isSticky: Boolean = false,
    val isClosed: Boolean = false,
    /** From the API only; scraped pages always look read without a web session. */
    val isUnread: Boolean = false,
    /** Watched threads' unread post count. */
    val unreadCount: Int = 0,
    val replies: Int? = null,
    val pageCount: Int? = null,
    val lastPoster: String? = null,
    /** Scraped pages give text ("22h", "Yesterday at 9:39 pm"); the API gives an instant. */
    val lastPostedText: String? = null,
    val lastPostedAt: Instant? = null,
    val watched: WatchedPosition? = null,
)

/** A list section: a forum header (null for an ungrouped list) and its rows. */
data class Section(val forum: ForumRef?, val items: List<ThreadItem>)

fun ThreadRow.toItem(pageForum: ForumRef?, unreadIds: Set<ThreadId> = emptySet()) = ThreadItem(
    id = id,
    openId = when (kind) {
        RowKind.THREAD, RowKind.MOVED -> linkId
        RowKind.DELETED -> redirectId
    },
    title = title,
    kind = kind,
    note = note,
    forum = section ?: pageForum,
    group = group?.title,
    isSticky = isSticky,
    isClosed = isClosed,
    isUnread = id in unreadIds,
    replies = replies,
    pageCount = pageCount,
    lastPoster = lastPoster?.name,
    lastPostedText = lastPostedText,
)

fun ThreadSummary.toItem() = ThreadItem(
    id = id,
    openId = id,
    title = title,
    forum = ForumRef(forumId, forumName),
    group = group,
    isUnread = isUnread,
    replies = replies,
    lastPoster = lastPoster.name,
    lastPostedAt = lastDate.toInstant(),
)

fun WatchedThread.toItem() = ThreadItem(
    id = id,
    openId = id,
    title = title,
    forum = ForumRef(forumId, forumName),
    isUnread = unreadCount > 0,
    unreadCount = unreadCount,
    replies = replies,
    // LASTPAGE is the page of the last read post, not the thread's length.
    lastPoster = lastPoster.name,
    lastPostedAt = lastDate.toInstant(),
    watched = WatchedPosition(lastPage, lastReadPost),
)

/** Groups rows under forum headers, forums in order of first appearance (so the most active forum leads). */
fun groupByForum(items: List<ThreadItem>): List<Section> =
    items.groupBy { it.forum?.id }.values.map { rows -> Section(rows.first().forum?.copy(groupId = null), rows) }

/**
 * Find in list: the rows whose title, group or (with [matchForum]) forum match [query], each section keeping its
 * header while any row is left. A forum page leaves its own forum out, since every row would match it.
 */
fun filterSections(sections: List<Section>, query: TextQuery, matchForum: Boolean = true): List<Section> =
    if (query.isBlank) sections
    else sections.map { section ->
        section.copy(items = section.items.filter { query.matches(it.title, it.group, it.forum?.title?.takeIf { matchForum }) })
    }.filter { it.items.isNotEmpty() }

/** Keeps a scraped page's own section headers (Popular, search), or one unheaded section for a forum page. */
fun sectionsOf(items: List<ThreadItem>, sectioned: Boolean): List<Section> =
    if (!sectioned) listOf(Section(null, items))
    else items.fold(mutableListOf<Section>()) { acc, item ->
        val last = acc.lastOrNull()
        if (last != null && last.forum?.id == item.forum?.id) acc[acc.lastIndex] = last.copy(items = last.items + item)
        else acc += Section(item.forum, listOf(item))
        acc
    }
