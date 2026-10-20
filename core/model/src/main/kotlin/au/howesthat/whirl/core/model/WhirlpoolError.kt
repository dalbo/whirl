package au.howesthat.whirl.core.model

/** Every failure the app can show to the user. API `ERROR`/`ALERT` text is carried verbatim in [message]. */
sealed class WhirlpoolError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Cloudflare served a challenge instead of the page (`cf-mitigated: challenge` or a "Just a moment" body). */
    class Challenge : WhirlpoolError("Whirlpool's Cloudflare protection challenged the request")

    /** The API rejected the key (HTTP 403 with an `ERROR` envelope). */
    class BadKey(message: String) : WhirlpoolError(message)

    /** HTTP 429/509. */
    class RateLimited(val retryAfterSeconds: Long?) : WhirlpoolError("Whirlpool is rate limiting requests")

    /** HTTP 5xx or a maintenance page. */
    class Maintenance(val httpCode: Int) : WhirlpoolError("Whirlpool is unavailable (HTTP $httpCode)")

    /** No connection, timeout, TLS failure. */
    class Network(cause: Throwable) : WhirlpoolError(cause.message ?: "Network error", cause)

    /** The page needs a web login (private forums, Private Messages): open it in the browser instead. */
    class NotSignedIn : WhirlpoolError("This page needs a Whirlpool web login")

    /** A page or response did not have the expected structure; Whirlpool probably changed its layout. */
    class Drift(message: String, cause: Throwable? = null) : WhirlpoolError(message, cause)

    /** Any other API `ERROR` text or unexpected HTTP status. */
    class Api(message: String, val httpCode: Int? = null) : WhirlpoolError(message)
}
