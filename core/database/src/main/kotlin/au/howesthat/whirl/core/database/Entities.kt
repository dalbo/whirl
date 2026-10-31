package au.howesthat.whirl.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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

/**
 * One background watched-thread check, for Settings › Check history (the last few are kept). [result] is a
 * `CheckResult` name; [standbyBucket] is a `UsageStatsManager` bucket, null before Android 9; [network] a
 * `NetworkKind` name. Times are epoch milliseconds. Nothing here identifies the account or its threads.
 */
@Entity(tableName = "check_log")
data class CheckLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val attempt: Int,
    @ColumnInfo(name = "interval_minutes") val intervalMinutes: Int,
    val result: String,
    val detail: String?,
    @ColumnInfo(name = "unread_threads") val unreadThreads: Int?,
    @ColumnInfo(name = "new_threads") val newThreads: Int?,
    @ColumnInfo(name = "standby_bucket") val standbyBucket: Int?,
    @ColumnInfo(name = "power_save") val powerSave: Boolean,
    @ColumnInfo(name = "device_idle") val deviceIdle: Boolean,
    val network: String,
)

/** When each cached list was last fetched, for max-age and refresh throttling. */
@Entity(tableName = "fetch_log")
data class FetchLogEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/**
 * A cached thread page: the page's metadata, with its posts in [PostEntity]. [filter] is "" for all posts, or the
 * post filter the page was fetched with (`u<userid>`, `m`, `r`). Pages are evicted least-recently-opened first.
 */
@Entity(tableName = "thread_pages", primaryKeys = ["thread_id", "filter", "page"])
data class ThreadPageEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    val filter: String,
    val page: Int,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    val title: String,
    @ColumnInfo(name = "forum_id") val forumId: Int?,
    @ColumnInfo(name = "forum_title") val forumTitle: String?,
    @ColumnInfo(name = "group_id") val groupId: Int?,
    @ColumnInfo(name = "moderator_note_html") val moderatorNoteHtml: String?,
    /** Multi-part thread links as `number:threadId:current` joined with `;` (empty when none). */
    val parts: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "accessed_at") val accessedAt: Long,
)

/** A post on a cached [ThreadPageEntity]; deleted with its page. Times are epoch milliseconds. */
@Entity(
    tableName = "posts",
    primaryKeys = ["thread_id", "filter", "page", "reply_id"],
    foreignKeys = [
        ForeignKey(
            entity = ThreadPageEntity::class,
            parentColumns = ["thread_id", "filter", "page"],
            childColumns = ["thread_id", "filter", "page"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PostEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    val filter: String,
    val page: Int,
    @ColumnInfo(name = "reply_id") val replyId: Long,
    @ColumnInfo(name = "reply_num") val replyNum: Int,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "user_name") val userName: String,
    @ColumnInfo(name = "user_group") val userGroup: String?,
    val quip: String?,
    @ColumnInfo(name = "post_count") val postCount: Int?,
    @ColumnInfo(name = "posted_text") val postedText: String?,
    /** [postedText] read as Sydney local time. */
    @ColumnInfo(name = "posted_at") val postedAt: Long?,
    @ColumnInfo(name = "edited_text") val editedText: String?,
    @ColumnInfo(name = "is_op") val isOp: Boolean,
    @ColumnInfo(name = "hidden_reason") val hiddenReason: String?,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "short_url") val shortUrl: String?,
    @ColumnInfo(name = "body_html") val bodyHtml: String,
)
