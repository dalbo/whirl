package au.howesthat.whirl.core.notifications

import au.howesthat.whirl.core.model.WatchedThread

/** What one notification check decided. */
data class AlertPlan(
    /** Unread threads to list, after "ignore my own replies". Empty means cancel the notification. */
    val threads: List<WatchedThread>,
    /** Some thread has a reply newer than the one last notified for it, so the notification should alert. */
    val hasNew: Boolean,
)

/**
 * Decides what to notify from the unread watched threads and what was notified before ([notified]: thread id →
 * `LAST_DATE` in epoch milliseconds). 2.14.12 kept a single last-notify time; per-thread dates (PLAN.md §3) mean a
 * thread is announced once per new reply, and a thread read elsewhere and replied to again is announced again.
 */
fun planAlert(unread: List<WatchedThread>, notified: Map<String, Long>, myUserId: Long?, ignoreOwnReplies: Boolean): AlertPlan {
    val threads = unread.filter { it.unreadCount > 0 && !(ignoreOwnReplies && myUserId != null && it.lastPoster.id == myUserId) }
    val hasNew = threads.any { it.lastDate.toInstant().toEpochMilli() > (notified[it.id.value] ?: Long.MIN_VALUE) }
    return AlertPlan(threads, hasNew)
}
