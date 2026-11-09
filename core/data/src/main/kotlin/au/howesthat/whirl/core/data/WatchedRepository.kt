package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.data.WatchedStore.Companion.logKey
import au.howesthat.whirl.core.database.ThreadEntity
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.database.WatchedThreadColumns
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WatchAction
import au.howesthat.whirl.core.model.WatchedThread
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * The watched threads (`get=watched`): the Unread and All lists as cached, with the actions still waiting to be sent
 * applied ([WatchQueue] makes and sends them).
 */
@Singleton
class WatchedRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val store: WatchedStore,
) {
    private val dao = store.dao
    private val log = store.log
    private val list = CachedApiList(log, store.clock, MaxAge.THREAD_LISTS)

    fun watched(unreadOnly: Boolean): Flow<Cached<List<WatchedThread>>> =
        list.observe(dao.observeSource(ThreadSource.WATCHED, WatchedStore.key(unreadOnly)), logKey(unreadOnly)) { it.toWatched() }

    /** Threads on either watched list, including actions not yet sent. Complete once the All list has been fetched. */
    val watchedIds: Flow<Set<ThreadId>> = dao.observeWatchedIds().map { ids -> ids.mapTo(mutableSetOf(), ::ThreadId) }

    fun isWatched(id: ThreadId): Flow<Boolean> = watchedIds.map { id in it }

    /** When the All list was last fetched (epoch milliseconds); null before the first fetch and after an account switch. */
    val allFetchedAt: Flow<Long?> = log.observeFetchedAt(logKey(unreadOnly = false))

    /**
     * The All list as last stored, plus the threads with an Unwatch still waiting to be sent: those are out of the
     * stored list already, but Undo (or Whirlpool rejecting it) brings them back.
     */
    suspend fun watchedAsFetched(): Set<ThreadId> {
        val stored = dao.observeSource(ThreadSource.WATCHED, WatchedStore.key(unreadOnly = false)).first().map { it.id }
        val unwatching = store.queue.all().filter { it.action == WatchAction.UNWATCH }.flatMap { it.ids }
        return (stored + unwatching).mapTo(mutableSetOf(), ::ThreadId)
    }

    /** The last post read in a watched thread with unread posts, as the cached lists know it; null otherwise. */
    suspend fun lastReadPost(id: ThreadId): Int? {
        if (!isWatched(id).first()) return null
        val t = dao.get(id.value) ?: return null
        return t.lastReadPost?.takeIf { it > 0 && (t.unreadCount ?: 0) > 0 }
    }

    /** Fetches the list if it is older than [maxAge] ([MaxAge.THREAD_LISTS] unless the send queue asks for zero). */
    suspend fun refresh(unreadOnly: Boolean, force: Boolean = false, maxAge: Duration = MaxAge.THREAD_LISTS): Boolean =
        list.refreshLogged(logKey(unreadOnly), force, maxAge) {
            store.account.fetchAndStore({ api.watched(unreadOnly).map { it.toColumns() } }) { gen, rows ->
                store.storeLists(gen) { store.replaceList(unreadOnly, rows) }
            }
        }

    /**
     * Fetches the unread list now (no max age; the notification check) and stores it as the cached Unread list.
     * Returns what Whirlpool sent, less the threads with a Mark as read or Unwatch still waiting to be sent.
     */
    suspend fun fetchUnread(): List<WatchedThread> {
        val (list, queued) = store.account.fetchAndStore({ api.watched(unreadOnly = true) }) { gen, list ->
            store.storeLists(gen) {
                store.replaceList(unreadOnly = true, list.map { it.toColumns() })
                list to store.queue.all().filter { it.action != WatchAction.WATCH }.flatMapTo(mutableSetOf()) { it.ids }
            }
        } ?: return emptyList() // the account changed twice while it was fetched
        return list.filterNot { it.id.value in queued }
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
}

internal fun WatchedThread.toColumns() = WatchedThreadColumns(
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
