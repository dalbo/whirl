package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.FetchLogDao
import au.howesthat.whirl.core.database.FetchLogEntity
import au.howesthat.whirl.core.database.ThreadDao
import au.howesthat.whirl.core.database.ThreadEntity
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.database.ThreadSourceEntity
import au.howesthat.whirl.core.database.WatchedThreadColumns
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WatchedThread
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

enum class WatchAction { WATCH, MARK_READ, UNWATCH }

/** An action already applied on this device, waiting out its Undo window before it is sent to Whirlpool. */
class PendingWatch internal constructor(
    val action: WatchAction,
    val ids: List<ThreadId>,
    internal val threads: List<ThreadEntity>,
    internal val sources: List<ThreadSourceEntity>,
) {
    internal enum class State { WAITING, SENDING, DONE }

    internal val state = AtomicReference(State.WAITING)
    internal val go = CompletableDeferred<Unit>()

    /** Why sending failed (the change was rolled back); null while waiting or once sent. */
    @Volatile
    var error: Throwable? = null
        internal set
}

sealed interface WatchEvent {
    /** Applied locally; offer Undo, then [WatchedRepository.commit] when the offer goes away. */
    data class Applied(val pending: PendingWatch) : WatchEvent

    /** Whirlpool rejected or never received the action; the local change has been rolled back. */
    data class Failed(val action: WatchAction, val error: Throwable) : WatchEvent
}

/**
 * Watched threads (`get=watched`) and the watch / mark read / unwatch actions.
 *
 * Actions are local first, as in 2.14.12, but are sent only once their Undo window closes ([commit], or
 * [UNDO_WINDOW] at the latest), so Undo never needs a second API call. That matters because Whirlpool has no "mark
 * unread", and re-watching a thread marks it read to the end (verified 2026-10-21). A failed send rolls back. Each send
 * carries `get=watched&watchedmode=1`, whose answer already reflects the change and refreshes both lists in one call.
 */
@Singleton
class WatchedRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: ThreadDao,
    private val log: FetchLogDao,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val refresher = Refresher(log, clock)

    /** Serialises local edits, list replacements and the pending list. */
    private val mutex = Mutex()
    private val pending = mutableListOf<PendingWatch>()

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

    suspend fun refresh(unreadOnly: Boolean, force: Boolean = false): Boolean {
        val key = key(unreadOnly)
        return refresher.refresh(logKey(key), MaxAge.THREAD_LISTS, force) {
            val list = api.watched(unreadOnly).map { it.toColumns() }
            mutex.withLock {
                dao.replaceWatchedList(key, list)
                reapplyPending()
            }
        }
    }

    /**
     * Fetches the unread list now (no max age; the notification check) and stores it as the cached Unread list.
     * Returns what Whirlpool sent, before any pending local changes are reapplied.
     */
    suspend fun fetchUnread(): List<WatchedThread> {
        val list = api.watched(unreadOnly = true)
        mutex.withLock {
            dao.replaceWatchedList(key(unreadOnly = true), list.map { it.toColumns() })
            log.put(FetchLogEntity(logKey(key(unreadOnly = true)), clock.millis()))
            reapplyPending()
        }
        return list
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
     * Applies [action] to the cached lists now and sends it after its Undo window. Emits [WatchEvent.Applied] so the
     * UI can offer Undo. With [undoable] false (auto mark read, notification actions) it is sent at once, silently,
     * and this returns once it has been sent (or failed and rolled back); the send itself is not cancelled with the caller.
     */
    suspend fun perform(action: WatchAction, ids: List<ThreadId>, undoable: Boolean = true): PendingWatch {
        require(ids.isNotEmpty())
        val raw = ids.map { it.value }
        val p = mutex.withLock {
            val p = PendingWatch(action, ids, dao.getAll(raw), dao.sourceRows(ThreadSource.WATCHED.name, raw))
            pending += p
            applyLocal(p)
            p
        }
        if (undoable) {
            scope.launch {
                withTimeoutOrNull(undoWindow) { p.go.await() }
                send(p)
            }
            _events.emit(WatchEvent.Applied(p))
        } else {
            scope.async { send(p) }.await()
        }
        return p
    }

    /** Puts things back as they were. Nothing was sent yet; returns false if it is too late (already sent). */
    suspend fun undo(p: PendingWatch): Boolean {
        if (!p.state.compareAndSet(PendingWatch.State.WAITING, PendingWatch.State.DONE)) return false
        p.go.cancel()
        mutex.withLock { rollBack(p) }
        return true
    }

    /** The Undo offer went away: send now rather than at the end of [UNDO_WINDOW]. */
    fun commit(p: PendingWatch) {
        p.go.complete(Unit)
    }

    private suspend fun send(p: PendingWatch) {
        if (!p.state.compareAndSet(PendingWatch.State.WAITING, PendingWatch.State.SENDING)) return
        try {
            val list = when (p.action) {
                WatchAction.WATCH -> api.updateWatched(watch = p.ids)
                WatchAction.MARK_READ -> api.updateWatched(markRead = p.ids)
                WatchAction.UNWATCH -> api.updateWatched(unwatch = p.ids)
            }.map { it.toColumns() }
            mutex.withLock {
                pending -= p
                store(list)
                reapplyPending()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            p.error = e
            mutex.withLock { rollBack(p) }
            _events.emit(WatchEvent.Failed(p.action, e))
        } finally {
            p.state.set(PendingWatch.State.DONE)
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

    private suspend fun rollBack(p: PendingWatch) {
        pending -= p
        val raw = p.ids.map { it.value }
        dao.upsert(p.threads)
        dao.removeFromSource(ThreadSource.WATCHED.name, raw)
        dao.insertSource(p.sources)
        reapplyPending()
    }

    /** A list replaced from Whirlpool does not know about actions still waiting to be sent. */
    private suspend fun reapplyPending() = pending.forEach { applyLocal(it) }

    private suspend fun applyLocal(p: PendingWatch) {
        val raw = p.ids.map { it.value }
        when (p.action) {
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
        /** The longest an action waits for its Undo offer to close (the snackbar normally closes sooner). */
        val UNDO_WINDOW = 15.seconds
    }
}

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
