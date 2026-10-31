package au.howesthat.whirl.navigation

import androidx.navigation3.runtime.NavKey
import au.howesthat.whirl.core.data.Homepage
import au.howesthat.whirl.feature.thread.ThreadRoute
import au.howesthat.whirl.feature.threadlist.ForumRoute
import au.howesthat.whirl.feature.threadlist.SearchRoute
import au.howesthat.whirl.feature.user.UserRoute
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

@Serializable object SettingsRoute : NavKey
@Serializable object AboutRoute : NavKey
@Serializable object CheckHistoryRoute : NavKey

// Drawer destinations.
@Serializable object NewsRoute : NavKey
@Serializable object RecentRoute : NavKey
@Serializable object LatestRoute : NavKey
@Serializable object WatchedRoute : NavKey
@Serializable object PopularRoute : NavKey
@Serializable object ForumsRoute : NavKey

fun Homepage.route(): NavKey = when (this) {
    Homepage.NEWS -> NewsRoute
    Homepage.RECENT -> RecentRoute
    Homepage.LATEST -> LatestRoute
    Homepage.WATCHED -> WatchedRoute
    Homepage.POPULAR -> PopularRoute
    Homepage.FORUMS -> ForumsRoute
}

/** Every route, so the back stacks can be saved across process death. A new route must be added here. */
val RouteSerializers = SerializersModule {
    polymorphic(NavKey::class) {
        subclass(SettingsRoute::class)
        subclass(AboutRoute::class)
        subclass(CheckHistoryRoute::class)
        subclass(NewsRoute::class)
        subclass(RecentRoute::class)
        subclass(LatestRoute::class)
        subclass(WatchedRoute::class)
        subclass(PopularRoute::class)
        subclass(ForumsRoute::class)
        subclass(ThreadRoute::class)
        subclass(ForumRoute::class)
        subclass(SearchRoute::class)
        subclass(UserRoute::class)
    }
}
