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

/** A user's profile; its screen arrives in task 6. */
@Serializable
data class UserRoute(val userId: Long)
