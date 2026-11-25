package au.howesthat.whirl.core.whirlcode

/**
 * Links as the site writes them, from every link in the fixtures (2026-11-19: 416 external, 45 forum, 18 whrl.pl) and
 * the edge-case post (2026-11-25):
 * - an external address links as typed, `class="external" rel="nofollow" target="_blank"`;
 * - a `forums.whirlpool.net.au` address is internal and scheme-relative, and shows only its path
 *   (`//forums.whirlpool.net.au/forum-replies.cfm?t=2691384` as `/forum-replies.cfm?t=2691384`);
 * - a `whrl.pl` address is internal and shows without its scheme (`whrl.pl/RgCnTb`);
 * - another `whirlpool.net.au` address is internal and shows as typed (one sample);
 * - an email address links with `mailto:`;
 * - a bare `www.` address is not linked;
 * - trailing punctuation (`.,;:!?'` and an unbalanced `)`) is the sentence's, not the link's.
 * Everything here takes and gives text already escaped for HTML.
 */
internal object Links {

    private val CANDIDATE = Regex(
        """(?<![\w@/.-])https?://[^\s<>"]+|(?<![\w.+-])[\w.+-]+@[\w-]+(?:\.[\w-]+)+""",
        RegexOption.IGNORE_CASE,
    )
    private const val TRAILING = ".,;:!?'"
    /** Escaped `<`, `>` and `"` end an address. */
    private val ESCAPED_END = Regex("&(?:lt|gt|quot);")

    /** Each address in [text] (escaped), its trailing punctuation left out. */
    fun find(text: String): List<Found> = CANDIDATE.findAll(text).mapNotNull { m ->
        var value = ESCAPED_END.find(m.value)?.let { m.value.substring(0, it.range.first) } ?: m.value
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
    fun autolink(value: String): String =
        if ('@' in value && "://" !in value) "<a class=\"email\" href=\"mailto:$value\">$value</a>"
        else anchor(value, null) ?: value

    /**
     * A link to [href] showing [labelHtml] (a `<a href>` typed by the user), or the address itself ([labelHtml] null, an
     * autolink). Null when [href] is not a web address: the site shows such a link as typed.
     */
    fun anchor(href: String, labelHtml: String?): String? {
        val m = WHIRLPOOL.matchEntire(href)
        if (m == null) {
            if (!WEB.containsMatchIn(href)) return null
            return "<a class=\"external\" rel=\"nofollow\" target=\"_blank\" href=\"${quoted(href)}\">${labelHtml ?: href}</a>"
        }
        val host = m.groupValues[1].lowercase()
        val rest = m.groupValues[2]
        val shown = labelHtml ?: when (host) {
            "forums.whirlpool.net.au" -> rest.ifEmpty { "/" }
            "whrl.pl" -> host + rest
            else -> href
        }
        return "<a class=\"internal\" href=\"${quoted("//$host$rest")}\">$shown</a>"
    }

    private val WHIRLPOOL = Regex("""(?i)(?:https?:)?//((?:forums\.|www\.)?whirlpool\.net\.au|whrl\.pl)(/.*)?""")
    private val WEB = Regex("""^https?://""", RegexOption.IGNORE_CASE)

    private fun quoted(value: String) = value.replace("\"", "&quot;")
}

/** A found address: where it is in the text and what it is. */
internal data class Found(val range: IntRange, val value: String)
