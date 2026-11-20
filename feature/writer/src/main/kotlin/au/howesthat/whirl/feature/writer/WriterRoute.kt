package au.howesthat.whirl.feature.writer

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The post writer for a thread (`docs/plans/post-writer.md` §5): a detail-pane entry like a thread, which it replaces
 * beside the list in two panes. [replyTo] is the post a reply was started from, for the site's "Reply to this post".
 */
@Serializable
data class WriterRoute(val threadId: String, val threadTitle: String, val replyTo: Long? = null) : NavKey
