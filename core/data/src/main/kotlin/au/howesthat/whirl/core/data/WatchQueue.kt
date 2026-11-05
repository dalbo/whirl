package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.PendingWatchEntity
import au.howesthat.whirl.core.database.ThreadEntity
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.database.ThreadSourceEntity
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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

    /** How it ended; open while it waits or will be retried. */
    internal val done = CompletableDeferred<SendResult>()
}

/** What became of an action [WatchQueue.awaitSend] waited for. */
sealed interface SendResult {
    /** Whirlpool has it, or it is still queued to be sent later (no network, Whirlpool busy, or not sent yet). */
    data object SentOrQueued : SendResult

    /** Whirlpool rejected it, or it could not be sent for a day; the local change has been undone. */
    data class Rejected(val error: Throwable) : SendResult

    /** An account switch (signing out or into another account) forgot it before it was sent. */
    data object Dropped : SendResult
}

sealed interface WatchEvent {
    /**
     * Applied locally. If [undoable], offer Undo, then [WatchQueue.commit] when the offer goes away; if not, it is
     * already being sent and this only says it was done.
     */
    data class Applied(val pending: PendingWatch, val undoable: Boolean = true) : WatchEvent

    /** Whirlpool rejected the action, or it could not be sent for a day; the local change has been undone. */
    data class Failed(val action: WatchAction, val error: Throwable) : WatchEvent
}

/** What [WatchQueue.drain] left behind. */
sealed interface Drain {
    /** Nothing left to send. */
    data object Empty : Drain

    /** A send did not get through (no network, Whirlpool busy); try again later. */
    data object Retry : Drain

    /** The rest are still in their Undo window; the next is due after [wait]. */
    data class Waiting(val wait: Duration) : Drain
}

/**
 * The watch / mark read / unwatch actions, and account switches (which must not let one account's actions go out
 * with the next one's key).
 *
 * Actions are local first, as in 2.14.12, but are sent only once their Undo window closes ([commit], or [UNDO_WINDOW]
 * at the latest), so Undo never needs a second API call. That matters because Whirlpool has no "mark unread", and
 * re-watching a thread marks it read to the end (verified 2026-10-21). Each send carries `get=watched&watchedmode=1`,
 * whose answer already reflects the change and refreshes both lists in one call.
 *
 * Every action is stored (`pending_watch`) until it is sent, so it survives the app closing and waits for the
 * network: while the app runs, [commit] sends it; otherwise [WatchSendWorker] does (docs/plans/v1.6.md §1.3). Actions
 * on the same thread go out in the order they were made. A rejected action is undone locally and reported.
 */
