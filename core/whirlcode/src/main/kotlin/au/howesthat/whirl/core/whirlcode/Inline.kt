package au.howesthat.whirl.core.whirlcode

/**
 * The inline pass over one line: bracket codes (`[*bold*]`, nesting), escapes (`[.*x*.]`), wiki and Google links, the
 * allowed HTML tags, entities, spaced dashes and autolinks. A code with no closer on its line stays as typed.
 */
internal object Inline {

    /** Opening marker → closing marker; the closer is followed by `]`. */
    private val CLOSERS = mapOf(
        '*' to '*', '/' to '/', '(' to ')', '-' to '-', '#' to '#', '~' to '~', '\\' to '\\', '^' to '^',
        '"' to '"', '\'' to '\'', '`' to '`', '_' to '_', '?' to '?',
    )

    private fun wrap(marker: Char, inner: String): String = when (marker) {
        '*' -> "<strong>$inner</strong>"
        '/' -> "<em>$inner</em>"
        '(' -> "<small>$inner</small>"
        '-' -> "<strike>$inner</strike>"
        '#' -> "<tt>$inner</tt>"
        '~' -> "<span class=\"wcserif\">$inner</span>"
        '\\' -> "<sub>$inner</sub>"
        '^' -> "<sup>$inner</sup>"
        '"' -> "<span class=\"wcrep1\">$inner</span>"
        '\'' -> "<span class=\"wcrep2\">$inner</span>"
        '`' -> "<span class=\"wcgrey\">$inner</span>"
        else -> "<span class=\"wcspoil\">$inner</span>"
    }

    /** Allowed tags (wiki: "HTML alternative"), as the site writes them: `<b>` as `<strong>`, `<i>` as `<em>`. */
    private val TAGS = mapOf(
        "b" to "strong", "strong" to "strong", "i" to "em", "em" to "em", "small" to "small",
        "strike" to "strike", "tt" to "tt", "sub" to "sub", "sup" to "sup",
    )
    private val TAG = Regex("""<(/?)([a-zA-Z]+)\s*>""")
    private val LINK_OPEN = Regex("""<a\s+href\s*=\s*(?:"([^"<>]*)"|'([^'<>]*)')\s*>""", RegexOption.IGNORE_CASE)
    private val LINK_CLOSE = Regex("""</a\s*>""", RegexOption.IGNORE_CASE)

    fun toHtml(line: String): String = Parser(line).run()

