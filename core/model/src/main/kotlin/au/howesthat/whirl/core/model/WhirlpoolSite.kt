package au.howesthat.whirl.core.model

/**
 * Whirlpool's two hosts, written once: `WhirlpoolUrls` builds every request from them, and the parsers resolve the
 * pages' relative links against them.
 */
object WhirlpoolSite {
    /** The forums: threads, forum pages, users, search. */
    const val FORUMS = "https://forums.whirlpool.net.au"

    /** The main site: news, the wiki, the profile page with the API key. */
    const val MAIN = "https://whirlpool.net.au"
}
