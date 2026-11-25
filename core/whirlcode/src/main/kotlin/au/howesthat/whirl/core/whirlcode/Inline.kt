package au.howesthat.whirl.core.whirlcode

/**
 * The inline pass over one paragraph (its lines joined by `\n`; the caller turns those into `<br>`), done the way the
 * site does it. Dallin's edge-case post (`fixtures/html/thread_9244p8v1_edgecases.html`, reply 76935755, source in
 * `fixtures/whirlcode/edge_cases.txt`) shows the site matching codes, not nesting them:
 * - each code is its opener, at least one character, then the nearest closer, across the paragraph's lines. So an
 *   unclosed `[*` takes everything to the next `*]` (`[**]` reaches past its own `*]`), a second `[*` inside is text,
 *   and crossed codes (`[*a [/b*] c/]`) give crossed tags, which the browser (and jsoup) sort out;
 * - the allowed HTML tags work only as open and close pairs; anything else, a tag on its own or with attributes, and a
 *   link to anything but a web address, shows as typed;
 * - `[.` and `.]` lose their dots after the codes are applied: `[.*a*.]` shows `[*a*]`, `[.["q"].]` a bracketed quote;
 * - a spaced `-` or `--` becomes a dash (not at the start of a line); addresses and email addresses link themselves
 *   (a bare `www.` address does not); every `@` is written `&#064;`.
 */
internal object Inline {

    /** A code as the site matches it: [open], at least one character (lines included), the nearest [close]. */
    private class Code(open: String, close: String, val render: (String) -> String) {
        val regex = Regex(Regex.escape(open) + "(.+?)" + Regex.escape(close), RegexOption.DOT_MATCHES_ALL)
    }

    private val CODES = listOf(
        Code("[*", "*]") { "<strong>$it</strong>" },
        Code("[/", "/]") { "<em>$it</em>" },
        Code("[(", ")]") { "<small>$it</small>" },
        Code("[-", "-]") { "<strike>$it</strike>" },
        Code("[#", "#]") { "<tt>$it</tt>" },
        Code("[~", "~]") { "<span class=\"wcserif\">$it</span>" },
        Code("[\\", "\\]") { "<sub>$it</sub>" },
        Code("[^", "^]") { "<sup>$it</sup>" },
        Code("[\"", "\"]") { "<span class=\"wcrep1\">$it</span>" },
        Code("['", "']") { "<span class=\"wcrep2\">$it</span>" },
        Code("[`", "`]") { "<span class=\"wcgrey\">$it</span>" },
        Code("[_", "_]") { "<span class=\"wcspoil\">$it</span>" },
    )

    /** The allowed tags (wiki: "HTML alternative") as the site writes them: `<b>` as `<strong>`, `<i>` as `<em>`. */
    private val TAGS = mapOf(
        "b" to "strong", "strong" to "strong", "i" to "em", "em" to "em", "small" to "small",
        "strike" to "strike", "tt" to "tt", "sub" to "sub", "sup" to "sup",
    )
    private val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val TAG_PAIR = Regex("""&lt;(${TAGS.keys.joinToString("|")})&gt;(.+?)&lt;/\1&gt;""", OPTIONS)
    /** `<a href="…">…</a>`: only `href`, in either quotes. */
    private val LINK = Regex("""&lt;a\s+href\s*=\s*(?:"([^"]*)"|'([^']*)')\s*&gt;(.+?)&lt;/a\s*&gt;""", OPTIONS)
    private val GOOGLE = Regex("""\[\?(.+?)\?\]""", RegexOption.DOT_MATCHES_ALL)
    private val WIKI = Regex("""\[\[([^\]|\n]+)(?:\|([^\]\n]+))?\]\]""")
    private val TAG = Regex("<[^>]*>")
    private val DASH = Regex("""(?<=[ \t])--?(?=[ \t])""")
    private val ENTITY = Regex("""&(?:#\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});""")

    fun toHtml(paragraph: String): String {
        var s = escapeHtml(paragraph)
        s = WIKI.replace(s) { m ->
            val term = m.groupValues[1]
            "<a class=\"wiki\" href=\"/wiki/${term.replace("\"", "&quot;")}\">${m.groupValues[2].ifEmpty { term }}</a>"
        }
        s = GOOGLE.replace(s) { m ->
            val terms = m.groupValues[1]
            "<a class=\"external\" rel=\"nofollow\" target=\"_blank\" href=\"http://www.google.com.au/search?q=${terms.replace("\"", "&quot;")}\">$terms</a>"
        }
        // The pairs again until none is left, for a tag inside another (<b><i>x</i></b>).
        while (true) {
            val next = TAG_PAIR.replace(s) { m -> TAGS.getValue(m.groupValues[1].lowercase()).let { "<$it>${m.groupValues[2]}</$it>" } }
            if (next == s) break
            s = next
        }
        s = LINK.replace(s) { m ->
            val href = m.groupValues[1].ifEmpty { m.groupValues[2] }.trim()
            Links.anchor(href, m.groupValues[3]) ?: m.value
        }
        for (code in CODES) s = code.regex.replace(s) { code.render(it.groupValues[1]) }
        s = textOutsideTags(s)
        s = s.replace("[.", "[").replace(".]", "]")
        return s.replace("@", "&#064;")
    }

    /** Links typed as text (not inside a link already) and spaced dashes, in the text between tags. */
    private fun textOutsideTags(html: String): String {
        val out = StringBuilder()
        var inLink = 0
        var last = 0
        for (tag in TAG.findAll(html)) {
            out.append(plain(html.substring(last, tag.range.first), linking = inLink == 0))
            out.append(tag.value)
            when {
                tag.value.startsWith("<a ", ignoreCase = true) -> inLink++
                tag.value.equals("</a>", ignoreCase = true) -> inLink = (inLink - 1).coerceAtLeast(0)
            }
            last = tag.range.last + 1
        }
        out.append(plain(html.substring(last), linking = inLink == 0))
        return out.toString()
    }

    /** Dashes first: a spaced dash is never part of an address, which has no spaces. */
    private fun plain(text: String, linking: Boolean): String {
        val dashed = DASH.replace(text) { if (it.value.length == 2) "\u2014" else "\u2013" }
        if (!linking) return dashed
        val out = StringBuilder()
        var last = 0
        for (link in Links.find(dashed)) {
            out.append(dashed, last, link.range.first).append(Links.autolink(link.value))
            last = link.range.last + 1
        }
        return out.append(dashed, last, dashed.length).toString()
    }

    /** `<`, `>` and `&` escaped, but an `&` that starts an entity stays one ("written as entities, never WhirlCode"). */
    fun escapeHtml(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when (c) {
                '&' -> {
                    val entity = ENTITY.matchAt(text, i)
                    if (entity != null) {
                        // A typed &lt; or &gt; in numeric form, so the tag patterns (which look for &lt;) only ever
                        // see a < or > that was typed as one: an entity never becomes code or HTML.
                        append(
                            when (entity.value.lowercase()) {
                                "&lt;" -> "&#60;"
                                "&gt;" -> "&#62;"
                                else -> entity.value
                            },
                        )
                        i += entity.value.length
                        continue
                    }
                    append("&amp;")
                }
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                else -> append(c)
            }
            i++
        }
    }

    /** Text shown as is, with no codes (code blocks, a jump link's label): escaped, `@` as `&#064;`. */
    fun escapeText(text: String): String = escapeHtml(text).replace("@", "&#064;")
}
