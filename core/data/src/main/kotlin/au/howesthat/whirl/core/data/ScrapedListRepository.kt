package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.model.ThreadListPage
import au.howesthat.whirl.core.model.WhirlpoolError
import au.howesthat.whirl.core.network.WhirlpoolUrls
import au.howesthat.whirl.core.network.WhirlpoolWeb
import au.howesthat.whirl.core.scraper.ThreadListParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import java.time.Clock
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
    @DefaultDispatcher private val parsing: CoroutineDispatcher,
) {
    private val cache = MemoryCache<ScrapedList, ThreadListPage>(KEEP, clock)

    fun observe(list: ScrapedList): Flow<Cached<ThreadListPage>?> = cache.observe(list)

    /**
     * Fetches [list] unless it is fresh. A forum page that needs a web login throws [WhirlpoolError.NotSignedIn] and
     * marks its forum private; an unreadable page throws [WhirlpoolError.Drift].
     */
    suspend fun refresh(list: ScrapedList, force: Boolean = false): Boolean =
        cache.refresh(list, MaxAge.THREAD_LISTS, force) {
            try {
                val html = web.get(list.url(web.urls))
                parseOrDrift(parsing) { ThreadListParser.parse(html) }
            } catch (e: WhirlpoolError.NotSignedIn) {
                if (list is ScrapedList.ForumPage) forums.markPrivate(list.forumId)
                throw e
            }
        }

    private companion object {
        const val KEEP = 40
    }
}

/**
 * Runs a parser on [dispatcher] (off the main thread); failures other than [WhirlpoolError] mean the markup moved and
 * become [WhirlpoolError.Drift].
 */
internal suspend fun <T> parseOrDrift(dispatcher: CoroutineDispatcher, parse: () -> T): T = withContext(dispatcher) {
    try {
        parse()
    } catch (e: WhirlpoolError) {
        throw e
    } catch (e: RuntimeException) {
        throw WhirlpoolError.Drift("Could not read the page", e)
    }
}
