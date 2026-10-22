package au.howesthat.whirl.core.data

import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts the times the app has come to the foreground (process start or return from the background, not rotation or
 * moving between screens). The app module calls [onForeground] from the process lifecycle.
 */
@Singleton
class AppVisits @Inject constructor() {
    private val count = AtomicInteger()

    /** The current visit; changes each time the app comes to the foreground. */
    val current: Int get() = count.get()

    fun onForeground() {
        count.incrementAndGet()
    }
}
