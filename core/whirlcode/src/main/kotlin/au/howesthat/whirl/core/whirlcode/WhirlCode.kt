package au.howesthat.whirl.core.whirlcode

import au.howesthat.whirl.core.model.PostBody

/**
 * WhirlCode, Whirlpool's post markup (`fixtures/html/wiki_whirlcode.html`), turned into the HTML the site renders for
 * it, so the post writer's preview goes through the same parser and renderer as every post
 * (`docs/plans/post-writer.md` §2).
 *
 * What the site does is known from the wiki, from Dallin's every-code test post (`fixtures/whirlcode/`) and from the
 * links in all fixtures. Malformed text (an unclosed `[*`, five structural codes on a line) is a guess until the
 * edge-case post is captured (plan §1.4); the guesses are marked in the code and in `WhirlCodeTest`. Whatever the
 * input, the output holds no HTML but the tags and attributes listed here.
 */
object WhirlCode {
    fun toHtml(source: String): String = BlockWriter(source.replace("\r\n", "\n").replace('\r', '\n')).write()

    /** A parsed post as WhirlCode: the reverse of [toHtml] through `PostBodyParser`, as near as WhirlCode can write it. */
    fun fromBody(body: PostBody): String = FromBody.write(body)
}

/** What a structural code opens: `:` an indent, `>` a reply quote, `*` and `#` a list level. */
private enum class Container(val open: String, val close: String) {
    INDENT("<blockquote>", "</blockquote>"),
    QUOTE("<blockquote class=\"wcrep1\">", "</blockquote>"),
    BULLETS("<ul>", "</ul>"),
    NUMBERS("<ol>", "</ol>");

    val isList get() = this == BULLETS || this == NUMBERS

    companion object {
        fun of(code: Char) = when (code) {
            ':' -> INDENT
            '>' -> QUOTE
            '*' -> BULLETS
            else -> NUMBERS
        }
    }
}

/**
 * The line pass. Structural codes open containers; a line's codes are compared with the open ones level by level, so
 * the levels they share stay open (`> a` then `>> b` nests b in a's quote). A blank line ends a paragraph but closes
 * nothing: on the site, `> a`, a blank line and `> * b` stay in one quote, and two runs of table rows split by a blank
 * line are one table.
 */
private class BlockWriter(source: String) {
    private val lines = source.split('\n')
    private val out = mutableListOf<String>()
    private val open = mutableListOf<Container>()
    /** Lines of the paragraph being written (already converted), joined by `<br>`; null when none is open. */
    private var paragraph: MutableList<String>? = null
    private var table: MutableList<List<String>>? = null

