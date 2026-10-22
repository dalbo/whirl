package au.howesthat.whirl.core.data

import java.time.LocalTime

/** User settings, carried over from Whirldroid 2.14.12 (PLAN.md §1.2) minus the dropped whim options. */
data class Settings(
    val homepage: Homepage = Homepage.FORUMS,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /**
     * Settings › Colours: a palette id (`core/ui/src/main/assets/palettes/<id>.json`) or "wallpaper" (Material You,
     * Android 12+). Replaces v1.0's "Use wallpaper colours" (`dynamic_color`), which it is read from until first saved.
     */
    val palette: String = "wallpaper",
    /** Settings › Layout: a layout id (`core/ui/src/main/assets/layouts/<id>.json`). */
    val layout: String = "classic",
    val nightStart: LocalTime = LocalTime.of(22, 0),
    val nightEnd: LocalTime = LocalTime.of(7, 0),
    val postFontSize: PostFontSize = PostFontSize.NORMAL,
    /** Scales all text in the app on top of Android's font size. */
    val textScale: TextScale = TextScale.DEFAULT,
    /** Slide and fade between screens; off switches screens at once. */
    val screenAnimations: Boolean = true,
    /** Whirlpool pages (replies, private forums, "Open in browser") open in the browser app, not a Custom Tab. */
    val browserApp: Boolean = false,
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

/** The start screen. [LATEST] (site-wide latest activity, `get=threads`) is new; 2.14.12 had the other five. */
enum class Homepage { NEWS, RECENT, LATEST, WATCHED, POPULAR, FORUMS }

/** 2.14.12 offered Light, Dark and "Dark at night"; following the system is the modern default. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, DARK_AT_NIGHT }

/**
 * 2.14.12's −2/−1/0/0.5/1/2 steps as post text sizes in sp; its "normal" was 17 sp (`post_font_size_normal`). The names
 * are stored, so LARGER (19) and LARGE (20) keep their names although their labels read Large and Larger.
 */
enum class PostFontSize(val sp: Float) { SMALLEST(14f), SMALLER(16f), NORMAL(17f), LARGER(19f), LARGE(20f), LARGEST(22f) }

/** App-wide text size, multiplied with Android's font size. */
enum class TextScale(val factor: Float) { DEFAULT(1f), LARGE(1.15f), LARGER(1.3f), LARGEST(1.5f) }

/** The account the API key belongs to. */
data class Account(val userId: Long, val userName: String)
