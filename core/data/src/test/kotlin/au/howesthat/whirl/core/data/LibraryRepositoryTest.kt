package au.howesthat.whirl.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.ForumDao
import au.howesthat.whirl.core.database.WhirlDatabase
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
import java.lang.reflect.Proxy
import kotlin.test.assertEquals

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

    private fun v18Favourites(vararg ids: Int) =
        ids.forEach { db.openHelper.writableDatabase.execSQL("INSERT INTO favourite_forums (forum_id, added_at) VALUES ($it, 1)") }

    @Test
    fun `v1_8's favourites move once, and one removed since stays removed`() = runBlocking {
        v18Favourites(92, 100)
        val first = Opened()
        withTimeout(5_000) {
            assertEquals(setOf(92, 100), first.repo.favouriteForums.first())
            first.repo.setFavourite(100, false)
            first.repo.setFavourite(7, true)
            assertEquals(setOf(92, 7), first.repo.favouriteForums.first())
        }
        first.close()

        val again = Opened() // the table is still there, as it is until a later version drops it
        withTimeout(5_000) { assertEquals(setOf(92, 7), again.repo.favouriteForums.first()) }
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
        var fail = true
        val real = db.forumDao()
        val flaky = Proxy.newProxyInstance(ForumDao::class.java.classLoader, arrayOf(ForumDao::class.java)) { _, method, args ->
            if (method.name == "favouriteIds" && fail) {
                fail = false
                throw IllegalStateException("database closed")
            }
            method.invoke(real, *(args ?: emptyArray()))
        } as ForumDao
        val s = Opened(flaky)
        withTimeout(5_000) {
            assertEquals(emptySet(), s.repo.favouriteForums.first(), "the list still shows")
            assertEquals(setOf(92), s.repo.favouriteForums.first(), "and the next read moves them")
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
