package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.FetchLogDao
import au.howesthat.whirl.core.database.FetchLogEntity
import au.howesthat.whirl.core.database.PendingWatchDao
import au.howesthat.whirl.core.database.PendingWatchEntity
import au.howesthat.whirl.core.database.ThreadDao
import au.howesthat.whirl.core.database.ThreadEntity
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.database.ThreadSourceEntity
import au.howesthat.whirl.core.database.Transactor
import au.howesthat.whirl.core.database.WatchedThreadColumns
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WatchedThread
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

enum class WatchAction { WATCH, MARK_READ, UNWATCH }

/**
 * An action already applied on this device, waiting out its Undo window before it is sent to Whirlpool. Its row in
 * `pending_watch` is what gets sent; this holds what the action changed, to put it back on Undo or failure.
 */
class PendingWatch internal constructor(
    val action: WatchAction,
    val ids: List<ThreadId>,
    internal val threads: List<ThreadEntity>,
    internal val sources: List<ThreadSourceEntity>,
) {
    internal var rowId: Long = 0
    internal val go = CompletableDeferred<Unit>()

    /** Sent (null) or given up and rolled back (the error); open while it waits or will be retried. */
    internal val done = CompletableDeferred<Throwable?>()
}

sealed interface WatchEvent {
    /**
     * Applied locally. If [undoable], offer Undo, then [WatchedRepository.commit] when the offer goes away; if not, it
     * is already being sent and this only says it was done.
     */
    data class Applied(val pending: PendingWatch, val undoable: Boolean = true) : WatchEvent

    /** Whirlpool rejected the action, or it could not be sent for a day; the local change has been undone. */
    data class Failed(val action: WatchAction, val error: Throwable) : WatchEvent
}

/** What [WatchedRepository.drain] left behind. */
sealed interface Drain {
    /** Nothing left to send. */
    data object Empty : Drain

    /** A send did not get through (no network, Whirlpool busy); try again later. */
    data object Retry : Drain

    /** The rest are still in their Undo window; the next is due after [wait]. */
    data class Waiting(val wait: Duration) : Drain
}

/**
 * Watched threads (`get=watched`) and the watch / mark read / unwatch actions.
 *
 * Actions are local first, as in 2.14.12, but are sent only once their Undo window closes ([commit], or [UNDO_WINDOW]
 * at the latest), so Undo never needs a second API call. That matters because Whirlpool has no "mark unread", and
 * re-watching a thread marks it read to the end (verified 2026-10-21). Each send carries `get=watched&watchedmode=1`,
 * whose answer already reflects the change and refreshes both lists in one call.
 *
 * Every action is stored (`pending_watch`) until it is sent, so it survives the app closing and waits for the
 * network: while the app runs, [commit] sends it; otherwise [WatchSendWorker] does (docs/plan-v1.6.md §1.3). Actions
 * on the same thread go out in the order they were made. A rejected action is undone locally and reported.
 */
