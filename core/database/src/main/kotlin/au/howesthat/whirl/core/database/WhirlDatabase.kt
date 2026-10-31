package au.howesthat.whirl.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: thread page cache (thread_pages, posts).
        AutoMigration(from = 1, to = 2),
        // v3: background check history (check_log).
        AutoMigration(from = 2, to = 3),
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

    /** Clears everything tied to the signed-in account (on log out or key change). Favourites are kept. */
    suspend fun clearAccountData() {
        threadDao().clearSource(ThreadSource.WATCHED.name, "unread")
        threadDao().clearSource(ThreadSource.WATCHED.name, "all")
        threadDao().clearSource(ThreadSource.RECENT.name, "")
        threadDao().deleteOrphans()
        notifiedDao().keepOnly(emptyList())
        fetchLogDao().clear()
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): WhirlDatabase =
        Room.databaseBuilder(context, WhirlDatabase::class.java, "whirl.db").build()

    @Provides fun forumDao(db: WhirlDatabase) = db.forumDao()
    @Provides fun threadDao(db: WhirlDatabase) = db.threadDao()
    @Provides fun newsDao(db: WhirlDatabase) = db.newsDao()
    @Provides fun fetchLogDao(db: WhirlDatabase) = db.fetchLogDao()
    @Provides fun notifiedDao(db: WhirlDatabase) = db.notifiedDao()
    @Provides fun threadPageDao(db: WhirlDatabase) = db.threadPageDao()
    @Provides fun checkLogDao(db: WhirlDatabase) = db.checkLogDao()
}
