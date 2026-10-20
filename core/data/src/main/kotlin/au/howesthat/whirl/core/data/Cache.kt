package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.FetchLogDao
import au.howesthat.whirl.core.database.FetchLogEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A cached value and when it was fetched; screens always render the cache with "Updated N ago". */
data class Cached<T>(val value: T, val updatedAt: Instant?)

/** Max ages from Whirldroid 2.14.12 (PLAN.md §1.4). */
object MaxAge {
    val NEWS = 60.minutes
    val THREAD_LISTS = 10.minutes
    val FORUMS = 7.days
    /** A manual refresh inside this window reuses the last result. */
    val MANUAL_THROTTLE = 10.seconds
}

/**
 * Stale-while-revalidate bookkeeping: decides whether a list needs fetching and records when it was fetched.
 * One fetch per key at a time; concurrent callers wait and then see it as fresh.
 */
class Refresher(private val log: FetchLogDao, private val clock: Clock) {
    private val locks = mutableMapOf<String, Mutex>()

    private fun lock(key: String) = synchronized(locks) { locks.getOrPut(key) { Mutex() } }

    /**
     * Runs [fetch] if [key] is older than [maxAge], or if [force] and older than [MaxAge.MANUAL_THROTTLE].
     * Returns true if it fetched. Errors propagate and leave the old timestamp in place.
     */
    suspend fun refresh(key: String, maxAge: Duration, force: Boolean, fetch: suspend () -> Unit): Boolean =
        lock(key).withLock {
            val last = log.fetchedAt(key)
            val age = last?.let { (clock.millis() - it).coerceAtLeast(0) }
            val limit = if (force) MaxAge.MANUAL_THROTTLE else maxAge
            if (age != null && age < limit.inWholeMilliseconds) return@withLock false
            fetch()
            log.put(FetchLogEntity(key, clock.millis()))
            true
        }
}
