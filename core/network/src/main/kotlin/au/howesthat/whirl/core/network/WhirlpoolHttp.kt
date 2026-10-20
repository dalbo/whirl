package au.howesthat.whirl.core.network

import au.howesthat.whirl.core.model.WhirlpoolError
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Shared HTTP plumbing for the API and the web pages. Verified 2026-10-20 on the owner's phone: OkHttp passes Whirlpool's
 * Cloudflare challenge on Android with any User-Agent (tools/spike); it does not from a desktop JVM.
 *
 * The API key travels in the query string, so nothing here logs request URLs or puts them in exception messages.
 */
object WhirlpoolHttp {

    fun client(userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build()) }
        .build()
}

/** How often and how long to back off on 5xx/429/509. Never retries a challenge, a 403 or a parse failure. */
data class RetryPolicy(val maxRetries: Int = 2, val baseDelay: Duration = 2.seconds, val maxDelay: Duration = 30.seconds) {
    companion object {
        val NONE = RetryPolicy(maxRetries = 0)
    }
}

internal class HttpBody(val code: Int, val body: String)

/**
 * Executes [request], retrying transient failures per [retry], and returns the body of any response that is not a
 * Cloudflare challenge, rate limit or server error. 4xx bodies are returned for the caller to interpret.
 */
internal suspend fun OkHttpClient.fetch(request: Request, retry: RetryPolicy): HttpBody {
    var attempt = 0
    while (true) {
        val error = try {
            newCall(request).executeAsync().use { response -> return classify(response) }
        } catch (e: WhirlpoolError) {
            e
        } catch (e: IOException) {
            WhirlpoolError.Network(e)
        }
        val retryable = error is WhirlpoolError.Maintenance || error is WhirlpoolError.RateLimited
        if (!retryable || attempt >= retry.maxRetries) throw error
        val wait = (error as? WhirlpoolError.RateLimited)?.retryAfterSeconds?.seconds
            ?: (retry.baseDelay * (1 shl attempt))
        delay(minOf(wait, retry.maxDelay))
        attempt++
    }
}

private fun classify(response: Response): HttpBody {
    if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) throw WhirlpoolError.Challenge()
    val code = response.code
    when {
        code == 429 || code == 509 -> throw WhirlpoolError.RateLimited(response.header("Retry-After")?.trim()?.toLongOrNull())
        code >= 500 -> throw WhirlpoolError.Maintenance(code)
    }
    val body = response.body.string()
    if (isChallengePage(body)) throw WhirlpoolError.Challenge()
    return HttpBody(code, body)
}

/** Cloudflare's interstitial, in case it arrives without the `cf-mitigated` header. */
internal fun isChallengePage(body: String): Boolean {
    val head = body.take(4096)
    return head.contains("<title>Just a moment", ignoreCase = true) || head.contains("challenges.cloudflare.com")
}
