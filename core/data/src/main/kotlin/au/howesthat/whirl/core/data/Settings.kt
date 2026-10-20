package au.howesthat.whirl.core.data

import java.time.LocalTime

/** User settings, carried over from Whirldroid 2.14.12 (PLAN.md §1.2) minus the dropped whim options. */
data class Settings(
    val homepage: Homepage = Homepage.FORUMS,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val nightStart: LocalTime = LocalTime.of(22, 0),
    val nightEnd: LocalTime = LocalTime.of(7, 0),
    val postFontSize: PostFontSize = PostFontSize.NORMAL,
    // Notifications
    val watchedNotify: Boolean = false,
    val notifyIntervalMinutes: Int = 60,
    // Threads
    val openThreadsInBrowser: Boolean = false,
    val loadPublicAtBottom: Boolean = false,
    val loadPrivateAtBottom: Boolean = false,
    // Recent
    val recentHistoryDays: Int = 7,
    val loadRecentAtTop: Boolean = false,
    val ignoreOwnRepliesRecent: Boolean = false,
    // Watched
    val loadWatchedAtTop: Boolean = false,
    val ignoreOwnRepliesWatched: Boolean = false,
    val watchedAutoMarkAsRead: Boolean = false,
    val watchedBackToList: Boolean = false,
) {
    companion object {
        val NOTIFY_INTERVALS = listOf(15, 30, 60, 240, 720, 1440)
        val RECENT_DAYS = listOf(1, 3, 7, 14, 30, 60, 120)
    }
}

enum class Homepage { NEWS, RECENT, WATCHED, POPULAR, FORUMS }

/** 2.14.12 offered Light, Dark and "Dark at night"; following the system is the modern default. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, DARK_AT_NIGHT }

/** 2.14.12's −2/−1/0/0.5/1/2 steps, as text sizes in sp (null = the theme's body size). */
enum class PostFontSize(val sp: Float?) { SMALLEST(14f), SMALLER(16f), NORMAL(null), LARGER(19f), LARGE(20f), LARGEST(22f) }

/** The account the API key belongs to. */
data class Account(val userId: Long, val userName: String)
