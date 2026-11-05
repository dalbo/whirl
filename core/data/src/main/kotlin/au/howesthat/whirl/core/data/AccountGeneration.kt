package au.howesthat.whirl.core.data

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts account switches (signing out, or into another account), so an answer fetched for one account is not stored
 * after the switch has cleared its data. A fetch reads [current] before it calls Whirlpool and stores only if it is
 * unchanged, checked in the same database transaction as the write; the switch moves it on in the transaction that
 * clears the data.
 */
@Singleton
class AccountGeneration @Inject constructor() {
    private val value = AtomicInteger()

    val current: Int get() = value.get()

    internal fun next() {
        value.incrementAndGet()
    }

    /**
     * [fetch]es an account's data and [store]s it. [store] gets the generation read before the fetch and returns null,
     * writing nothing, if it has changed (checked in the transaction that writes). An answer dropped that way may have
     * used the old key, or the new one read while the switch was still finishing, so it is fetched once more: the new
     * account's first lists are not lost. Null if that answer is dropped too.
     */
    suspend fun <T, R : Any> fetchAndStore(fetch: suspend () -> T, store: suspend (gen: Int, T) -> R?): R? {
        repeat(2) {
            val gen = current
            store(gen, fetch())?.let { return it }
        }
        return null
    }
}
