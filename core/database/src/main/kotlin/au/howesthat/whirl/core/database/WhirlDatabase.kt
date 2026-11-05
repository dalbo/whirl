package au.howesthat.whirl.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.withTransaction
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        ForumEntity::class,
        FavouriteForumEntity::class,
        ThreadEntity::class,
        ThreadSourceEntity::class,
        NewsEntity::class,
        NotifiedEntity::class,
        FetchLogEntity::class,
        ThreadPageEntity::class,
        PostEntity::class,
        CheckLogEntity::class,
        PendingWatchEntity::class,
    ],
    version = 5,
    exportSchema = true,
    autoMigrations = [
        // v2: thread page cache (thread_pages, posts).
        AutoMigration(from = 1, to = 2),
        // v3: background check history (check_log).
        AutoMigration(from = 2, to = 3),
        // v4: watch actions waiting to be sent (pending_watch).
        AutoMigration(from = 3, to = 4),
        // v5 (MIGRATION_4_5): the thread page cache loses its post filter.
    ],
)
abstract class WhirlDatabase : RoomDatabase() {
    abstract fun forumDao(): ForumDao
    abstract fun threadDao(): ThreadDao
    abstract fun newsDao(): NewsDao
    abstract fun fetchLogDao(): FetchLogDao
    abstract fun notifiedDao(): NotifiedDao
    abstract fun threadPageDao(): ThreadPageDao
    abstract fun checkLogDao(): CheckLogDao
    abstract fun pendingWatchDao(): PendingWatchDao

    /** Clears everything tied to the signed-in account (on log out or key change), in one transaction. Favourites are kept. */
    suspend fun clearAccountData() = withTransaction {
        threadDao().clearSource(ThreadSource.WATCHED.name, "unread")
        threadDao().clearSource(ThreadSource.WATCHED.name, "all")
        threadDao().clearSource(ThreadSource.RECENT.name, "")
        threadDao().deleteOrphans()
        notifiedDao().keepOnly(emptyList())
        fetchLogDao().clear()
        pendingWatchDao().clear()
    }
}

/**
 * v5: `thread_pages` and `posts` lose their `filter` column (v1.7; in-app post filters were gone since v1.1.2). It is
 * part of both primary keys, so the tables are recreated; they are a cache, and pages refetch when opened.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE posts")
        db.execSQL("DROP TABLE thread_pages")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `thread_pages` (`thread_id` TEXT NOT NULL, `page` INTEGER NOT NULL, " +
                "`page_count` INTEGER NOT NULL, `title` TEXT NOT NULL, `forum_id` INTEGER, `forum_title` TEXT, `group_id` INTEGER, " +
                "`moderator_note_html` TEXT, `parts` TEXT NOT NULL, `fetched_at` INTEGER NOT NULL, `accessed_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`thread_id`, `page`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `posts` (`thread_id` TEXT NOT NULL, `page` INTEGER NOT NULL, `reply_id` INTEGER NOT NULL, " +
                "`reply_num` INTEGER NOT NULL, `user_id` INTEGER NOT NULL, `user_name` TEXT NOT NULL, `user_group` TEXT, `quip` TEXT, " +
                "`post_count` INTEGER, `posted_text` TEXT, `posted_at` INTEGER, `edited_text` TEXT, `is_op` INTEGER NOT NULL, " +
                "`hidden_reason` TEXT, `is_deleted` INTEGER NOT NULL, `short_url` TEXT, `body_html` TEXT NOT NULL, " +
                "PRIMARY KEY(`thread_id`, `page`, `reply_id`), FOREIGN KEY(`thread_id`, `page`) REFERENCES `thread_pages`(`thread_id`, `page`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WhirlDatabase =
        Room.databaseBuilder(context, WhirlDatabase::class.java, "whirl.db").addMigrations(MIGRATION_4_5).build()

    @Provides fun forumDao(db: WhirlDatabase) = db.forumDao()
    @Provides fun threadDao(db: WhirlDatabase) = db.threadDao()
    @Provides fun newsDao(db: WhirlDatabase) = db.newsDao()
    @Provides fun fetchLogDao(db: WhirlDatabase) = db.fetchLogDao()
    @Provides fun notifiedDao(db: WhirlDatabase) = db.notifiedDao()
    @Provides fun threadPageDao(db: WhirlDatabase) = db.threadPageDao()
    @Provides fun checkLogDao(db: WhirlDatabase) = db.checkLogDao()
    @Provides fun pendingWatchDao(db: WhirlDatabase) = db.pendingWatchDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class TransactorModule {
    @Binds abstract fun transactor(impl: RoomTransactor): Transactor
}
