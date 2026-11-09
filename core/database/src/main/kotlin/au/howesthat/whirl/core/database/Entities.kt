package au.howesthat.whirl.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import au.howesthat.whirl.core.model.WatchAction

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
)

/** Which list shows which thread, in what order. [key] narrows the [source] (a forum, Unread or All). */
@Entity(
    tableName = "thread_source",
    primaryKeys = ["source", "key", "thread_id"],
    indices = [Index("thread_id")],
)
data class ThreadSourceEntity(
    val source: ThreadSource,
    val key: String,
    @ColumnInfo(name = "thread_id") val threadId: String,
    val position: Int,
)

/**
 * The API lists cached in Room, stored by name. Scraped lists (forum pages, Popular, search) are kept in memory only
 * (`ScrapedListRepository`).
 */
enum class ThreadSource { FORUM, WATCHED, RECENT, LATEST }

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

/**
 * A watch action applied on this device and not yet sent to Whirlpool, so it survives the app closing (v1.6). Rows are
 * sent in [id] order. Times are epoch milliseconds.
 */
@Entity(tableName = "pending_watch")
@TypeConverters(ThreadIdsConverter::class)
data class PendingWatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: WatchAction,
    @ColumnInfo(name = "thread_ids") val ids: List<String>,
    @ColumnInfo(name = "send_after") val sendAfter: Long,
    val state: PendingState = PendingState.WAITING,
    val attempts: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

/**
 * `pending_watch.thread_ids`: the ids joined with commas. [ThreadDao.deleteOrphans] matches ids in it with `LIKE`, so
 * the format must stay as it is. Thread ids are alphanumeric, so a comma never occurs in one.
 */
object ThreadIdsConverter {
    @TypeConverter
    fun join(ids: List<String>): String = ids.joinToString(",")

    @TypeConverter
    fun split(joined: String): List<String> = joined.split(',').filter { it.isNotEmpty() }
}

enum class PendingState { WAITING, SENDING }

/** When each cached list was last fetched, for max-age and refresh throttling. */
@Entity(tableName = "fetch_log")
data class FetchLogEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)

/** A cached thread page: the page's metadata, with its posts in [PostEntity]. Evicted least-recently-opened first. */
@Entity(tableName = "thread_pages", primaryKeys = ["thread_id", "page"])
data class ThreadPageEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
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
    primaryKeys = ["thread_id", "page", "reply_id"],
    foreignKeys = [
        ForeignKey(
            entity = ThreadPageEntity::class,
            parentColumns = ["thread_id", "page"],
            childColumns = ["thread_id", "page"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PostEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
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
    /** The company a representative posts for (v7). */
    @ColumnInfo(name = "rep_of") val repOf: String? = null,
)

/**
 * A thread's archive page list as last seen (v7): every archive page, in order, as `path|label` joined with `\n`
 * (a label never holds a newline). Also counts the earlier pages searched today, for the daily cap.
 */
@Entity(tableName = "archive_threads")
data class ArchiveThreadEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: String,
    val title: String,
    @ColumnInfo(name = "forum_id") val forumId: Int?,
    @ColumnInfo(name = "forum_title") val forumTitle: String?,
    @ColumnInfo(name = "group_id") val groupId: Int?,
    val pages: String,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    /** The Sydney day ([java.time.LocalDate.toEpochDay]) [searchedToday] counts for. */
    @ColumnInfo(name = "searched_day") val searchedDay: Long = 0,
    @ColumnInfo(name = "searched_today") val searchedToday: Int = 0,
)

/**
 * A cached archive page whose posts are in [ArchivePostEntity] (v7). [path] is the page's address as listed;
 * [first]/[last] are the post numbers it holds. Evicted by count, least recently opened first.
 */
@Entity(tableName = "archive_pages", primaryKeys = ["thread_id", "path"])
data class ArchivePageEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    val path: String,
    val first: Int,
    val last: Int,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
    @ColumnInfo(name = "accessed_at") val accessedAt: Long,
)

/** A post on a cached [ArchivePageEntity], with its body (v7); deleted with its page. */
@Entity(
    tableName = "archive_posts",
    primaryKeys = ["thread_id", "reply_id"],
    indices = [Index("thread_id", "path"), Index("thread_id", "reply_num")],
    foreignKeys = [
        ForeignKey(
            entity = ArchivePageEntity::class,
            parentColumns = ["thread_id", "path"],
            childColumns = ["thread_id", "path"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ArchivePostEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    val path: String,
    @ColumnInfo(name = "reply_id") val replyId: Long,
    @ColumnInfo(name = "reply_num") val replyNum: Int,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "user_name") val userName: String,
    @ColumnInfo(name = "user_group") val userGroup: String?,
    val quip: String?,
    @ColumnInfo(name = "post_count") val postCount: Int?,
    @ColumnInfo(name = "posted_text") val postedText: String?,
    @ColumnInfo(name = "posted_at") val postedAt: Long?,
    @ColumnInfo(name = "edited_text") val editedText: String?,
    @ColumnInfo(name = "is_op") val isOp: Boolean,
    @ColumnInfo(name = "is_deleted") val isDeleted: Boolean,
    @ColumnInfo(name = "short_url") val shortUrl: String?,
    @ColumnInfo(name = "body_html") val bodyHtml: String,
    @ColumnInfo(name = "rep_of") val repOf: String?,
)

/**
 * Who wrote each post of every archive page ever fetched (v7), without bodies: small, kept when bodies are evicted,
 * so filters ("posts by", moderators, reps) are a query and never refetch a page.
 */
@Entity(
    tableName = "archive_index",
    primaryKeys = ["thread_id", "reply_id"],
    indices = [Index("thread_id", "user_id"), Index("thread_id", "path")],
)
data class ArchiveIndexEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "reply_id") val replyId: Long,
    val path: String,
    @ColumnInfo(name = "reply_num") val replyNum: Int,
    @ColumnInfo(name = "user_id") val userId: Long,
    @ColumnInfo(name = "user_name") val userName: String,
    @ColumnInfo(name = "user_group") val userGroup: String?,
    @ColumnInfo(name = "rep_of") val repOf: String?,
    @ColumnInfo(name = "is_op") val isOp: Boolean,
)