    /** Text with `<`, `>` and `&` escaped, except an `&` that starts an entity ("written as entities, never WhirlCode"). */
    fun escapeText(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '&' -> {
                    val entity = ENTITY.matchAt(text, i)
                    if (entity != null) {
                        append(entity.value)
                        i += entity.value.length
                        continue
                    }
                    append("&amp;")
                }
                c == '<' -> append("&lt;")
                c == '>' -> append("&gt;")
                c == '@' -> append("&#064;") // as the site writes every @
                else -> append(c)
            }
            i++
        }
    }

    private val ENTITY = Regex("""&(?:#\d{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z][a-zA-Z0-9]{1,31});""")

    private fun attr(value: String) = value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private const val MAX_DEPTH = 32

    private class Parser(val s: String) {
        /** Positions where a code's closer was looked for and not found, so a run of unclosed codes stays linear. */
        private val failed = HashSet<Long>()

        /** Where each closer last occurs: an opener after it can never close, so it is text at once. */
        private val lastCloser = HashMap<String, Int>()
        private fun canClose(close: String, from: Int) = lastCloser.getOrPut(close) { s.lastIndexOf(close) } >= from

        fun run(): String = parse(0, null)!!.first

        /**
         * Converts [s] from [from] until [closer] (a code's `*]`), returning the HTML and the index after the closer;
         * null when [closer] never comes. With no closer, runs to the end.
         */
        fun parse(from: Int, closer: String?, depth: Int = 0): Pair<String, Int>? {
            val key = from.toLong() shl 8 or (closer?.get(0)?.code?.toLong() ?: 0)
            if (closer != null && key in failed) return null
            val html = StringBuilder()
            val text = StringBuilder()
            var textStart = from
            val tags = ArrayDeque<String>()
            fun flush(at: Int) {
                if (text.isNotEmpty()) html.append(plain(text.toString(), s.getOrNull(textStart - 1), s.getOrNull(at)))
                text.clear()
            }
            fun emit(at: Int, piece: String) {
                flush(at)
                html.append(piece)
            }
            var i = from
            while (i < s.length) {
                if (text.isEmpty()) textStart = i
                if (closer != null && s.startsWith(closer, i)) {
                    flush(i)
                    while (tags.isNotEmpty()) html.append("</${tags.removeLast()}>")
                    return html.toString() to i + closer.length
                }
                val c = s[i]
                val next = s.getOrNull(i + 1)
                // [.*escaped*.] → [*escaped*], taken as text.
                if (c == '[' && next == '.' && s.getOrNull(i + 2) in CLOSERS) {
                    val m = s[i + 2]
                    val end = s.indexOf("${CLOSERS[m]}.]", i + 3)
                    if (end >= 0) {
                        text.append('[').append(m).append(s, i + 3, end).append(CLOSERS[m]).append(']')
                        i = end + 3
                        continue
                    }
                }
                // [[term]] and [[term|label]]: a wiki link.
                if (c == '[' && next == '[') {
                    val end = s.indexOf("]]", i + 2)
                    if (end > i + 2) {
                        val inner = s.substring(i + 2, end)
                        val term = inner.substringBefore('|')
                        val label = if ('|' in inner) inner.substringAfter('|') else term
                        emit(i, "<a class=\"wiki\" href=\"/wiki/${attr(term)}\">${escapeText(label)}</a>")
                        i = end + 2
                        continue
                    }
                }
                // Past MAX_DEPTH nested codes, an opener is text: deep enough for any post, and no stack overflow.
                if (c == '[' && next != null && next in CLOSERS && depth < MAX_DEPTH) {
                    val close = "${CLOSERS[next]}]"
                    if (next == '?') {
                        // [?terms?]: a Google search, the terms as typed (the site leaves the spaces in the URL).
                        val end = s.indexOf(close, i + 2)
                        if (end > i + 2) {
                            val terms = s.substring(i + 2, end)
                            emit(i, "<a class=\"external\" rel=\"nofollow\" target=\"_blank\" href=\"http://www.google.com.au/search?q=${attr(terms)}\">${escapeText(terms)}</a>")
                            i = end + 2
                            continue
                        }
                    } else {
                        val inner = if (canClose(close, i + 2)) parse(i + 2, close, depth + 1) else null
                        if (inner != null) {
                            emit(i, wrap(next, inner.first))
                            i = inner.second
                            continue
                        }
                    }
                }
                if (c == '<') {
                    val link = LINK_OPEN.matchAt(s, i)
                    if (link != null) {
                        val closeAt = LINK_CLOSE.find(s, link.range.last + 1)
                        if (closeAt != null) {
                            val href = link.groupValues[1].ifEmpty { link.groupValues[2] }
                            // The link's text is converted on its own, so codes in it cannot run past `</a>`.
                            val label = toHtml(s.substring(link.range.last + 1, closeAt.range.first))
                            emit(i, Links.anchor(href, label))
                            i = closeAt.range.last + 1
                            continue
                        }
                    }
                    val tag = TAG.matchAt(s, i)
                    val name = tag?.groupValues?.get(2)?.lowercase()?.let(TAGS::get)
                    if (tag != null && name != null) {
                        if (tag.groupValues[1].isEmpty()) {
                            emit(i, "<$name>")
                            tags.addLast(name)
                        } else if (name in tags) {
                            // Close what was opened since, then this one: the output stays well formed.
                            flush(i)
                            while (true) {
                                val last = tags.removeLast()
                                html.append("</$last>")
                                if (last == name) break
                            }
                        } // A closing tag that was never opened is dropped.
                        i = tag.range.last + 1
                        continue
                    }
                }
                text.append(c)
                i++
            }
            if (closer != null) {
                failed += key
                return null
            }
            flush(i)
            while (tags.isNotEmpty()) html.append("</${tags.removeLast()}>")
            return html.toString() to i
        }
    }

    /**
     * Plain text: autolinks, then spaced dashes and escaping. [before] and [after] are the source characters around
     * it, so a dash at its edge knows whether it is spaced.
     */
    private fun plain(text: String, before: Char?, after: Char?): String {
        val out = StringBuilder()
        var last = 0
        for (link in Links.find(text)) {
            out.append(dashes(text.substring(last, link.range.first), if (last == 0) before else text[last - 1], text[link.range.first]))
            out.append(Links.autolink(link.value))
            last = link.range.last + 1
        }
        out.append(dashes(text.substring(last), if (last == 0) before else text[last - 1], after))
        return out.toString()
    }

    /** ` - ` becomes an en dash and ` -- ` an em dash; only when spaced on both sides (a line's start is not a space). */
    private fun dashes(text: String, before: Char?, after: Char?): String {
        if ('-' !in text) return escapeText(text)
        val padded = (before ?: '\u0000') + text + (after ?: '\u0000')
        val replaced = DASH.replace(padded) { if (it.value.length == 2) "—" else "–" }
        return escapeText(replaced.substring(1, replaced.length - 1))
    }

    private val DASH = Regex("""(?<=\s)--?(?=\s)""")
}
