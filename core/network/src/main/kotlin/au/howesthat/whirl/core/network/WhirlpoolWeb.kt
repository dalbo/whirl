package au.howesthat.whirl.core.network

import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.WhirlpoolError
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Fetches raw forum HTML for the parsers in `:core:scraper`. No cookies: every page is the logged-out view. */
class WhirlpoolWeb(
    private val client: OkHttpClient,
    val urls: WhirlpoolUrls = WhirlpoolUrls(),
    private val retry: RetryPolicy = RetryPolicy(),
) {
    /** Returns the page body. "Not signed in" pages come back as 200 and are recognised by the parsers. */
    suspend fun get(url: HttpUrl): String {
        val response = client.fetch(Request.Builder().url(url).build(), retry)
        if (response.code !in 200..299) throw WhirlpoolError.Api("HTTP ${response.code}", response.code)
        return response.body
    }
}

/** URL forms verified against the live site (PLAN.md §2.3). */
class WhirlpoolUrls(val forums: HttpUrl = "https://forums.whirlpool.net.au/".toHttpUrl()) {

    fun index(): HttpUrl = forums

    fun forum(id: Int, page: Int = 1, group: Int? = null): HttpUrl = forums.newBuilder()
        .addPathSegments("forum/$id")
        .apply { if (page > 1) addQueryParameter("p", page.toString()) }
        .apply { group?.let { addQueryParameter("g", it.toString()) } }
        .build()

    /** [page] -1 is the last page; the server resolves it and reports the real page number in the inline vars. */
    fun thread(id: ThreadId, page: Int = 1, userId: Long? = null, moderatorsOnly: Boolean = false, repsOnly: Boolean = false): HttpUrl =
        forums.newBuilder()
            .addPathSegments("thread/${id.value}")
            .apply { if (page != 1) addQueryParameter("p", page.toString()) }
            .apply { userId?.let { addQueryParameter("ux", it.toString()) } }
            .apply { if (moderatorsOnly) addQueryParameter("um", "1") }
            .apply { if (repsOnly) addQueryParameter("ur", "1") }
            .build()

    /** The whole thread on one page. */
    fun archive(id: ThreadId): HttpUrl = forums.newBuilder().addPathSegments("archive/${id.value}").build()

    fun popular(): HttpUrl = forums.newBuilder().addPathSegment("forum").addPathSegment("")
        .addQueryParameter("action", "popular_views").build()

    /** Locates a post by id: redirects to the thread page that contains it. */
    fun reply(postId: Long): HttpUrl = forums.newBuilder().addPathSegment("forum").addPathSegment("")
        .addQueryParameter("action", "replies").addQueryParameter("r", postId.toString()).build()

    fun user(id: Long, days: Int? = null): HttpUrl = forums.newBuilder()
        .addPathSegments("user/$id")
        .apply { days?.let { addQueryParameter("days", it.toString()) } }
        .build()

    enum class SearchIn(val param: String) { TITLES("t"), BODIES("b"), BOTH("tb") }

    /** Thread-table search results, 100 per page (what 2.14.12 used). Parse with ThreadListParser. */
    fun searchList(query: String, forum: Int? = null, group: Int? = null, searchIn: SearchIn = SearchIn.TITLES, page: Int = 1): HttpUrl =
        forums.newBuilder().addPathSegment("forum").addPathSegment("")
            .addQueryParameter("action", "search")
            .addQueryParameter("q", query)
            .addQueryParameter("f", forum?.toString().orEmpty())
            .addQueryParameter("fg", group?.toString().orEmpty())
            .addQueryParameter("w", searchIn.param)
            .addQueryParameter("o", "list")
            .apply { if (page > 1) addQueryParameter("p", page.toString()) }
            .build()

    /** Relevance-ordered search, 50 per page, no page count: step [page] until empty. Parse with SearchResultsParser. */
    fun search(query: String, forum: Int? = null, group: Int? = null, searchIn: SearchIn = SearchIn.BOTH, page: Int = 1): HttpUrl =
        forums.newBuilder().addPathSegment("search")
            .addQueryParameter("q", query)
            .apply { forum?.let { addQueryParameter("f", it.toString()) } }
            .apply { group?.let { addQueryParameter("fg", it.toString()) } }
            .addQueryParameter("w", searchIn.param)
            .apply { if (page > 1) addQueryParameter("p", page.toString()) }
            .build()
}
