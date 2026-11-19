package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.DraftDao
import au.howesthat.whirl.core.database.DraftEntity
import au.howesthat.whirl.core.model.ThreadId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** A reply being written to [threadId], kept until the user deletes it (`docs/plans/post-writer.md` §4). */
data class Draft(val threadId: ThreadId, val threadTitle: String, val text: String, val updatedAt: Instant) {
    /** Quotes in it: lines like `@123 name writes...`. */
    val quotes: Int get() = QUOTE.findAll(text).count()

    private companion object {
        val QUOTE = Regex("""(?m)^@\d+ .* writes\.\.\.$""")
    }
}

/**
 * The drafts, one per thread. One owner for each draft: every write goes through here, one at a time. While the writer
 * for a thread is open it holds the newest text, so a quote added then (a thread beside the writer, in two panes) is
 * handed to the writer ([attachWriter]) rather than written over its head, where the writer's next save would drop it.
 */
@Singleton
class DraftRepository @Inject constructor(private val dao: DraftDao, private val clock: Clock) {

    private val mutex = Mutex()
    /** Open writers by thread id: given a quote, they add it and return their text. */
    private val writers = ConcurrentHashMap<String, suspend (String) -> String>()

    fun observe(threadId: ThreadId): Flow<Draft?> = dao.observe(threadId.value).map { it?.toDraft() }

    /** Newest first. */
    fun observeAll(): Flow<List<Draft>> = dao.observeAll().map { list -> list.map { it.toDraft() } }

    suspend fun get(threadId: ThreadId): Draft? = dao.get(threadId.value)?.toDraft()

    /** Stores [text] as the thread's draft; a blank one deletes it. */
    suspend fun save(threadId: ThreadId, threadTitle: String, text: String) = mutex.withLock { write(threadId, threadTitle, text) }

    suspend fun delete(threadId: ThreadId) = mutex.withLock { dao.delete(threadId.value) }

    /**
     * Adds [quote] to the thread's draft (starting one), to the open writer if there is one. Returns the number of
     * quotes in the draft now, for "Quoted · 3 quotes in your reply".
     */
    suspend fun appendQuote(threadId: ThreadId, threadTitle: String, quote: String): Int = mutex.withLock {
        val writer = writers[threadId.value]
        val text = if (writer != null) {
            writer(quote)
        } else {
            append(dao.get(threadId.value)?.text.orEmpty(), quote).also { write(threadId, threadTitle, it) }
        }
        Draft(threadId, threadTitle, text, clock.instant()).quotes
    }

    /**
     * The writer for [threadId] is open: quotes for that thread go to [onQuote], which adds one (with [append]) and
     * returns its text. It must not call [save] itself (the lock is held while it runs); its own save after an edit
     * stores the quote. Close the handle when the writer goes.
     */
    suspend fun attachWriter(threadId: ThreadId, onQuote: suspend (String) -> String): AutoCloseable {
        // Under the lock, so a quote already being added finishes before the writer takes over.
        mutex.withLock { writers[threadId.value] = onQuote }
        // From the writer's onCleared, outside any coroutine; removes only this writer, not one opened since.
        return AutoCloseable { writers.remove(threadId.value, onQuote) }
    }

    private suspend fun write(threadId: ThreadId, threadTitle: String, text: String) {
        if (text.isBlank()) dao.delete(threadId.value) else dao.upsert(DraftEntity(threadId.value, threadTitle, text, clock.millis()))
    }

    private fun DraftEntity.toDraft() = Draft(ThreadId(threadId), threadTitle, text, Instant.ofEpochMilli(updatedAt))

    companion object {
        /** [quote] after [text], a blank line between. */
        fun append(text: String, quote: String): String = if (text.isBlank()) quote else text.trimEnd() + "\n\n" + quote
    }
}
