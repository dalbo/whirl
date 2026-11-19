package au.howesthat.whirl.core.whirlcode

/**
 * Links as the site writes them, from every link in the fixtures (2026-11-19: 416 external, 45 forum, 18 whrl.pl):
 * - an external address links as typed, `class="external" rel="nofollow" target="_blank"`;
 * - a `forums.whirlpool.net.au` address is internal and scheme-relative, and shows only its path
 *   (`//forums.whirlpool.net.au/forum-replies.cfm?t=2691384` as `/forum-replies.cfm?t=2691384`);
 * - a `whrl.pl` address is internal and shows without its scheme (`whrl.pl/RgCnTb`);
 * - another `whirlpool.net.au` address is internal and shows as typed (one sample);
 * - a bare `www.` address links with `http://` and shows as typed;
 * - an email address links with `mailto:`.
 * Trailing punctuation (`.,;:!?'` and an unbalanced `)`) is the sentence's, not the link's (a guess, plan §1.4).
 */
internal object Links {

    private val CANDIDATE = Regex(
        """(?<![\w@/.-])(?:https?://[^\s<>"]+|www\.[^\s<>"]+)|(?<![\w.+-])[\w.+-]+@[\w-]+(?:\.[\w-]+)+""",
        RegexOption.IGNORE_CASE,
    )
    private const val TRAILING = ".,;:!?'"

    /** Each address in [text], its trailing punctuation left out. */
    fun find(text: String): List<Found> = CANDIDATE.findAll(text).mapNotNull { m ->
        var value = m.value
        while (value.isNotEmpty()) {
            val last = value.last()
            value = when {
                last in TRAILING -> value.dropLast(1)
                last == ')' && value.count { it == '(' } < value.count { it == ')' } -> value.dropLast(1)
                else -> break
            }
        }
        if (value.length < 4) null else Found(m.range.first until m.range.first + value.length, value)
    }.toList()

    /** An address typed in the text. */
    fun autolink(value: String): String = when {
        '@' in value && !value.contains("://") -> {
            val shown = Inline.escapeText(value)
            "<a class=\"email\" href=\"mailto:$shown\">$shown</a>"
        }
        value.startsWith("www.", ignoreCase = true) -> anchor("http://$value", Inline.escapeText(value))
        else -> anchor(value, null)
    }

    /** `<a href>` typed by the user ([labelHtml] already converted), or an autolink ([labelHtml] null). */
    fun anchor(href: String, labelHtml: String?): String {
        val m = WHIRLPOOL.matchEntire(href.trim())
        if (m == null) return external(href, labelHtml ?: Inline.escapeText(href))
        val host = m.groupValues[1].lowercase()
        val rest = m.groupValues[2]
        val shown = labelHtml ?: Inline.escapeText(
            when (host) {
                "forums.whirlpool.net.au" -> rest.ifEmpty { "/" }
                "whrl.pl" -> host + rest
                else -> href
            },
        )
        return "<a class=\"internal\" href=\"${attr("//$host$rest")}\">$shown</a>"
    }

    private fun external(href: String, labelHtml: String): String {
        // Only web and mail addresses: a `javascript:` or other scheme is left as text.
        if (!SAFE.containsMatchIn(href.trim())) return labelHtml
        return "<a class=\"external\" rel=\"nofollow\" target=\"_blank\" href=\"${attr(href.trim())}\">$labelHtml</a>"
    }

    private val WHIRLPOOL = Regex("""(?i)(?:https?:)?//((?:forums\.|www\.)?whirlpool\.net\.au|whrl\.pl)(/.*)?""")
    private val SAFE = Regex("""^(?:https?://|mailto:|/|#)""", RegexOption.IGNORE_CASE)

    private fun attr(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
}

/** A found address: where it is in the text and what it is. */
internal data class Found(val range: IntRange, val value: String)
