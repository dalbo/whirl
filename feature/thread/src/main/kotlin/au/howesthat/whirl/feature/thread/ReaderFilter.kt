package au.howesthat.whirl.feature.thread

import au.howesthat.whirl.core.data.ArchiveRepository.ScanQuery
import au.howesthat.whirl.core.data.PostFilter
import au.howesthat.whirl.core.model.TextQuery

/**
 * What the scroll reader shows instead of every post: one poster's posts, the OP's, moderators', reps', or posts
 * matching a search. Saved as [code] (in the route and across process death).
 */
sealed interface ReaderFilter {
    val code: String

    data class User(val userId: Long, val name: String) : ReaderFilter {
        override val code get() = "u:$userId:$name"
    }

    /** The signed-in user's posts; the reader knows their id. */
    data object Mine : ReaderFilter { override val code = "me" }
    data object Op : ReaderFilter { override val code = "op" }
    data object Moderators : ReaderFilter { override val code = "mods" }
    data object Reps : ReaderFilter { override val code = "reps" }

    data class Text(val query: String) : ReaderFilter {
        override val code get() = "t:$query"
    }

    companion object {
        fun parse(code: String?): ReaderFilter? = when {
            code == null -> null
            code == Mine.code -> Mine
            code == Op.code -> Op
            code == Moderators.code -> Moderators
            code == Reps.code -> Reps
            code.startsWith("u:") -> code.removePrefix("u:").split(':', limit = 2).let { parts ->
                parts[0].toLongOrNull()?.let { User(it, parts.getOrElse(1) { "" }) }
            }
            code.startsWith("t:") -> Text(code.removePrefix("t:")).takeIf { it.query.isNotBlank() }
            else -> null
        }
    }
}

/** The repository's query for [this]; null for [ReaderFilter.Mine] without an account. */
internal fun ReaderFilter.scanQuery(myUserId: Long?): ScanQuery? = when (this) {
    is ReaderFilter.User -> ScanQuery.Filter(PostFilter.User(userId))
    ReaderFilter.Mine -> myUserId?.let { ScanQuery.Filter(PostFilter.User(it)) }
    ReaderFilter.Op -> ScanQuery.Filter(PostFilter.Op)
    ReaderFilter.Moderators -> ScanQuery.Filter(PostFilter.Moderators)
    ReaderFilter.Reps -> ScanQuery.Filter(PostFilter.Reps)
    is ReaderFilter.Text -> ScanQuery.Text(TextQuery(query))
}
