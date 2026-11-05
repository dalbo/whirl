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
}
