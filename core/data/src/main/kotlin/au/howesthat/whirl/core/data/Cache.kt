package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.database.FetchLogDao
import au.howesthat.whirl.core.database.FetchLogEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
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

/**
 * Values kept in memory only, each with when it was fetched; the [keep] most recently fetched are kept. Also a
 * [Refresher] for them, which needs no fetch log.
 */
class MemoryCache<K : Any, V>(private val keep: Int, private val clock: Clock) {
    private val entries = MutableStateFlow<Map<K, Cached<V>>>(emptyMap())
    private val refresher = Refresher(log = null, clock = clock)

    /** [key]'s value; a refresh of another key does not emit it again. */
    fun observe(key: K): Flow<Cached<V>?> = entries.map { it[key] }.distinctUntilChanged()

    /** Runs [fetch] if [key] is older than [maxAge] (or forced, past the throttle) and keeps what it returns. */
    suspend fun refresh(key: K, maxAge: Duration, force: Boolean, fetch: suspend () -> V): Boolean =
        refresher.refresh(key.toString(), maxAge, force, lastFetched = { entries.value[key]?.updatedAt?.toEpochMilli() }) {
            val entry = Cached(fetch(), Instant.ofEpochMilli(clock.millis()))
            entries.update { old -> (old - key).entries.sortedByDescending { it.value.updatedAt }.take(keep - 1).associate { it.toPair() } + (key to entry) }
        }
}

/** Max ages from Whirldroid 2.14.12 (PLAN.md §1.4). */
object MaxAge {
    val NEWS = 60.minutes
    val THREAD_LISTS = 10.minutes
    val FORUMS = 7.days
    /** Thread pages render from the cache at once and refetch when older than this. */
    val THREAD_PAGE = 1.minutes
    /** A manual refresh inside this window reuses the last result. */
    val MANUAL_THROTTLE = 10.seconds
}

/**
 * Stale-while-revalidate bookkeeping: decides whether a list needs fetching and records when it was fetched.
 * One fetch per key at a time; concurrent callers wait and then see it as fresh.
 */
class Refresher(private val log: FetchLogDao?, private val clock: Clock) {
    /** A key's lock and how many callers hold or wait for it; dropped when the last one is done. */
    private class Lock {
        val mutex = Mutex()
        var users = 0
    }

    /** Only the keys being refreshed now: users and searches are keys too, so a map of every key seen would only grow. */
    private val locks = mutableMapOf<String, Lock>()

    internal val lockCount: Int get() = synchronized(locks) { locks.size }

    private suspend fun <T> withKey(key: String, block: suspend () -> T): T {
        val lock = synchronized(locks) { locks.getOrPut(key, ::Lock).also { it.users++ } }
        try {
            return lock.mutex.withLock { block() }
        } finally {
            synchronized(locks) { if (--lock.users == 0) locks.remove(key) }
        }
    }

    /**
     * Runs [fetch] if [key] is older than [maxAge], or if [force] and older than [MaxAge.MANUAL_THROTTLE].
     * Returns true if it fetched. Errors propagate and leave the old timestamp in place.
     */
    suspend fun refresh(key: String, maxAge: Duration, force: Boolean, fetch: suspend () -> Unit): Boolean {
        val log = checkNotNull(log) { "This Refresher has no fetch log" }
        return refresh(key, maxAge, force, lastFetched = { log.fetchedAt(key) }) {
            fetch()
            log.put(FetchLogEntity(key, clock.millis()))
        }
    }

    /** As above, for data that records its own fetch time ([lastFetched]) instead of using the fetch log. */
    suspend fun refresh(
        key: String,
        maxAge: Duration,
        force: Boolean,
        lastFetched: suspend () -> Long?,
        fetch: suspend () -> Unit,
    ): Boolean = withKey(key) {
        if (isFresh(lastFetched(), maxAge, force)) return@withKey false
        fetch()
        true
    }

    private fun isFresh(fetchedAt: Long?, maxAge: Duration, force: Boolean): Boolean {
        val age = fetchedAt?.let { (clock.millis() - it).coerceAtLeast(0) } ?: return false
        return age < (if (force) MaxAge.MANUAL_THROTTLE else maxAge).inWholeMilliseconds
    }
}