@Singleton
class WatchedRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: ThreadDao,
    private val log: FetchLogDao,
    private val queue: PendingWatchDao,
    private val transactor: Transactor,
    private val account: AccountGeneration,
    private val scheduler: WatchSendScheduler,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val refresher = Refresher(log, clock)

    /**
     * Serialises local edits, list replacements and the queue. Always taken before any database transaction; each
     * section under it is one transaction, so observers (screens, the notification check) never see a list replaced
     * without the actions still waiting to be sent.
     */
    private val mutex = Mutex()

    /** Actions made in this process, by row id, for Undo and rollback. */
    private val live = mutableMapOf<Long, PendingWatch>()

    /** One sender at a time, in the app or in the worker. Taken before [mutex]. */
    private val sending = Mutex()

    internal var undoWindow: Duration = UNDO_WINDOW

    private val _events = MutableSharedFlow<WatchEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<WatchEvent> = _events.asSharedFlow()

    fun watched(unreadOnly: Boolean): Flow<Cached<List<WatchedThread>>> {
        val key = key(unreadOnly)
        return combine(dao.observeSource(ThreadSource.WATCHED.name, key), log.observeFetchedAt(logKey(key))) { rows, at ->
            Cached(rows.map { it.toWatched() }, at?.let(Instant::ofEpochMilli))
        }
    }

    /** Threads on either watched list, including actions not yet sent. Complete once the All list has been fetched. */
    val watchedIds: Flow<Set<ThreadId>> = dao.observeWatchedIds().map { ids -> ids.mapTo(mutableSetOf(), ::ThreadId) }

    fun isWatched(id: ThreadId): Flow<Boolean> = watchedIds.map { id in it }

    /** The last post read in a watched thread with unread posts, as the cached lists know it; null otherwise. */
    suspend fun lastReadPost(id: ThreadId): Int? {
        if (!isWatched(id).first()) return null
        val t = dao.get(id.value) ?: return null
        return t.lastReadPost?.takeIf { it > 0 && (t.unreadCount ?: 0) > 0 }
    }

    suspend fun refresh(unreadOnly: Boolean, force: Boolean = false): Boolean = refresh(unreadOnly, MaxAge.THREAD_LISTS, force)

    private suspend fun refresh(unreadOnly: Boolean, maxAge: Duration, force: Boolean): Boolean {
        val key = key(unreadOnly)
        return refresher.refresh(logKey(key), maxAge, force, lastFetched = { log.fetchedAt(logKey(key)) }) {
            val gen = account.current
            val list = api.watched(unreadOnly).map { it.toColumns() }
            storeLists(gen) {
                dao.replaceWatchedList(key, list)
                log.put(FetchLogEntity(logKey(key), clock.millis()))
            }
        }
    }

    /**
     * Fetches the unread list now (no max age; the notification check) and stores it as the cached Unread list.
     * Returns what Whirlpool sent, less the threads with a Mark as read or Unwatch still waiting to be sent.
     */
    suspend fun fetchUnread(): List<WatchedThread> {
        val gen = account.current
        val list = api.watched(unreadOnly = true)
        val queued = storeLists(gen) {
            dao.replaceWatchedList(key(unreadOnly = true), list.map { it.toColumns() })
            log.put(FetchLogEntity(logKey(key(unreadOnly = true)), clock.millis()))
            queue.all().filter { it.action != WatchAction.WATCH.name }.flatMapTo(mutableSetOf()) { it.ids }
        } ?: return emptyList() // fetched for an account that has since signed out
        return list.filterNot { it.id.value in queued }
    }

    /**
     * Runs [block] under [mutex] in one transaction, then re-applies the actions not yet sent and drops threads no list
     * refers to. Null, with nothing written, if the account changed since [gen] was read (before the fetch).
     */
    private suspend fun <R : Any> storeLists(gen: Int, block: suspend () -> R): R? = mutex.withLock {
        transactor.run {
            if (account.current != gen) return@run null
            val result = block()
            reapplyPending()
            dao.deleteOrphans()
            result
        }
    }

    /** Fetches the All list if it is stale, so screens outside Watched know what is watched. Errors are ignored. */
    suspend fun ensureKnown() {
        try {
            refresh(unreadOnly = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Watched state stays as cached; the action menus fall back to offering Watch.
        }
    }

    /**
     * Applies [action] to the cached lists now and queues it. With [undoable] it is sent after its Undo window, and
     * [WatchEvent.Applied] lets the UI offer Undo; without, it is sent at once, and [announce] says whether the UI
     * still says it was done (Undo turned off in settings) or not (auto mark read, notification buttons). Either way it
     * is kept until Whirlpool has it; [awaitSend] waits for the outcome.
     */
    suspend fun perform(action: WatchAction, ids: List<ThreadId>, undoable: Boolean = true, announce: Boolean = undoable): PendingWatch {
        require(ids.isNotEmpty())
        val raw = ids.map { it.value }
        val now = clock.millis()
        val wait = if (undoable) undoWindow else Duration.ZERO
        val p = mutex.withLock {
            val p = transactor.run {
                val p = PendingWatch(action, ids, dao.getAll(raw), dao.sourceRows(ThreadSource.WATCHED.name, raw))
                p.rowId = queue.insert(
                    PendingWatchEntity(action = action.name, threadIds = raw.joinToString(","), sendAfter = now + wait.inWholeMilliseconds, createdAt = now),
                )
                applyLocal(action, raw)
                p
            }
            live[p.rowId] = p
            p
        }
        // The backstop, should the app not be around to send it.
        scheduler.schedule(wait)
        if (undoable) {
            scope.launch {
                withTimeoutOrNull(undoWindow) { p.go.await() }
                if (p.go.isCancelled) return@launch
                queue.makeDue(p.rowId, clock.millis())
                drainInBackground()
            }
            _events.emit(WatchEvent.Applied(p))
        } else {
            scope.launch { drainInBackground() }
            if (announce) _events.emit(WatchEvent.Applied(p, undoable = false))
        }
        return p
    }

    /**
     * Waits up to [timeout] for [p] to be sent. False only if Whirlpool rejected it (it has been rolled back); true once
     * sent, or while it is still queued to be retried.
     */
    suspend fun awaitSend(p: PendingWatch, timeout: Duration): Boolean =
        // Timed on a real dispatcher: the send runs on one, whatever clock the caller's dispatcher keeps.
        withContext(Dispatchers.Default) { withTimeoutOrNull(timeout) { p.done.await() } } == null

    /** Puts things back as they were. Nothing was sent yet; returns false if it is too late (being sent or sent). */
    suspend fun undo(p: PendingWatch): Boolean = mutex.withLock {
        val undone = transactor.run {
            if (queue.deleteWaiting(p.rowId) == 0) return@run false
            rollBack(p)
            true
        }
        if (undone) {
            p.go.cancel()
            live -= p.rowId
        }
        undone
    }

    /** The Undo offer went away: send now rather than at the end of [UNDO_WINDOW]. */
    fun commit(p: PendingWatch) {
        p.go.complete(Unit)
    }

    /**
     * [drain] from the app's own scope, where an unexpected error (the database, say) must not crash the app: the
     * action stays queued for [WatchSendWorker].
     */
    private suspend fun drainInBackground() {
        try {
            drain()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            scheduler.schedule(Duration.ZERO)
        }
    }

    /** Sends the queued actions that are due, oldest first. Called by the app and by [WatchSendWorker]. */
    suspend fun drain(): Drain = sending.withLock { drainLocked() }

    private suspend fun drainLocked(): Drain {
        // Nothing is being sent now, so a row still marked as sending was cut off when its process ended.
        queue.resetSending()
        while (true) {
            val rows = queue.all()
            if (rows.isEmpty()) return Drain.Empty
            val now = clock.millis()
            val row = nextDue(rows, now) ?: return Drain.Waiting(rows.filter { it.sendAfter > now }.minOf { it.sendAfter - now }.milliseconds)
            if (queue.claim(row.id) == 0) continue
            if (!send(row, now)) {
                scheduler.schedule(Duration.ZERO)
                return Drain.Retry
            }
        }
    }

    /** The first due row with no earlier row on the same threads, so actions on a thread go out in order. */
    private fun nextDue(rows: List<PendingWatchEntity>, now: Long): PendingWatchEntity? {
        val earlier = mutableSetOf<String>()
        for (row in rows) {
            val ids = row.ids
            if (row.sendAfter <= now && ids.none { it in earlier }) return row
            earlier += ids
        }
        return null
    }

    /** Sends one claimed row. False if it should be tried again later; otherwise it is gone from the queue. */
    private suspend fun send(row: PendingWatchEntity, now: Long): Boolean {
        val action = WatchAction.valueOf(row.action)
        val ids = row.ids.map(::ThreadId)
        val gen = account.current
        try {
            val list = when (action) {
                WatchAction.WATCH -> api.updateWatched(watch = ids)
                WatchAction.MARK_READ -> api.updateWatched(markRead = ids)
                WatchAction.UNWATCH -> api.updateWatched(unwatch = ids)
            }.map { it.toColumns() }
            storeLists(gen) {
                queue.delete(row.id)
                store(list)
                listOfNotNull(live.remove(row.id))
            }?.forEach { it.done.complete(null) }
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val giveUp = row.attempts + 1 >= MAX_ATTEMPTS || now - row.createdAt > MAX_AGE.inWholeMilliseconds
            if (!giveUp && e is WhirlpoolError.Maintenance) {
                // Whirlpool answers an id it does not know (a thread deleted since the list was fetched) with a 500
                // for the whole request. If the list loads, Whirlpool is up: send again without the threads no
                // longer watched, or give up if they all still are. If it does not, Whirlpool is down: try later.
                val still = stillWatched()
                if (still == null) {
                    queue.release(row.id, row.threadIds)
                    return false
                }
                val keep = if (action == WatchAction.WATCH) row.ids else row.ids.filter { it in still }
                if (keep.size < row.ids.size) {
                    queue.release(row.id, keep.joinToString(","))
                    if (keep.isEmpty()) finish(row.id, null)
                    return true
                }
            } else if (!giveUp && e.isTransient()) {
                queue.release(row.id, row.threadIds)
                return false
            }
            finish(row.id, e)
            // The action may be hours old: Whirlpool's lists, not what was cached then, say how things are now.
            refreshQuietly()
            _events.emit(WatchEvent.Failed(action, e))
            return true
        }
    }

    /**
     * Removes a row that is done with. With an [error], the threads go back on the lists they were on when the action
     * was made, if this process made it. Their details stay as fetched since (the snapshot is used only for a thread
     * no longer cached, which an Unwatch can cause), and [refreshQuietly] corrects the rest.
     */
    private suspend fun finish(rowId: Long, error: Throwable?) {
        val p = mutex.withLock {
            transactor.run {
                queue.delete(rowId)
                val p = live[rowId]
                if (p != null && error != null) {
                    val cached = dao.getAll(p.ids.map { it.value }).mapTo(mutableSetOf()) { it.id }
                    dao.upsert(p.threads.filterNot { it.id in cached })
                    restoreMembership(p)
                }
            }
            live.remove(rowId)
        }
        p?.done?.complete(error)
    }

    private suspend fun stillWatched(): Set<String>? = try {
        api.watched(unreadOnly = false).mapTo(mutableSetOf()) { it.id.value }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /** After a failure: Whirlpool's lists show what really happened. Fetched however recently they were. */
    private suspend fun refreshQuietly() {
        try {
            refresh(unreadOnly = false, maxAge = Duration.ZERO, force = false)
            refresh(unreadOnly = true, maxAge = Duration.ZERO, force = false)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The next refresh corrects the lists.
        }
    }

    /** At startup: queued actions left by an earlier process (closed, force-stopped, restored from a backup) get sent. */
    suspend fun resume() {
        if (queue.all().isNotEmpty()) scheduler.schedule(Duration.ZERO)
    }

    /**
     * Signing out or into another account. [change] stores the new key (or removes it) and [clear] deletes the old
     * account's data. Nothing queued may be sent with the next key, so this waits for a send in progress and forgets the
     * queue first; nothing fetched with the old key may be stored after [clear], so the account generation moves on in
     * the same transaction, after [change].
     */
    suspend fun switchAccount(change: suspend () -> Unit, clear: suspend () -> Unit) = sending.withLock {
        mutex.withLock {
            scheduler.cancel()
            queue.clear()
            live.values.forEach { it.go.cancel() }
            live.clear()
            change()
            transactor.run {
                account.next()
                clear()
            }
        }
    }

    /** Both lists from one `watchedmode=1` answer: Unread is All filtered, in the same order (as the fixtures show). */
    private suspend fun store(all: List<WatchedThreadColumns>) {
        dao.replaceWatchedList(key(unreadOnly = false), all)
        dao.replaceWatchedList(key(unreadOnly = true), all.filter { it.unreadCount > 0 })
        val now = clock.millis()
        log.put(FetchLogEntity(logKey(key(unreadOnly = false)), now))
        log.put(FetchLogEntity(logKey(key(unreadOnly = true)), now))
    }

    /** Undo: everything as it was when the action was made, minutes ago at most. */
    private suspend fun rollBack(p: PendingWatch) {
        dao.upsert(p.threads)
        restoreMembership(p)
    }

    /** The threads back on the watched lists they were on when the action was made. */
    private suspend fun restoreMembership(p: PendingWatch) {
        dao.removeFromSource(ThreadSource.WATCHED.name, p.ids.map { it.value })
        dao.insertSource(p.sources)
        reapplyPending()
    }

    /** A list replaced from Whirlpool does not know about actions still waiting to be sent. */
    private suspend fun reapplyPending() = queue.all().forEach { applyLocal(WatchAction.valueOf(it.action), it.ids) }

    private suspend fun applyLocal(action: WatchAction, raw: List<String>) {
        when (action) {
            WatchAction.MARK_READ -> {
                dao.markRead(raw)
                dao.removeFromSourceKey(ThreadSource.WATCHED.name, key(unreadOnly = true), raw)
            }
            WatchAction.UNWATCH -> dao.removeFromSource(ThreadSource.WATCHED.name, raw)
            // Shown at the end of All until Whirlpool's list replaces it; a thread never cached gets no row to show.
            WatchAction.WATCH -> {
                val all = key(unreadOnly = false)
                val present = dao.sourceRows(ThreadSource.WATCHED.name, raw).filter { it.key == all }.map { it.threadId }.toSet()
                val start = (dao.maxPosition(ThreadSource.WATCHED.name, all) ?: -1) + 1
                dao.insertSource(
                    raw.filterNot { it in present }.mapIndexed { i, id -> ThreadSourceEntity(ThreadSource.WATCHED.name, all, id, start + i) },
                )
            }
        }
    }

    private fun key(unreadOnly: Boolean) = if (unreadOnly) "unread" else "all"

    private fun logKey(key: String) = "watched:$key"

    companion object {
        /**
         * The longest an action waits for its Undo offer to close. The snackbar closes after Settings.undoSeconds (at most 10 s), but
         * Android's "Time to take action" setting can stretch it to 2 minutes; this is also when the backstop runs.
         */
        val UNDO_WINDOW = 3.minutes

        /** Sends that did not get through are retried this many times, and for at most [MAX_AGE]. */
        const val MAX_ATTEMPTS = 5
        val MAX_AGE = 24.hours
    }
}

/** No network, Cloudflare or rate limiting: worth sending again later. ([WhirlpoolError.Maintenance] is checked apart.) */
private fun Exception.isTransient() = this is WhirlpoolError.Network || this is WhirlpoolError.Challenge || this is WhirlpoolError.RateLimited

private fun WatchedThread.toColumns() = WatchedThreadColumns(
    id = id.value, title = title, forumId = forumId, forumName = forumName, replies = replies,
    firstUserId = firstPoster.id, firstUserName = firstPoster.name, firstDate = firstDate.toInstant().toEpochMilli(),
    lastUserId = lastPoster.id, lastUserName = lastPoster.name, lastDate = lastDate.toInstant().toEpochMilli(),
    unreadCount = unreadCount, lastReadPost = lastReadPost, lastPage = lastPage,
)

private fun ThreadEntity.toWatched() = WatchedThread(
    id = ThreadId(id), title = title, replies = replies, forumId = forumId, forumName = forumName,
    firstDate = firstDate.toOffsetDateTime(), firstPoster = UserRef(firstUserId ?: 0, firstUserName.orEmpty()),
    lastDate = lastDate.toOffsetDateTime(), lastPoster = UserRef(lastUserId ?: 0, lastUserName.orEmpty()),
    unreadCount = unreadCount ?: 0, lastReadPost = lastReadPost ?: 0, lastPage = lastPage ?: 1,
)
