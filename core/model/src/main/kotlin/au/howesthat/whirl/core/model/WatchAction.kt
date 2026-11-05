package au.howesthat.whirl.core.model

/** What a watched-thread action does. Stored by name in the queue of actions waiting to be sent. */
enum class WatchAction { WATCH, MARK_READ, UNWATCH }
