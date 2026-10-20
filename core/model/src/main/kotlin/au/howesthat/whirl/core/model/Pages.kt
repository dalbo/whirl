package au.howesthat.whirl.core.model

/** A relevance-ordered search result (`/search?q=`). */
data class SearchResult(
    val rank: Int,
    val threadId: ThreadId,
    val title: String,
    val forum: ForumRef?,
    /** "3 months ago → 3 days ago" as two parts. */
    val firstPostAge: String?,
    val lastPostAge: String?,
    val snippet: String,
)

/** The public forum index (`/`). */
data class ForumIndex(val sections: List<ForumIndexSection>)

data class ForumIndexSection(val title: String, val forums: List<ForumIndexEntry>)

data class ForumIndexEntry(val forum: ForumRef, val latest: List<LatestThread>)

data class LatestThread(val id: ThreadId, val title: String, val ago: String?)

/** A user's profile page (`/user/<id>`). */
data class UserProfile(
    val user: UserRef,
    /** "About this user" rows in page order, labels without the trailing colon ("Status", "Joined", ...). */
    val about: List<Pair<String, String>>,
    val recentActivity: List<ThreadRow>,
)
