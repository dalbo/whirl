package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.network.WhirlpoolUrls

/** Where tapping a thread goes. */
sealed interface OpenThread {
    /**
     * [page] may be [ThreadPageRepository.LAST_PAGE]; [scrollToPost] is a post number (1-based in the thread);
     * [fromWatched] offers Mark as read and Unwatch, and "back to list" after marking read. [lastReadPost] is the
     * last post read (`LASTREAD`), to mark where the new posts start, even when the thread opens at the top.
     */
    data class InApp(
        val id: ThreadId,
        val page: Int,
        val scrollToPost: Int? = null,
        val forumId: Int? = null,
        val fromWatched: Boolean = false,
        val lastReadPost: Int? = null,
    ) : OpenThread
    data class Browser(val url: String) : OpenThread
}

/** The list a thread was opened from, which decides the opening position. */
enum class OpenedFrom { WATCHED, RECENT, LIST }

/** Read position of a watched thread (`LASTPAGE`, `LASTREAD`, `UNREAD`). */
data class WatchedPosition(val lastPage: Int, val lastReadPost: Int, val unreadCount: Int) {
    /** The first post not read yet; null with nothing unread, or nothing read yet (the thread opens at its start). */
    val firstUnread: Int? get() = (lastReadPost + 1).takeIf { lastReadPost > 0 && unreadCount > 0 }

    /** Where a watched thread opens: the first unread post, else the last read one; null with nothing read. */
    val openAt: Int? get() = firstUnread ?: lastReadPost.takeIf { it > 0 }

    /**
     * The page of [openAt]. `LASTPAGE` is the page of the last read post, so the first unread post is on the next page
     * only when the last read one ends a page.
     */
    val openPage: Int get() = if (firstUnread != null && lastReadPost % POSTS_PER_PAGE == 0) lastPage + 1 else lastPage
}

/**
 * The opening rules of 2.14.12 `ForumPageFragment.openThread` (PLAN.md §1.1):
 * - watched → the first unread post at the top of the screen (the last read post with nothing unread; 2.14.12
 *   scrolled to the last read post); "open watched at top" opens that post's page at its top;
 * - recent → last page unless "open recent at top";
 * - other lists → last page if "open public threads at the last page";
 * - private forums, and everything when "open threads in the browser" is on → the browser, at the same position
 *   (`?p=N#rM` for watched, `?p=-1#bottom` for last-page cases; private threads also honour "open private at bottom").
 */
class ThreadOpener(private val urls: WhirlpoolUrls = WhirlpoolUrls()) {

    fun open(
        id: ThreadId,
        forumId: Int?,
        isPrivateForum: Boolean,
        from: OpenedFrom,
        settings: Settings,
        watched: WatchedPosition? = null,
    ): OpenThread {
        val atLastPage = when (from) {
            OpenedFrom.WATCHED -> false
            OpenedFrom.RECENT -> !settings.loadRecentAtTop
            OpenedFrom.LIST -> if (isPrivateForum) settings.loadPrivateAtBottom else settings.loadPublicAtBottom
        }
        if (isPrivateForum || settings.openThreadsInBrowser) {
            val url = when {
                watched != null && from == OpenedFrom.WATCHED ->
                    urls.thread(id, watched.openPage).newBuilder().fragment(watched.openAt?.let { "r$it" }).build()
                atLastPage -> urls.thread(id, ThreadPageRepository.LAST_PAGE).newBuilder().fragment("bottom").build()
                else -> urls.thread(id)
            }
            return OpenThread.Browser(url.toString())
        }
        return when {
            from == OpenedFrom.WATCHED && watched != null -> OpenThread.InApp(
                id,
                page = watched.openPage,
                // The first unread post at the top of the screen, under its "New posts" marker.
                scrollToPost = watched.openAt.takeIf { !settings.loadWatchedAtTop },
                forumId = forumId,
                fromWatched = true,
                lastReadPost = watched.lastReadPost.takeIf { it > 0 },
            )
            atLastPage -> OpenThread.InApp(id, ThreadPageRepository.LAST_PAGE, forumId = forumId)
            else -> OpenThread.InApp(id, 1, forumId = forumId)
        }
    }

    /** "Jump to last post": the last page, in the browser for private forums. */
    fun lastPost(id: ThreadId, forumId: Int?, isPrivateForum: Boolean, settings: Settings): OpenThread =
        if (isPrivateForum || settings.openThreadsInBrowser) {
            OpenThread.Browser(urls.thread(id, ThreadPageRepository.LAST_PAGE).newBuilder().fragment("bottom").build().toString())
        } else {
            OpenThread.InApp(id, ThreadPageRepository.LAST_PAGE, forumId = forumId)
        }

    fun browserUrl(id: ThreadId): String = urls.thread(id).toString()
}
