package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.model.UserProfile
import au.howesthat.whirl.core.network.WhirlpoolWeb
import au.howesthat.whirl.core.scraper.UserProfileParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Scraped user profiles (`/user/<id>`), kept in memory for [MaxAge.THREAD_LISTS] like other scraped pages. */
@Singleton
class UserRepository @Inject constructor(private val web: WhirlpoolWeb, private val clock: Clock) {
    private val refresher = Refresher(log = null, clock = clock)
    private val cache = MutableStateFlow<Map<Long, Cached<UserProfile>>>(emptyMap())

    fun profile(userId: Long): Flow<Cached<UserProfile>?> = cache.map { it[userId] }

    suspend fun refresh(userId: Long, force: Boolean = false): Boolean =
        refresher.refresh("user:$userId", MaxAge.THREAD_LISTS, force, lastFetched = { cache.value[userId]?.updatedAt?.toEpochMilli() }) {
            val profile = parseOrDrift { UserProfileParser.parse(web.get(web.urls.user(userId))) }
            val entry = Cached(profile, Instant.ofEpochMilli(clock.millis()))
            cache.update { old -> (old - userId).entries.sortedByDescending { it.value.updatedAt }.take(KEEP - 1).associate { it.toPair() } + (userId to entry) }
        }

    private companion object {
        const val KEEP = 20
    }
}