@Singleton
class WatchQueue @Inject constructor(
    private val api: WhirlpoolApi,
    private val store: WatchedStore,
    private val lists: WatchedRepository,
    private val scheduler: WatchSendScheduler,
    /** Times [awaitSend]: the send runs on the app's scope, whatever clock the caller's dispatcher keeps. */
    @DefaultDispatcher private val timing: CoroutineDispatcher,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val dao = store.dao
    private val queue = store.queue
    private val clock = store.clock
    private val mutex = store.mutex

    /** Actions made in this process, by row id, for Undo and rollback. Guarded by [mutex]. */
    private val live = mutableMapOf<Long, PendingWatch>()

    /** One sender at a time, in the app or in the worker. Taken before [mutex]. */
    private val sending = Mutex()

    /** Set while an account switch waits for [sending]: the sender stops after the row it is sending. */
    @Volatile private var switching = false

    internal var undoWindow: Duration = UNDO_WINDOW

    private val _events = MutableSharedFlow<WatchEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<WatchEvent> = _events.asSharedFlow()

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
            val p = store.transactor.run {
                val p = PendingWatch(action, ids, dao.getAll(raw), dao.sourceRows(ThreadSource.WATCHED, raw))
                p.rowId = queue.insert(PendingWatchEntity(action = action, ids = raw, sendAfter = now + wait.inWholeMilliseconds, createdAt = now))
                store.applyLocal(action, raw)
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

    /** Waits up to [timeout] for [p] to be sent; still waiting then is [SendResult.SentOrQueued]. */
    suspend fun awaitSend(p: PendingWatch, timeout: Duration): SendResult =
        withContext(timing) { withTimeoutOrNull(timeout) { p.done.await() } } ?: SendResult.SentOrQueued

    /** Puts things back as they were. Nothing was sent yet; returns false if it is too late (being sent or sent). */
    suspend fun undo(p: PendingWatch): Boolean = mutex.withLock {
        val undone = store.transactor.run {
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
            // Signing out: the queue is about to be forgotten; stop after the row just sent.
            if (switching) return Drain.Empty
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
            if (row.sendAfter <= now && row.ids.none { it in earlier }) return row
            earlier += row.ids
        }
        return null
    }

    /** Sends one claimed row. False if it should be tried again later; otherwise it is gone from the queue. */
    private suspend fun send(row: PendingWatchEntity, now: Long): Boolean {
        val ids = row.ids.map(::ThreadId)
        val gen = store.account.current
        try {
            val list = when (row.action) {
                WatchAction.WATCH -> api.updateWatched(watch = ids)
                WatchAction.MARK_READ -> api.updateWatched(markRead = ids)
                WatchAction.UNWATCH -> api.updateWatched(unwatch = ids)
            }.map { it.toColumns() }
            store.storeLists(gen) {
                queue.delete(row.id)
                store.replaceBoth(list)
                listOfNotNull(live.remove(row.id))
            }?.forEach { it.done.complete(SendResult.SentOrQueued) }
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
                    queue.release(row.id, row.ids)
                    return false
                }
                val keep = if (row.action == WatchAction.WATCH) row.ids else row.ids.filter { it in still }
                if (keep.size < row.ids.size) {
                    queue.release(row.id, keep)
                    if (keep.isEmpty()) finish(row.id, null)
                    return true
                }
            } else if (!giveUp && e.isTransient()) {
                queue.release(row.id, row.ids)
                return false
            }
            finish(row.id, e)
            // The action may be hours old: Whirlpool's lists, not what was cached then, say how things are now. Not
            // after giving up on the network, which the lists would not get through either.
            if (!e.isTransient()) refreshQuietly()
            _events.emit(WatchEvent.Failed(row.action, e))
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
            store.transactor.run {
                queue.delete(rowId)
                val p = live[rowId]
                if (p != null && error != null) {
                    val cached = dao.getAll(p.ids.map { it.value }).mapTo(mutableSetOf()) { it.id }
                    dao.upsert(p.threads.filterNot { it.id in cached })
                    // While the action waited, every refresh re-applied Mark as read, so these columns are still ours.
                    if (p.action == WatchAction.MARK_READ) {
                        p.threads.filter { it.id in cached }.forEach { dao.setReadState(it.id, it.unreadCount, it.lastReadPost, it.isUnread) }
                    }
                    store.restoreMembership(p)
                }
            }
            live.remove(rowId)
        }
        p?.done?.complete(if (error == null) SendResult.SentOrQueued else SendResult.Rejected(error))
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
        if (switching) return
        try {
            lists.refresh(unreadOnly = false, force = false, maxAge = Duration.ZERO)
            lists.refresh(unreadOnly = true, force = false, maxAge = Duration.ZERO)
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
     * Signing out or into another account; the only way to. [change] stores the new key (or removes it) and [clear]
     * deletes the old account's data. Nothing queued may be sent with the next key, so this waits for the row being
     * sent (the sender stops after it) and forgets the queue first: actions made in this process end as
     * [SendResult.Dropped]. Nothing fetched with the old key may be stored after [clear], so the account generation
     * moves on in the same transaction, after [change].
     */
    suspend fun switchAccount(change: suspend () -> Unit, clear: suspend () -> Unit) {
        switching = true
        try {
            sending.withLock {
                mutex.withLock {
                    scheduler.cancel()
                    queue.clear()
                    live.values.forEach {
                        it.go.cancel()
                        it.done.complete(SendResult.Dropped)
                    }
                    live.clear()
                    change()
                    store.transactor.run {
                        store.account.next()
                        clear()
                    }
                }
            }
        } finally {
            switching = false
        }
    }

    /** Undo: everything as it was when the action was made, minutes ago at most. */
    private suspend fun rollBack(p: PendingWatch) {
        dao.upsert(p.threads)
        store.restoreMembership(p)
    }

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
