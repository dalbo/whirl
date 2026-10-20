package au.howesthat.whirl.core.network

import au.howesthat.whirl.core.model.ApiUser
import au.howesthat.whirl.core.model.Forum
import au.howesthat.whirl.core.model.NewsItem
import au.howesthat.whirl.core.model.ThreadId
import au.howesthat.whirl.core.model.ThreadSummary
import au.howesthat.whirl.core.model.UserRef
import au.howesthat.whirl.core.model.WatchedThread
import au.howesthat.whirl.core.model.WhirlpoolError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.parser.Parser
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * The Whirlpool JSON API (`https://whirlpool.net.au/api/?key=K&output=json&get=a+b`).
 *
 * Every response carries an envelope: `ALERT` (site notice, published on [alert]), `AUTH` (the key's user), `ERROR`.
 * A bad key is HTTP 403 with `ERROR` set. Unknown or removed `get=` values (whims) return the bare envelope, so a
 * requested key that is missing from the response is treated as [WhirlpoolError.Drift].
 */
class WhirlpoolApi(
    private val client: OkHttpClient,
    private val apiKey: suspend () -> String?,
    private val baseUrl: HttpUrl = "https://whirlpool.net.au/api/".toHttpUrl(),
    private val retry: RetryPolicy = RetryPolicy(),
) {
    private val _alert = MutableStateFlow("")

    /** The latest non-error `ALERT` text from the API (empty when none). Shown verbatim. */
    val alert: StateFlow<String> = _alert.asStateFlow()

    /** Validates a key: returns its user or throws [WhirlpoolError.BadKey]. Used by the setup wizard. */
    suspend fun user(key: String? = null): ApiUser {
        val env = call(setOf("user"), key = key)
        val auth = env.auth ?: throw WhirlpoolError.Drift("API response has no AUTH")
        if (auth.id == 0L) throw WhirlpoolError.BadKey(env.error.ifEmpty { "Not authenticated. Bad API key?" })
        return ApiUser(auth.id, auth.name)
    }

    suspend fun forums(): List<Forum> =
        call(setOf("forum")).forums.required("FORUM").map { Forum(it.id, it.title, it.section) }

    suspend fun news(): List<NewsItem> =
        call(setOf("news")).news.required("NEWS").map { NewsItem(it.id, it.title, it.source, it.blurb, parseApiDate(it.date)) }

    /**
     * Threads, most recently active first. With no [forumIds] this is the 30 (or [count]) most recently active threads
     * site-wide. Works for some private forums (35) as a list.
     */
    suspend fun threads(forumIds: Collection<Int> = emptyList(), count: Int? = null): List<ThreadSummary> {
        require(count == null || count in 1..100) { "threadcount must be 1..100" }
        val params = buildMap {
            if (forumIds.isNotEmpty()) put("forumids", forumIds.joinToString(","))
            count?.let { put("threadcount", it.toString()) }
        }
        return call(setOf("threads"), params).threads.required("THREADS").map(::toSummary)
    }

    /** Threads the user posted in recently. Only ever seen empty; the shape is assumed to match [threads]. */
    suspend fun recent(days: Int? = null): List<ThreadSummary> {
        val params = days?.let { mapOf("recentdays" to it.toString()) }.orEmpty()
        return call(setOf("recent"), params).recent.required("RECENT").map(::toSummary)
    }

    /** Watched threads. Always passes `watchedmode`: without it the API returns a third, undocumented subset. */
    suspend fun watched(unreadOnly: Boolean): List<WatchedThread> =
        call(setOf("watched"), mapOf("watchedmode" to if (unreadOnly) "0" else "1")).watched.required("WATCHED").map {
            WatchedThread(
                id = ThreadId(it.id),
                title = unescape(it.title),
                replies = it.replies,
                forumId = it.forumId,
                forumName = it.forumName,
                firstDate = parseApiDate(it.firstDate),
                firstPoster = UserRef(it.first.id, it.first.name),
                lastDate = parseApiDate(it.lastDate),
                lastPoster = UserRef(it.last.id, it.last.name),
                unreadCount = it.unread,
                lastReadPost = it.lastRead,
                lastPage = maxOf(it.lastPage, 1),
            )
        }

    // Documented commands taking comma-separated thread ids; their responses are not yet captured (Phase 3).
    suspend fun watch(ids: Collection<ThreadId>) = command("watchedadd", ids)
    suspend fun markRead(ids: Collection<ThreadId>) = command("watchedread", ids)
    suspend fun unwatch(ids: Collection<ThreadId>) = command("watchedremove", ids)

    private suspend fun command(name: String, ids: Collection<ThreadId>) {
        require(ids.isNotEmpty())
        call(emptySet(), mapOf(name to ids.joinToString(",") { it.value }))
    }

    private suspend fun call(gets: Set<String>, params: Map<String, String> = emptyMap(), key: String? = null): ApiEnvelope {
        val apiKey = key ?: apiKey() ?: throw WhirlpoolError.BadKey("No API key set")
        val url = baseUrl.newBuilder()
            .addQueryParameter("key", apiKey)
            .addQueryParameter("output", "json")
            .apply { if (gets.isNotEmpty()) addEncodedQueryParameter("get", gets.joinToString("+")) }
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val response = client.fetch(Request.Builder().url(url).build(), retry)
        val env = try {
            json.decodeFromString(ApiEnvelope.serializer(), response.body)
        } catch (e: SerializationException) {
            throw if (response.code == 403) WhirlpoolError.Api("HTTP 403", 403) else WhirlpoolError.Drift("Unexpected API response", e)
        } catch (e: IllegalArgumentException) {
            throw WhirlpoolError.Drift("Unexpected API response", e)
        }
        when {
            response.code == 403 || (env.auth?.id == 0L && env.error.isNotEmpty()) ->
                throw WhirlpoolError.BadKey(env.error.ifEmpty { "Not authenticated. Bad API key?" })
            env.error.isNotEmpty() -> throw WhirlpoolError.Api(env.error, response.code)
            response.code !in 200..299 -> throw WhirlpoolError.Api("HTTP ${response.code}", response.code)
        }
        _alert.value = env.alert
        return env
    }

    private fun toSummary(it: ThreadDto) = ThreadSummary(
        id = ThreadId(it.id),
        title = unescape(it.title),
        replies = it.replies,
        forumId = it.forumId,
        forumName = it.forumName,
        group = it.group.ifEmpty { null },
        firstDate = parseApiDate(it.firstDate),
        firstPoster = UserRef(it.first.id, it.first.name),
        lastDate = parseApiDate(it.lastDate),
        lastPoster = UserRef(it.last.id, it.last.name),
        isSubscribed = it.subscribed,
        isUnread = it.unread,
        hasParticipated = it.participated,
    )

    private fun <T> List<T>?.required(key: String): List<T> =
        this ?: throw WhirlpoolError.Drift("API response has no $key")

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        // `2026-10-19T20:39:50+1000`: no colon in the offset, which OffsetDateTime.parse rejects.
        private val API_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx")

        fun parseApiDate(text: String): OffsetDateTime = try {
            OffsetDateTime.parse(text, API_DATE)
        } catch (e: DateTimeParseException) {
            throw WhirlpoolError.Drift("Unexpected API date '$text'", e)
        }

        /** Thread titles are HTML-escaped (`&amp;`); news titles are not, so this is applied to threads only. */
        internal fun unescape(title: String): String = Parser.unescapeEntities(title, false)
    }
}