    fun write(): String {
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            i++
            when {
                line.isBlank() -> endParagraph()
                line.trim() == "$$" -> {
                    // Fenced code: to the next `$$` line, or the end.
                    val end = (i until lines.size).firstOrNull { lines[it].trim() == "$$" } ?: lines.size
                    topLevel(Pre(lines.subList(i, end).joinToString("\n")))
                    i = (end + 1).coerceAtMost(lines.size)
                }
                line.startsWith("$ ") -> topLevel(Pre(line.substring(2)))
                RULE.matches(line.trim()) -> topLevel("<hr>")
                line.startsWith("|") -> {
                    closeContainers()
                    (table ?: mutableListOf<List<String>>().also { table = it }) += cells(line)
                }
                else -> {
                    endTable()
                    JUMP.matchEntire(line)?.let { m ->
                        val id = m.groupValues[1]
                        topLevel("<p class=\"reference\"><a onclick=\"jumpToReplyId($id);return false;\" title=\"$id\">${Inline.escapeText(m.groupValues[2])}</a></p>")
                    } ?: structured(line)
                }
            }
        }
        closeContainers()
        endTable()
        return out.joinToString("\n")
    }

    /** A block that sits outside every container: code, rules, jump links. */
    private fun topLevel(html: String) {
        closeContainers()
        endTable()
        out += html
    }

    @Suppress("FunctionName")
    private fun Pre(text: String) = "<pre>${Inline.escapeText(text)}</pre>"

    private fun structured(line: String) {
        val (codes, text) = structure(line) ?: (emptyList<Char>() to line)
        val wanted = codes.map(Container::of)
        if (wanted != open) {
            endParagraph()
            val keep = open.indices.firstOrNull { it >= wanted.size || open[it] != wanted[it] } ?: open.size
            while (open.size > keep) out += open.removeAt(open.lastIndex).close
            for (c in wanted.drop(keep)) {
                out += c.open
                open += c
            }
        }
        if (text.isBlank()) {
            endParagraph()
            return
        }
        val html = Inline.toHtml(text)
        if (open.lastOrNull()?.isList == true) {
            out += "<li>$html</li>"
        } else {
            (paragraph ?: mutableListOf<String>().also { paragraph = it }) += html
        }
    }

    private fun endParagraph() {
        paragraph?.let { out += "<p>${it.joinToString("<br>")}</p>" }
        paragraph = null
    }

    private fun closeContainers() {
        endParagraph()
        while (open.isNotEmpty()) out += open.removeAt(open.lastIndex).close
    }

    /**
     * A `| :--- | :---: | ---:` row under the first row makes that row a header and sets each column's alignment
     * (left is the default and gets no style, as on the site). Without one, every row is a plain row.
     */
    private fun endTable() {
        val rows = table ?: return
        table = null
        val separator = rows.getOrNull(1)?.takeIf { row -> row.isNotEmpty() && row.all { SEPARATOR.matches(it) } }
        val aligns = separator?.map { cell ->
            when {
                cell.startsWith(':') && cell.endsWith(':') -> "center"
                cell.endsWith(':') -> "right"
                else -> null
            }
        }.orEmpty()
        val body = if (separator != null) listOf(rows[0]) + rows.drop(2) else rows
        val html = StringBuilder("<table>")
        body.forEachIndexed { r, row ->
            val tag = if (separator != null && r == 0) "th" else "td"
            html.append("<tr>")
            row.forEachIndexed { c, cell ->
                val style = aligns.getOrNull(c)?.let { " style=\"text-align:$it\"" }.orEmpty()
                html.append("<$tag$style>").append(Inline.toHtml(cell)).append("</$tag>")
            }
            html.append("</tr>")
        }
        out += html.append("</table>").toString()
    }

    private companion object {
        val RULE = Regex("-{5,}")
        val JUMP = Regex("""@(\d+) (.+)""")
        val SEPARATOR = Regex(""":?-{3,}:?""")
        const val CODES = ":>*#"

        /** Cells split on `|`; the leading pipe, and an empty cell after a trailing one, are not cells. */
        fun cells(line: String): List<String> {
            val parts = line.substring(1).split('|').map { it.trim() }
            return if (parts.size > 1 && parts.last().isEmpty()) parts.dropLast(1) else parts
        }

        /**
         * The structural codes at the start of [line] and the text after them, or null when there are none. Spaces
         * between codes are optional; one is required before the text, so `>foo` and `> *bold*`'s `*` are text.
         * Limits (wiki): one `:`, three `>`, three list markers, four codes in all. Over them, the line is plain text
         * (a guess, plan §1.4).
         */
        fun structure(line: String): Pair<List<Char>, String>? {
            var end = 0
            while (end < line.length && (line[end] in CODES || line[end] == ' ')) end++
            // Back off codes that run into the text with no space ("> *bold*": the `*` is the text's).
            while (end > 0 && end < line.length && line[end - 1] != ' ') end--
            while (end > 0 && line[end - 1] in CODES && end == line.length) end-- // codes alone on a line
            val prefix = line.substring(0, end)
            if (prefix.isEmpty() || prefix[0] !in CODES) return null
            val codes = prefix.filter { it in CODES }.toList()
            val lists = codes.count { it == '*' || it == '#' }
            if (codes.count { it == ':' } > 1 || codes.count { it == '>' } > 3 || lists > 3 || codes.size > 4) return null
            return codes to line.substring(end)
        }
    }
}
