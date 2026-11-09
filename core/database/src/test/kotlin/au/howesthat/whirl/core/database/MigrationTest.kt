package au.howesthat.whirl.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import au.howesthat.whirl.core.model.WatchAction
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
            assertEquals(listOf("3pxj858k"), db.threadDao().observeSource(ThreadSource.WATCHED, "all").first().map { it.id })
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
    fun `v4 to v5 keeps the plain cached pages, drops filtered ones, and keeps the rest`() = runTest {
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO fetch_log (`key`, fetched_at) VALUES ('forums', 123)")
            db.execSQL("INSERT INTO threads (id, title, forum_id, forum_name, replies, unread_count) VALUES ('3pxj858k', 'NBN', 92, 'Choosing an ISP', 250, 4)")
            db.execSQL("INSERT INTO pending_watch (action, thread_ids, send_after, created_at, state, attempts) VALUES ('UNWATCH', '3pxj858k', 1, 1, 'WAITING', 0)")
            // The same page cached twice: all posts, and (before v1.1.2) moderators' posts.
            for (filter in listOf("", "m")) {
                db.execSQL(
                    "INSERT INTO thread_pages (thread_id, filter, page, page_count, title, parts, fetched_at, accessed_at) " +
                        "VALUES ('3pxj858k', '$filter', 1, ${if (filter == "") 13 else 1}, 'NBN', '', 1, 1)",
                )
                db.execSQL(
                    "INSERT INTO posts (thread_id, filter, page, reply_id, reply_num, user_id, user_name, is_op, is_deleted, body_html) " +
                        "VALUES ('3pxj858k', '$filter', 1, ${if (filter == "") 10 else 99}, 1, 1, 'a', 1, 0, '<p>hi</p>')",
                )
            }
        }
        helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(1, db.threadPageDao().pageCount(), "the plain page, which still opens offline")
            assertEquals(listOf(10L), db.threadPageDao().posts("3pxj858k", 1).map { it.replyId })
            assertEquals(13, db.threadPageDao().lastKnownPageCount("3pxj858k"))
            assertEquals(123L, db.fetchLogDao().fetchedAt("forums"))
            assertEquals(4, db.threadDao().get("3pxj858k")?.unreadCount)
            assertEquals(listOf(WatchAction.UNWATCH), db.pendingWatchDao().all().map { it.action })
        } finally {
            db.close()
        }
    }

    @Test
    fun `v5 to v6 drops the unused thread columns and keeps everything else`() = runTest {
        helper.createDatabase(name, 5).use { db ->
            db.execSQL(
                "INSERT INTO threads (id, title, forum_id, forum_name, group_name, replies, page_count, first_user_id, first_user_name, " +
                    "first_date, last_user_id, last_user_name, last_date, is_unread, unread_count, last_read_post, last_page, is_sticky, is_closed) " +
                    "VALUES ('3pxj858k', 'NBN', 92, 'Choosing an ISP', 'Fibre', 250, NULL, 1, 'a', 10, 2, 'b', 20, 1, 4, 246, 13, 0, 0)",
            )
            db.execSQL("INSERT INTO thread_source (source, `key`, thread_id, position) VALUES ('WATCHED', 'all', '3pxj858k', 0)")
            db.execSQL("INSERT INTO pending_watch (action, thread_ids, send_after, created_at, state, attempts) VALUES ('MARK_READ', '3pxj858k,9yvx1663', 1, 1, 'SENDING', 2)")
        }
        helper.runMigrationsAndValidate(name, 6, true, MIGRATION_4_5).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val t = db.threadDao().get("3pxj858k")
            assertEquals(
                ThreadEntity(
                    "3pxj858k", "NBN", 92, "Choosing an ISP", "Fibre", 250, 1, "a", 10, 2, "b", 20,
                    isUnread = true, unreadCount = 4, lastReadPost = 246, lastPage = 13,
                ),
                t,
            )
            assertEquals(listOf("3pxj858k"), db.threadDao().observeSource(ThreadSource.WATCHED, "all").first().map { it.id })
            // The queue's text columns read as the typed values they always held.
            val row = db.pendingWatchDao().all().single()
            assertEquals(WatchAction.MARK_READ, row.action)
            assertEquals(PendingState.SENDING, row.state)
            assertEquals(listOf("3pxj858k", "9yvx1663"), row.ids)
        } finally {
            db.close()
        }
    }

    @Test
    fun `v6 to v7 adds the archive cache and the rep's company, keeping cached posts`() = runTest {
        helper.createDatabase(name, 6).use { db ->
            db.execSQL(
                "INSERT INTO thread_pages (thread_id, page, page_count, title, forum_id, forum_title, group_id, moderator_note_html, parts, " +
                    "fetched_at, accessed_at) VALUES ('3l10nlj5', 1, 31, 'Leaptel', 31, 'Other ISPs', NULL, NULL, '', 1, 1)",
            )
            db.execSQL(
                "INSERT INTO posts (thread_id, page, reply_id, reply_num, user_id, user_name, user_group, quip, post_count, posted_text, " +
                    "posted_at, edited_text, is_op, hidden_reason, is_deleted, short_url, body_html) " +
                    "VALUES ('3l10nlj5', 1, 73670545, 13, 27743, 'Matt E.', 'ISP Representative', NULL, 9458, 'x', NULL, NULL, 0, NULL, 0, NULL, '<p>hi</p>')",
            )
        }
        helper.runMigrationsAndValidate(name, 7, true, MIGRATION_4_5).close()

        val db = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java, name)
            .addMigrations(MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val post = db.threadPageDao().posts("3l10nlj5", 1).single()
            assertEquals("ISP Representative", post.userGroup)
            assertEquals(null, post.repOf, "filled in when the page is next fetched")
            assertEquals(0, db.archiveDao().pageCount())
        } finally {
            db.close()
        }
    }
}
