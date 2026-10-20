package au.howesthat.whirl.navigation

import au.howesthat.whirl.core.data.Homepage
import kotlinx.serialization.Serializable

@Serializable object SettingsRoute
@Serializable object AboutRoute

// Drawer destinations.
@Serializable object NewsRoute
@Serializable object RecentRoute
@Serializable object LatestRoute
@Serializable object WatchedRoute
@Serializable object PopularRoute
@Serializable object ForumsRoute

fun Homepage.route(): Any = when (this) {
    Homepage.NEWS -> NewsRoute
    Homepage.RECENT -> RecentRoute
    Homepage.LATEST -> LatestRoute
    Homepage.WATCHED -> WatchedRoute
    Homepage.POPULAR -> PopularRoute
    Homepage.FORUMS -> ForumsRoute
}

/** A thread page; [page] may be -1 for the last page, [scrollToPost] is a post number. Its screen arrives in task 5. */
@Serializable
data class ThreadRoute(val threadId: String, val page: Int = 1, val scrollToPost: Int? = null, val forumId: Int? = null)
