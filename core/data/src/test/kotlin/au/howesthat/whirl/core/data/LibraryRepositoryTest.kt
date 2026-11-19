package au.howesthat.whirl.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.ForumDao
import au.howesthat.whirl.core.database.ForumEntity
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.model.ThreadId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Favourite forums in the library store (v1.9), moved once from Room. */
@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val file by lazy { File(tmp.root, "library.preferences_pb") }
    private val scopes = mutableListOf<CoroutineScope>()

    @After
    fun tearDown() = runBlocking {
        scopes.forEach { it.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    /** A repository on the library file; [close] before opening it again (one DataStore per file at a time). */
    private inner class Opened(dao: ForumDao = db.forumDao()) {
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob()).also(scopes::add)
        val store = PreferenceDataStoreFactory.create(scope = scope) { file }
        val repo = LibraryRepository(store, dao)
        suspend fun close() = scope.coroutineContext.job.cancelAndJoin()
    }

    /** v1.8's table as v7 made it, which the v8 migration keeps until the favourites are moved. */
    private fun v18Favourites(vararg ids: Int) {
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TABLE IF NOT EXISTS favourite_forums (forum_id INTEGER NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY(forum_id))")
        ids.forEach { sql.execSQL("INSERT INTO favourite_forums (forum_id, added_at) VALUES ($it, 1)") }
    }

    private fun tableExists() = db.openHelper.writableDatabase
        .query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'favourite_forums'").use { it.moveToFirst() }

    @Test
    fun `v1_8's favourites move once, Room's table is dropped, and one removed since stays removed`() = runBlocking {
        v18Favourites(92, 100)
        val first = Opened()
        withTimeout(5_000) {
            assertEquals(setOf(92, 100), first.repo.favouriteForums.first())
            assertFalse(tableExists(), "dropped once moved")
            first.repo.setFavourite(100, false)
            first.repo.setFavourite(7, true)
            assertEquals(setOf(92, 7), first.repo.favouriteForums.first())
        }
        first.close()

        v18Favourites(55) // a leftover table (say a drop that failed) is never read again
        val again = Opened()
        withTimeout(5_000) { assertEquals(setOf(92, 7), again.repo.favouriteForums.first()) }
        assertFalse(tableExists(), "and is dropped")
    }

    @Test
    fun `a removal before the first read is not undone by the move`() = runBlocking {
        v18Favourites(92, 100)
        val s = Opened()
        withTimeout(5_000) {
            s.repo.setFavourite(92, false)
            assertEquals(setOf(100), s.repo.favouriteForums.first())
        }
    }

    @Test
    fun `a move that fails is tried again on the next read`() = runBlocking {
        v18Favourites(92)
        val flaky = FlakyForumDao(db)
        val s = Opened(flaky)
        withTimeout(5_000) {
            assertEquals(emptySet(), s.repo.favouriteForums.first(), "the list still shows")
            assertEquals(setOf(92), s.repo.favouriteForums.first(), "and the next read moves them")
        }
    }

    @Test
    fun `a new install has no table to move, and imported favourites are added`() = runBlocking {
        val s = Opened()
        withTimeout(5_000) {
            s.repo.setFavourite(7, true)
            s.repo.addFavourites(listOf(92, 7))
            assertEquals(setOf(7, 92), s.repo.favouriteForums.first())
        }
    }

    @Test
    fun `mutes are set, cleared, and forgotten for threads no longer watched`() = runBlocking {
        val s = Opened()
        withTimeout(5_000) {
            s.repo.setMuted(listOf(ThreadId("a"), ThreadId("b"), ThreadId("c")), muted = true)
            s.repo.setMuted(listOf(ThreadId("c")), muted = false)
            assertEquals(setOf(ThreadId("a"), ThreadId("b")), s.repo.mutedThreads.first())
            s.repo.keepMutedOnly(setOf(ThreadId("b"), ThreadId("z")))
            assertEquals(setOf(ThreadId("b")), s.repo.mutedThreads.first())
        }
    }

    @Test
    fun `an entry that is not a forum id is skipped`() = runBlocking {
        val s = Opened()
        withTimeout(5_000) {
            s.repo.setFavourite(92, true)
            s.store.edit { it[stringSetPreferencesKey("favourite_forums")] = setOf("92", "nonsense") }
            assertEquals(setOf(92), s.repo.favouriteForums.first())
        }
    }
}

/** The real DAO, whose first read of v1.8's favourites fails. */
private class FlakyForumDao(db: WhirlDatabase) : ForumDao(db) {
    private val real = db.forumDao()
    private var fail = true
    override fun observeAll() = real.observeAll()
    override suspend fun privateIds() = real.privateIds()
    override suspend fun deleteAll() = real.deleteAll()
    override suspend fun insertAll(forums: List<ForumEntity>) = real.insertAll(forums)
    override suspend fun setPrivate(id: Int, isPrivate: Boolean) = real.setPrivate(id, isPrivate)
    override suspend fun clearPrivate() = real.clearPrivate()
    override suspend fun legacyFavouriteIds(): List<Int>? {
        if (fail) {
            fail = false
            throw IllegalStateException("database closed")
        }
        return super.legacyFavouriteIds()
    }
}
