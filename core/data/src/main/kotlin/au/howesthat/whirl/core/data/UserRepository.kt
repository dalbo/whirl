package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.model.UserProfile
import au.howesthat.whirl.core.network.WhirlpoolWeb
import au.howesthat.whirl.core.scraper.UserProfileParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Scraped user profiles (`/user/<id>`), kept in memory for [MaxAge.THREAD_LISTS] like other scraped pages. */
@Singleton
class UserRepository @Inject constructor(
    private val web: WhirlpoolWeb,
    private val clock: Clock,
    @DefaultDispatcher private val parsing: CoroutineDispatcher,
) {
    private val cache = MemoryCache<Long, UserProfile>(KEEP, clock)

    fun profile(userId: Long): Flow<Cached<UserProfile>?> = cache.observe(userId)

    suspend fun refresh(userId: Long, force: Boolean = false): Boolean =
        cache.refresh(userId, MaxAge.THREAD_LISTS, force) {
            val html = web.get(web.urls.user(userId))
            parseOrDrift(parsing) { UserProfileParser.parse(html) }
        }

    private companion object {
        const val KEEP = 20
    }
}
