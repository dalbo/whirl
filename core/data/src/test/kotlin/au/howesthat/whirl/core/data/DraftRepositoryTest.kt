package au.howesthat.whirl.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import au.howesthat.whirl.core.database.WhirlDatabase
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.model.ThreadId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The post writer's drafts (`docs/plans/post-writer.md` §4). */
@RunWith(RobolectricTestRunner::class)
class DraftRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WhirlDatabase::class.java)
        .allowMainThreadQueries().build()
    private val repo = DraftRepository(db.draftDao(), Clock.fixed(Instant.parse("2026-11-19T00:00:00Z"), ZoneOffset.UTC))
    private val nbn = ThreadId("3pxj858k")

    @After
    fun tearDown() = db.close()

    @Test
    fun `saved, listed newest first, and a blank draft is deleted`() = runTest {
        repo.save(nbn, "NBN", "Hello")
        assertEquals("Hello", repo.get(nbn)?.text)
        assertEquals(listOf(nbn), repo.observeAll().first().map { it.threadId })
        repo.save(nbn, "NBN", "  \n")
        assertNull(repo.get(nbn))
    }

    @Test
    fun `quotes are added below the text, and counted`() = runTest {
        assertEquals(1, repo.appendQuote(nbn, "NBN", "@1 A writes...\n\n[\"one\"]"))
        assertEquals(2, repo.appendQuote(nbn, "NBN", "@2 B writes...\n\n[\"two\"]"))
        assertEquals("@1 A writes...\n\n[\"one\"]\n\n@2 B writes...\n\n[\"two\"]", repo.get(nbn)?.text)
    }

    @Test
    fun `a quote goes to the open writer, not over its head`() = runTest {
        repo.save(nbn, "NBN", "stored")
        var writerText = "typed since the last save"
        val handle = repo.attachWriter(nbn) { quote -> DraftRepository.append(writerText, quote).also { writerText = it } }
        assertEquals(1, repo.appendQuote(nbn, "NBN", "@1 A writes..."))
        assertEquals("typed since the last save\n\n@1 A writes...", writerText)
        assertEquals("stored", repo.get(nbn)?.text, "the writer saves it, not the repository")

        handle.close()
        repo.appendQuote(nbn, "NBN", "@2 B writes...")
        assertEquals("stored\n\n@2 B writes...", repo.get(nbn)?.text, "once the writer is gone, quotes are stored")
    }

    @Test
    fun `signing out keeps drafts`() = runTest {
        repo.save(nbn, "NBN", "mine")
        db.clearAccountData()
        assertEquals("mine", repo.get(nbn)?.text)
        assertEquals(emptyList(), db.threadDao().observeSource(ThreadSource.WATCHED, "all").first())
    }
}
