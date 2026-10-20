package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.ApiThreadColumns
import au.howesthat.whirl.core.database.FavouriteForumEntity
import au.howesthat.whirl.core.database.FetchLogDao
import au.howesthat.whirl.core.database.ForumDao
import au.howesthat.whirl.core.database.ForumEntity
import au.howesthat.whirl.core.database.NewsDao
import au.howesthat.whirl.core.database.NewsEntity
import au.howesthat.whirl.core.database.ThreadDao
import au.howesthat.whirl.core.database.ThreadEntity
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.database.WatchedThreadColumns
import au.howesthat.whirl.core.model.Forum
import au.howesthat.whirl.core.model.NewsItem
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadSummary
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WatchedThread
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

data class ForumListItem(val forum: Forum, val isFavourite: Boolean, val isPrivate: Boolean)

@Singleton
class ForumRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: ForumDao,
    private val log: FetchLogDao,
    private val clock: Clock,
) {
    private val refresher = Refresher(log, clock)

    val forums: Flow<Cached<List<ForumListItem>>> =
        combine(dao.observeAll(), log.observeFetchedAt(KEY)) { rows, at ->
            Cached(rows.map { ForumListItem(Forum(it.id, it.title, it.section), it.isFavourite, it.isPrivate) }, at?.let(Instant::ofEpochMilli))
        }

    suspend fun refresh(force: Boolean = false) = refresher.refresh(KEY, MaxAge.FORUMS, force) {
        dao.replaceAll(api.forums().mapIndexed { i, f -> ForumEntity(f.id, f.title, f.section, i) })
    }

    suspend fun setFavourite(forumId: Int, favourite: Boolean) =
        if (favourite) dao.addFavourite(FavouriteForumEntity(forumId, clock.millis())) else dao.removeFavourite(forumId)

    /** Called when a forum page turns out to need a web login. */
    suspend fun markPrivate(forumId: Int) = dao.setPrivate(forumId, true)

    private companion object {
        const val KEY = "forums"
    }
}

@Singleton
class NewsRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: NewsDao,
    private val log: FetchLogDao,
    clock: Clock,
) {
    private val refresher = Refresher(log, clock)

    val news: Flow<Cached<List<NewsItem>>> = combine(dao.observeAll(), log.observeFetchedAt(KEY)) { rows, at ->
        Cached(
            rows.map {
                val offset = ZoneOffset.ofTotalSeconds(it.offsetSeconds)
                NewsItem(it.id, it.title, it.source, it.blurb, OffsetDateTime.ofInstant(Instant.ofEpochMilli(it.date), offset))
            },
            at?.let(Instant::ofEpochMilli),
        )
    }

    suspend fun refresh(force: Boolean = false) = refresher.refresh(KEY, MaxAge.NEWS, force) {
        dao.replaceAll(
            api.news().map {
                NewsEntity(it.id, it.title, it.source, it.blurb, it.date.toInstant().toEpochMilli(), it.date.offset.totalSeconds)
            },
        )
    }

    private companion object {
        const val KEY = "news"
    }
}

/** Site-wide most recently active threads (`get=threads` without forumids): the "Latest" feed. */
@Singleton
class LatestThreadsRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: ThreadDao,
    private val log: FetchLogDao,
    clock: Clock,
) {
    private val refresher = Refresher(log, clock)

    val threads: Flow<Cached<List<ThreadSummary>>> =
        combine(dao.observeSource(ThreadSource.LATEST.name, KEY), log.observeFetchedAt(LOG_KEY)) { rows, at ->
            Cached(rows.map { it.toSummary() }, at?.let(Instant::ofEpochMilli))
        }

    suspend fun refresh(force: Boolean = false) = refresher.refresh(LOG_KEY, MaxAge.THREAD_LISTS, force) {
        dao.replaceApiList(ThreadSource.LATEST, KEY, api.threads(count = 100).map { it.toColumns() })
    }

    private companion object {
        const val KEY = ""
        const val LOG_KEY = "latest"
    }
}

