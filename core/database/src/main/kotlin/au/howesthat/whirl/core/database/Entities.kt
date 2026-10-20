package au.howesthat.whirl.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A forum from `get=forum`, in API order. [isPrivate] is learned when a forum page says "Not signed in". */
@Entity(tableName = "forums")
data class ForumEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val section: String,
    val sort: Int,
    @ColumnInfo(name = "is_private", defaultValue = "0") val isPrivate: Boolean = false,
)

/** Kept apart from [ForumEntity] so refreshing the forum list never loses favourites. */
@Entity(tableName = "favourite_forums")
data class FavouriteForumEntity(
    @PrimaryKey @ColumnInfo(name = "forum_id") val forumId: Int,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

/**
 * One row per thread, merged from every source. Each source writes only the columns it knows (partial upserts in
 * [ThreadDao]); dates are epoch milliseconds. Thread ids are strings.
 */
@Entity(tableName = "threads", indices = [Index("forum_id")])
data class ThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    @ColumnInfo(name = "forum_id", defaultValue = "0") val forumId: Int = 0,
    @ColumnInfo(name = "forum_name", defaultValue = "") val forumName: String = "",
    @ColumnInfo(name = "group_name") val groupName: String? = null,
    @ColumnInfo(defaultValue = "0") val replies: Int = 0,
    @ColumnInfo(name = "page_count") val pageCount: Int? = null,
    @ColumnInfo(name = "first_user_id") val firstUserId: Long? = null,
    @ColumnInfo(name = "first_user_name") val firstUserName: String? = null,
    @ColumnInfo(name = "first_date") val firstDate: Long? = null,
    @ColumnInfo(name = "last_user_id") val lastUserId: Long? = null,
    @ColumnInfo(name = "last_user_name") val lastUserName: String? = null,
    @ColumnInfo(name = "last_date") val lastDate: Long? = null,
    /** `THREADS.UNREAD`. */
    @ColumnInfo(name = "is_unread") val isUnread: Boolean? = null,
    /** `WATCHED.UNREAD`, `LASTREAD`, `LASTPAGE`. Non-null only for watched threads. */
    @ColumnInfo(name = "unread_count") val unreadCount: Int? = null,
    @ColumnInfo(name = "last_read_post") val lastReadPost: Int? = null,
    @ColumnInfo(name = "last_page") val lastPage: Int? = null,
    @ColumnInfo(name = "is_sticky", defaultValue = "0") val isSticky: Boolean = false,
    @ColumnInfo(name = "is_closed", defaultValue = "0") val isClosed: Boolean = false,
)

/** Which list shows which thread, in what order. [source] is a [ThreadSource] name; [key] narrows it (forum page, mode). */
@Entity(
    tableName = "thread_source",
    primaryKeys = ["source", "key", "thread_id"],
    indices = [Index("thread_id")],
)
data class ThreadSourceEntity(
    val source: String,
    val key: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    val position: Int,
)

enum class ThreadSource { FORUM, WATCHED, RECENT, POPULAR, SEARCH, LATEST }

@Entity(tableName = "news")
data class NewsEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val source: String,
    val blurb: String,
    val date: Long,
    /** The API's offset in seconds, so the original wall-clock time can be shown. */
    @ColumnInfo(name = "offset_seconds") val offsetSeconds: Int,
)

/** What a watched-thread notification last announced, to notify each new reply once (Phase 3). */
@Entity(tableName = "notified")
data class NotifiedEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "last_date") val lastDate: Long,
)

/** When each cached list was last fetched, for max-age and refresh throttling. */
@Entity(tableName = "fetch_log")
data class FetchLogEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
