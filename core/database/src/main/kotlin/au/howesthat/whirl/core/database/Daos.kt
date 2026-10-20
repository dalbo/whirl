package au.howesthat.whirl.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class ForumWithFavourite(
    val id: Int,
    val title: String,
    val section: String,
    val sort: Int,
    @ColumnInfo(name = "is_private") val isPrivate: Boolean,
    @ColumnInfo(name = "is_favourite") val isFavourite: Boolean,
)

@Dao
interface ForumDao {
    @Query(
        """SELECT f.id, f.title, f.section, f.sort, f.is_private, (fav.forum_id IS NOT NULL) AS is_favourite
           FROM forums f LEFT JOIN favourite_forums fav ON fav.forum_id = f.id ORDER BY f.sort""",
    )
    fun observeAll(): Flow<List<ForumWithFavourite>>

    @Query("SELECT id FROM forums WHERE is_private = 1")
    suspend fun privateIds(): List<Int>

    @Query("DELETE FROM forums")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(forums: List<ForumEntity>)

    /** Replaces the forum list, keeping what has been learned about private forums. */
    @Transaction
    suspend fun replaceAll(forums: List<ForumEntity>) {
        val private = privateIds().toSet()
        deleteAll()
        insertAll(forums.map { if (it.id in private) it.copy(isPrivate = true) else it })
    }

    @Query("UPDATE forums SET is_private = :isPrivate WHERE id = :id")
    suspend fun setPrivate(id: Int, isPrivate: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourite(favourite: FavouriteForumEntity)

    @Query("DELETE FROM favourite_forums WHERE forum_id = :forumId")
    suspend fun removeFavourite(forumId: Int)
}

/** The columns `get=threads` and `get=recent` provide. */
data class ApiThreadColumns(
    val id: String,
    val title: String,
    @ColumnInfo(name = "forum_id") val forumId: Int,
    @ColumnInfo(name = "forum_name") val forumName: String,
    @ColumnInfo(name = "group_name") val groupName: String?,
    val replies: Int,
    @ColumnInfo(name = "first_user_id") val firstUserId: Long,
    @ColumnInfo(name = "first_user_name") val firstUserName: String,
    @ColumnInfo(name = "first_date") val firstDate: Long,
    @ColumnInfo(name = "last_user_id") val lastUserId: Long,
    @ColumnInfo(name = "last_user_name") val lastUserName: String,
    @ColumnInfo(name = "last_date") val lastDate: Long,
    @ColumnInfo(name = "is_unread") val isUnread: Boolean,
)

/** The columns `get=watched` provides. */
data class WatchedThreadColumns(
    val id: String,
    val title: String,
    @ColumnInfo(name = "forum_id") val forumId: Int,
    @ColumnInfo(name = "forum_name") val forumName: String,
    val replies: Int,
    @ColumnInfo(name = "first_user_id") val firstUserId: Long,
    @ColumnInfo(name = "first_user_name") val firstUserName: String,
    @ColumnInfo(name = "first_date") val firstDate: Long,
    @ColumnInfo(name = "last_user_id") val lastUserId: Long,
    @ColumnInfo(name = "last_user_name") val lastUserName: String,
    @ColumnInfo(name = "last_date") val lastDate: Long,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    @ColumnInfo(name = "last_read_post") val lastReadPost: Int,
    @ColumnInfo(name = "last_page") val lastPage: Int,
)

@Dao
interface ThreadDao {
    @Query(
        """SELECT t.* FROM thread_source s JOIN threads t ON t.id = s.thread_id
           WHERE s.source = :source AND s.`key` = :key ORDER BY s.position""",
    )
    fun observeSource(source: String, key: String): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE id = :id")
    suspend fun get(id: String): ThreadEntity?

    @Upsert(entity = ThreadEntity::class)
    suspend fun upsertApi(threads: List<ApiThreadColumns>)

    @Upsert(entity = ThreadEntity::class)
    suspend fun upsertWatched(threads: List<WatchedThreadColumns>)

    @Query("DELETE FROM thread_source WHERE source = :source AND `key` = :key")
    suspend fun clearSource(source: String, key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSource(rows: List<ThreadSourceEntity>)

    @Transaction
    suspend fun replaceSource(source: ThreadSource, key: String, ids: List<String>) {
        clearSource(source.name, key)
        insertSource(ids.mapIndexed { i, id -> ThreadSourceEntity(source.name, key, id, i) })
    }

    @Transaction
    suspend fun replaceApiList(source: ThreadSource, key: String, threads: List<ApiThreadColumns>) {
        upsertApi(threads)
        replaceSource(source, key, threads.map { it.id })
    }

    @Transaction
    suspend fun replaceWatchedList(key: String, threads: List<WatchedThreadColumns>) {
        upsertWatched(threads)
        replaceSource(ThreadSource.WATCHED, key, threads.map { it.id })
    }

    /** Local effect of "mark read", applied before the API call (2.14.12 behaviour). */
    @Query("UPDATE threads SET unread_count = 0, last_read_post = replies + 1, is_unread = 0 WHERE id IN (:ids)")
    suspend fun markRead(ids: List<String>)

    @Query("DELETE FROM thread_source WHERE source = :source AND thread_id IN (:ids)")
    suspend fun removeFromSource(source: String, ids: List<String>)

    @Query("DELETE FROM thread_source WHERE source = :source AND `key` = :key AND thread_id IN (:ids)")
    suspend fun removeFromSourceKey(source: String, key: String, ids: List<String>)

    /** Drops threads no list refers to any more. */
    @Query("DELETE FROM threads WHERE id NOT IN (SELECT thread_id FROM thread_source)")
    suspend fun deleteOrphans()
}

@Dao
interface NewsDao {
    @Query("SELECT * FROM news ORDER BY date DESC")
    fun observeAll(): Flow<List<NewsEntity>>

    @Query("DELETE FROM news")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(news: List<NewsEntity>)

    @Transaction
    suspend fun replaceAll(news: List<NewsEntity>) {
        deleteAll()
        insertAll(news)
    }
}

@Dao
interface FetchLogDao {
    @Query("SELECT fetched_at FROM fetch_log WHERE `key` = :key")
    suspend fun fetchedAt(key: String): Long?

    @Query("SELECT fetched_at FROM fetch_log WHERE `key` = :key")
    fun observeFetchedAt(key: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: FetchLogEntity)

    @Query("DELETE FROM fetch_log")
    suspend fun clear()
}

@Dao
interface NotifiedDao {
    @Query("SELECT * FROM notified")
    suspend fun all(): List<NotifiedEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entries: List<NotifiedEntity>)
}
