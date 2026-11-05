package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.FetchLogDao
import au.howesthat.whirl.core.database.FetchLogEntity
import au.howesthat.whirl.core.database.PendingWatchDao
import au.howesthat.whirl.core.database.ThreadDao
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.database.ThreadSourceEntity
import au.howesthat.whirl.core.database.Transactor
import au.howesthat.whirl.core.database.WatchedThreadColumns
import au.howesthat.whirl.core.model.WatchAction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the watched lists ([WatchedRepository]) and the send queue ([WatchQueue]) share: the cached lists, the queue
 * table and [mutex]. The queue's own state (actions made in this process, the sender's lock) stays in [WatchQueue].
 *
 * Lock order: `WatchQueue.sending`, then [mutex], then the database transaction. Every section under [mutex] is one
 * transaction, so observers (screens, the notification check) never see a list replaced without the actions still
 * waiting to be sent.
 */
@Singleton
class WatchedStore @Inject constructor(
    internal val dao: ThreadDao,
    internal val log: FetchLogDao,
    internal val queue: PendingWatchDao,
    internal val transactor: Transactor,
    internal val account: AccountGeneration,
    internal val clock: Clock,
) {
    /** Serialises local edits, list replacements and the queue. Always taken before any database transaction. */
    internal val mutex = Mutex()

    /**
     * Runs [block] under [mutex] in one transaction, then re-applies the actions not yet sent and drops threads no list
     * refers to. Null, with nothing written, if the account changed since [gen] was read (before the fetch).
     */
    internal suspend fun <R : Any> storeLists(gen: Int, block: suspend () -> R): R? = mutex.withLock {
        transactor.run {
            if (account.current != gen) return@run null
            val result = block()
            reapplyPending()
            dao.deleteOrphans()
            result
        }
    }

    /** One list as Whirlpool sent it, and when. Call inside [storeLists]. */
    internal suspend fun replaceList(unreadOnly: Boolean, list: List<WatchedThreadColumns>, at: Long = clock.millis()) {
        dao.replaceWatchedList(key(unreadOnly), list)
        log.put(FetchLogEntity(logKey(unreadOnly), at))
    }

    /** Both lists from one `watchedmode=1` answer: Unread is All filtered, in the same order (as the fixtures show). */
    internal suspend fun replaceBoth(all: List<WatchedThreadColumns>) {
        val now = clock.millis()
        replaceList(unreadOnly = false, all, now)
        replaceList(unreadOnly = true, all.filter { it.unreadCount > 0 }, now)
    }

    /** The threads back on the watched lists they were on when [p] was made. */
    internal suspend fun restoreMembership(p: PendingWatch) {
        dao.removeFromSource(ThreadSource.WATCHED, p.ids.map { it.value })
        dao.insertSource(p.sources)
        reapplyPending()
    }

    /** A list replaced from Whirlpool does not know about actions still waiting to be sent. */
    private suspend fun reapplyPending() = queue.all().forEach { applyLocal(it.action, it.ids) }

    internal suspend fun applyLocal(action: WatchAction, raw: List<String>) {
        when (action) {
            WatchAction.MARK_READ -> {
                dao.markRead(raw)
                dao.removeFromSourceKey(ThreadSource.WATCHED, key(unreadOnly = true), raw)
            }
            WatchAction.UNWATCH -> dao.removeFromSource(ThreadSource.WATCHED, raw)
            // Shown at the end of All until Whirlpool's list replaces it; a thread never cached gets no row to show.
            WatchAction.WATCH -> {
                val all = key(unreadOnly = false)
                val present = dao.sourceRows(ThreadSource.WATCHED, raw).filter { it.key == all }.map { it.threadId }.toSet()
                val start = (dao.maxPosition(ThreadSource.WATCHED, all) ?: -1) + 1
                dao.insertSource(
                    raw.filterNot { it in present }.mapIndexed { i, id -> ThreadSourceEntity(ThreadSource.WATCHED, all, id, start + i) },
                )
            }
        }
    }

    internal companion object {
        /** The `thread_source` key of each list; `WhirlDatabase.clearAccountData` clears both. */
        fun key(unreadOnly: Boolean) = if (unreadOnly) "unread" else "all"

        fun logKey(unreadOnly: Boolean) = "watched:${key(unreadOnly)}"
    }
}
