package au.howesthat.whirl.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val name = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), WhirlDatabase::class.java)

    @Test
    fun `v1 to v2 adds the thread page cache and keeps v1 data`() = runTest {
        helper.createDatabase(name, 1).use { db ->
            db.execSQL("INSERT INTO forums (id, title, section, sort, is_private) VALUES (92, 'Choosing an ISP', 'ISPs', 0, 0)")
            db.execSQL("INSERT INTO favourite_forums (forum_id, added_at) VALUES (92, 1)")
            db.execSQL("INSERT INTO threads (id, title, forum_id, forum_name, replies, unread_count) VALUES ('3pxj858k', 'NBN', 92, 'Choosing an ISP', 250, 4)")
            db.execSQL("INSERT INTO thread_source (source, `key`, thread_id, position) VALUES ('WATCHED', 'all', '3pxj858k', 0)")
            db.execSQL("INSERT INTO fetch_log (`key`, fetched_at) VALUES ('forums', 123)")
        }
        // Validates the migrated schema against the exported v2 schema.
        helper.runMigrationsAndValidate(name, 2, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val forums = db.forumDao().observeAll().first()
            assertEquals(listOf(92), forums.map { it.id })
            assertEquals(true, forums.single().isFavourite)
            assertEquals(4, db.threadDao().get("3pxj858k")?.unreadCount)
            assertEquals(listOf("3pxj858k"), db.threadDao().observeSource("WATCHED", "all").first().map { it.id })
            assertEquals(123L, db.fetchLogDao().fetchedAt("forums"))
            assertEquals(0, db.threadPageDao().pageCount())
        } finally {
            db.close()
        }
    }

    @Test
    fun `v2 to v3 adds the check history and keeps v2 data`() = runTest {
        helper.createDatabase(name, 2).use { db ->
            db.execSQL("INSERT INTO notified (thread_id, last_date) VALUES ('3pxj858k', 5)")
            db.execSQL("INSERT INTO fetch_log (`key`, fetched_at) VALUES ('forums', 123)")
        }
        helper.runMigrationsAndValidate(name, 3, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(listOf(NotifiedEntity("3pxj858k", 5)), db.notifiedDao().all())
            assertEquals(123L, db.fetchLogDao().fetchedAt("forums"))
            assertEquals(emptyList(), db.checkLogDao().observe().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `v3 to v4 adds the queue of watch actions and keeps v3 data`() = runTest {
        helper.createDatabase(name, 3).use { db ->
            db.execSQL("INSERT INTO fetch_log (`key`, fetched_at) VALUES ('forums', 123)")
        }
        helper.runMigrationsAndValidate(name, 4, true).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(123L, db.fetchLogDao().fetchedAt("forums"))
            assertEquals(emptyList(), db.pendingWatchDao().all())
        } finally {
            db.close()
        }
    }

    @Test
    fun `v4 to v5 empties the page cache without its filter and keeps the rest`() = runTest {
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO fetch_log (`key`, fetched_at) VALUES ('forums', 123)")
            db.execSQL("INSERT INTO threads (id, title, forum_id, forum_name, replies, unread_count) VALUES ('3pxj858k', 'NBN', 92, 'Choosing an ISP', 250, 4)")
            db.execSQL("INSERT INTO pending_watch (action, thread_ids, send_after, created_at, state, attempts) VALUES ('UNWATCH', '3pxj858k', 1, 1, 'WAITING', 0)")
            // The same page cached twice: all posts, and (before v1.1.2) moderators' posts.
            for (filter in listOf("", "m")) {
                db.execSQL(
                    "INSERT INTO thread_pages (thread_id, filter, page, page_count, title, parts, fetched_at, accessed_at) " +
                        "VALUES ('3pxj858k', '$filter', 1, 13, 'NBN', '', 1, 1)",
                )
                db.execSQL(
                    "INSERT INTO posts (thread_id, filter, page, reply_id, reply_num, user_id, user_name, is_op, is_deleted, body_html) " +
                        "VALUES ('3pxj858k', '$filter', 1, 10, 1, 1, 'a', 1, 0, '<p>hi</p>')",
                )
            }
        }
        helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(0, db.threadPageDao().pageCount(), "a cache: pages refetch when opened")
            assertEquals(123L, db.fetchLogDao().fetchedAt("forums"))
            assertEquals(4, db.threadDao().get("3pxj858k")?.unreadCount)
            assertEquals(listOf("UNWATCH"), db.pendingWatchDao().all().map { it.action })
        } finally {
            db.close()
        }
    }
}
