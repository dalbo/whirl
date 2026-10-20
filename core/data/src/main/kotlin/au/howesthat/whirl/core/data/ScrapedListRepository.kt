package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.model.ThreadListPage
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.network.WhirlpoolWeb
import au.howesthat.whirl.core.scraper.ThreadListParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import okhttp3.HttpUrl
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** A scraped thread table: one forum page, Popular, or one page of search results (list view). */
sealed interface ScrapedList {
    fun url(urls: WhirlpoolUrls): HttpUrl

    data class ForumPage(val forumId: Int, val page: Int, val groupId: Int? = null) : ScrapedList {
        override fun url(urls: WhirlpoolUrls) = urls.forum(forumId, page, groupId)
    }

    data object Popular : ScrapedList {
        override fun url(urls: WhirlpoolUrls) = urls.popular()
    }

    data class Search(
        val query: String,
        val forumId: Int? = null,
        val groupId: Int? = null,
        val searchIn: WhirlpoolUrls.SearchIn = WhirlpoolUrls.SearchIn.TITLES,
        val page: Int = 1,
    ) : ScrapedList {
        override fun url(urls: WhirlpoolUrls) = urls.searchList(query, forumId, groupId, searchIn, page)
    }
}

/**
 * Scraped thread tables, kept in memory only: like 2.14.12 they are not persisted, but revisiting one within
 * [MaxAge.THREAD_LISTS] does not refetch it. The newest [KEEP] lists are kept.
 */
@Singleton
class ScrapedListRepository @Inject constructor(
    private val web: WhirlpoolWeb,
    private val forums: ForumRepository,
    private val clock: Clock,
) {
    private val refresher = Refresher(log = null, clock = clock)
    private val cache = MutableStateFlow<Map<ScrapedList, Cached<ThreadListPage>>>(emptyMap())

    fun observe(list: ScrapedList): Flow<Cached<ThreadListPage>?> = cache.map { it[list] }

    /**
     * Fetches [list] unless it is fresh. A forum page that needs a web login throws [WhirlpoolError.NotSignedIn] and
     * marks its forum private; an unreadable page throws [WhirlpoolError.Drift].
     */
    suspend fun refresh(list: ScrapedList, force: Boolean = false): Boolean =
        refresher.refresh(list.toString(), MaxAge.THREAD_LISTS, force, lastFetched = { cache.value[list]?.updatedAt?.toEpochMilli() }) {
            val page = try {
                parseOrDrift { ThreadListParser.parse(web.get(list.url(web.urls))) }
            } catch (e: WhirlpoolError.NotSignedIn) {
                if (list is ScrapedList.ForumPage) forums.markPrivate(list.forumId)
                throw e
            }
            val entry = Cached(page, Instant.ofEpochMilli(clock.millis()))
            cache.update { old -> (old - list).entries.sortedByDescending { it.value.updatedAt }.take(KEEP - 1).associate { it.toPair() } + (list to entry) }
        }

    private companion object {
        const val KEEP = 40
    }
}

/** Runs a parser; failures other than [WhirlpoolError] mean the markup moved and become [WhirlpoolError.Drift]. */
internal inline fun <T> parseOrDrift(parse: () -> T): T = try {
    parse()
} catch (e: WhirlpoolError) {
    throw e
} catch (e: RuntimeException) {
    throw WhirlpoolError.Drift("Could not read the page", e)
}