@Singleton
class WatchedRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: ThreadDao,
    private val log: FetchLogDao,
    clock: Clock,
) {
    private val refresher = Refresher(log, clock)

    fun watched(unreadOnly: Boolean): Flow<Cached<List<WatchedThread>>> {
        val key = key(unreadOnly)
        return combine(dao.observeSource(ThreadSource.WATCHED.name, key), log.observeFetchedAt("watched:$key")) { rows, at ->
            Cached(rows.map { it.toWatched() }, at?.let(Instant::ofEpochMilli))
        }
    }

    suspend fun refresh(unreadOnly: Boolean, force: Boolean = false): Boolean {
        val key = key(unreadOnly)
        return refresher.refresh("watched:$key", MaxAge.THREAD_LISTS, force) {
            dao.replaceWatchedList(key, api.watched(unreadOnly).map { it.toColumns() })
        }
    }

    /** Marks threads read locally first (so the list updates at once), then on Whirlpool. */
    suspend fun markRead(ids: List<ThreadId>) {
        val raw = ids.map { it.value }
        dao.markRead(raw)
        dao.removeFromSourceKey(ThreadSource.WATCHED.name, key(unreadOnly = true), raw)
        api.markRead(ids)
    }

    suspend fun unwatch(ids: List<ThreadId>) {
        dao.removeFromSource(ThreadSource.WATCHED.name, ids.map { it.value })
        api.unwatch(ids)
    }

    private fun key(unreadOnly: Boolean) = if (unreadOnly) "unread" else "all"
}

private fun ThreadSummary.toColumns() = ApiThreadColumns(
    id = id.value, title = title, forumId = forumId, forumName = forumName, groupName = group, replies = replies,
    firstUserId = firstPoster.id, firstUserName = firstPoster.name, firstDate = firstDate.toInstant().toEpochMilli(),
    lastUserId = lastPoster.id, lastUserName = lastPoster.name, lastDate = lastDate.toInstant().toEpochMilli(),
    isUnread = isUnread,
)

private fun WatchedThread.toColumns() = WatchedThreadColumns(
    id = id.value, title = title, forumId = forumId, forumName = forumName, replies = replies,
    firstUserId = firstPoster.id, firstUserName = firstPoster.name, firstDate = firstDate.toInstant().toEpochMilli(),
    lastUserId = lastPoster.id, lastUserName = lastPoster.name, lastDate = lastDate.toInstant().toEpochMilli(),
    unreadCount = unreadCount, lastReadPost = lastReadPost, lastPage = lastPage,
)

// Cached rows lose the API's +1000 offset; instants are what matter and display uses the device or Sydney zone.
private fun Long?.toOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(this ?: 0), ZoneOffset.UTC)

private fun ThreadEntity.toSummary() = ThreadSummary(
    id = ThreadId(id), title = title, replies = replies, forumId = forumId, forumName = forumName, group = groupName,
    firstDate = firstDate.toOffsetDateTime(), firstPoster = UserRef(firstUserId ?: 0, firstUserName.orEmpty()),
    lastDate = lastDate.toOffsetDateTime(), lastPoster = UserRef(lastUserId ?: 0, lastUserName.orEmpty()),
    isSubscribed = false, isUnread = isUnread == true, hasParticipated = false,
)

private fun ThreadEntity.toWatched() = WatchedThread(
    id = ThreadId(id), title = title, replies = replies, forumId = forumId, forumName = forumName,
    firstDate = firstDate.toOffsetDateTime(), firstPoster = UserRef(firstUserId ?: 0, firstUserName.orEmpty()),
    lastDate = lastDate.toOffsetDateTime(), lastPoster = UserRef(lastUserId ?: 0, lastUserName.orEmpty()),
    unreadCount = unreadCount ?: 0, lastReadPost = lastReadPost ?: 0, lastPage = lastPage ?: 1,
)
