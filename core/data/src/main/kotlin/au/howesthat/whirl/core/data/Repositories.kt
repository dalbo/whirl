package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.ApiThreadColumns
import au.howesthat.whirl.core.database.FavouriteForumEntity
import au.howesthat.whirl.core.database.FetchLogDao
import au.howesthat.whirl.core.database.FetchLogEntity
import au.howesthat.whirl.core.database.ForumDao
import au.howesthat.whirl.core.database.ForumEntity
import au.howesthat.whirl.core.database.NewsDao
import au.howesthat.whirl.core.database.NewsEntity
import au.howesthat.whirl.core.database.ThreadDao
import au.howesthat.whirl.core.database.ThreadEntity
import au.howesthat.whirl.core.database.ThreadSource
import au.howesthat.whirl.core.model.Forum
import au.howesthat.whirl.core.model.NewsItem
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadSummary
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.network.WhirlpoolApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
            Cached(
                rows.map { ForumListItem(Forum(it.id, it.title, it.section), it.isFavourite, it.isPrivate || it.id in KNOWN_PRIVATE) },
                at?.let(Instant::ofEpochMilli),
            )
        }

    /** Forums whose pages need a web login: the known ones plus any learned from a "Not signed in" page. */
    val privateIds: Flow<Set<Int>> = dao.observeAll().map { rows -> rows.filter { it.isPrivate }.mapTo(KNOWN_PRIVATE.toMutableSet()) { it.id } }

    suspend fun refresh(force: Boolean = false) = refresher.refresh(KEY, MaxAge.FORUMS, force) {
        dao.replaceAll(api.forums().mapIndexed { i, f -> ForumEntity(f.id, f.title, f.section, i) })
    }

    suspend fun setFavourite(forumId: Int, favourite: Boolean) =
        if (favourite) dao.addFavourite(FavouriteForumEntity(forumId, clock.millis())) else dao.removeFavourite(forumId)

    /** Called when a forum page turns out to need a web login. */
    suspend fun markPrivate(forumId: Int) = dao.setPrivate(forumId, true)

    /**
     * Once (v1.1.2): forget the private forums learned so far. Up to v1.1.1 a filtered thread view, which needs a login
     * everywhere, marked its forum private, after which the forum's threads always opened in the browser. Truly private
     * forums are learned again from their pages; the known ones (KNOWN_PRIVATE) never depended on this.
     */
    suspend fun forgetLearnedPrivateOnce() {
        if (log.fetchedAt(PRIVATE_RESET_KEY) != null) return
        dao.clearPrivate()
        log.put(FetchLogEntity(PRIVATE_RESET_KEY, clock.millis()))
    }

    companion object {
        private const val KEY = "forums"
        private const val PRIVATE_RESET_KEY = "reset:learned-private-forums-1.1.2"

        /** Private Messages, Feedback, In the news, Talk with a mod (PLAN.md §2.2). */
        val KNOWN_PRIVATE = setOf(1, 35, 85, 134)
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

    val threads: Flow<Cached<List<ThreadSummary>>> = observeApiList(dao, log, ThreadSource.LATEST, KEY, LOG_KEY)

    suspend fun refresh(force: Boolean = false) = refresher.refresh(LOG_KEY, MaxAge.THREAD_LISTS, force) {
        dao.replaceApiList(ThreadSource.LATEST, KEY, api.threads(count = 100).map { it.toColumns() })
    }

    private companion object {
        const val KEY = ""
        const val LOG_KEY = "latest"
    }
}

/**
 * One forum's 100 most recently active threads from the API (`get=threads&forumids=N&threadcount=100`). For public
 * forums this overlays `UNREAD` on the scraped pages; for private forums it is the whole list (where the API allows).
 */
@Singleton
class ForumThreadsRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: ThreadDao,
    private val log: FetchLogDao,
    clock: Clock,
) {
    private val refresher = Refresher(log, clock)

    fun threads(forumId: Int): Flow<Cached<List<ThreadSummary>>> =
        observeApiList(dao, log, ThreadSource.FORUM, forumId.toString(), logKey(forumId))

    suspend fun refresh(forumId: Int, force: Boolean = false) = refresher.refresh(logKey(forumId), MaxAge.THREAD_LISTS, force) {
        dao.replaceApiList(ThreadSource.FORUM, forumId.toString(), api.threads(listOf(forumId), count = 100).map { it.toColumns() })
    }

    private fun logKey(forumId: Int) = "forum-api:$forumId"
}

/** Threads the user posted in over the last `recentHistoryDays` days (`get=recent`). */
@Singleton
class RecentRepository @Inject constructor(
    private val api: WhirlpoolApi,
    private val dao: ThreadDao,
    private val log: FetchLogDao,
    clock: Clock,
) {
    private val refresher = Refresher(log, clock)

    fun threads(days: Int): Flow<Cached<List<ThreadSummary>>> = observeApiList(dao, log, ThreadSource.RECENT, KEY, logKey(days))

    /** A changed history length is a different list, so it fetches at once. */
    suspend fun refresh(days: Int, force: Boolean = false) = refresher.refresh(logKey(days), MaxAge.THREAD_LISTS, force) {
        dao.replaceApiList(ThreadSource.RECENT, KEY, api.recent(days).map { it.toColumns() })
        // Only the latest history length's list is stored, so older lengths must refetch.
        log.deleteMatching("recent:%")
    }

    private fun logKey(days: Int) = "recent:$days"

    private companion object {
        // clearAccountData() clears this key on log out.
        const val KEY = ""
    }
}

private fun observeApiList(dao: ThreadDao, log: FetchLogDao, source: ThreadSource, key: String, logKey: String) =
    combine(dao.observeSource(source.name, key), log.observeFetchedAt(logKey)) { rows, at ->
        Cached(rows.map { it.toSummary() }, at?.let(Instant::ofEpochMilli))
    }

private fun ThreadSummary.toColumns() = ApiThreadColumns(
    id = id.value, title = title, forumId = forumId, forumName = forumName, groupName = group, replies = replies,
    firstUserId = firstPoster.id, firstUserName = firstPoster.name, firstDate = firstDate.toInstant().toEpochMilli(),
    lastUserId = lastPoster.id, lastUserName = lastPoster.name, lastDate = lastDate.toInstant().toEpochMilli(),
    isUnread = isUnread,
)

// Cached rows lose the API's +1000 offset; instants are what matter and display uses the device or Sydney zone.
internal fun Long?.toOffsetDateTime(): OffsetDateTime =
    OffsetDateTime.ofInstant(Instant.ofEpochMilli(this ?: 0), ZoneOffset.UTC)

private fun ThreadEntity.toSummary() = ThreadSummary(
    id = ThreadId(id), title = title, replies = replies, forumId = forumId, forumName = forumName, group = groupName,
    firstDate = firstDate.toOffsetDateTime(), firstPoster = UserRef(firstUserId ?: 0, firstUserName.orEmpty()),
    lastDate = lastDate.toOffsetDateTime(), lastPoster = UserRef(lastUserId ?: 0, lastUserName.orEmpty()),
    isSubscribed = false, isUnread = isUnread == true, hasParticipated = false,
)
