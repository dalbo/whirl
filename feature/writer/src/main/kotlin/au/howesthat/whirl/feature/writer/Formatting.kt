package au.howesthat.whirl.feature.writer

import androidx.compose.ui.text.TextRange

/** Text and its selection: what a formatting button reads and what it gives back. */
data class Edit(val text: String, val selection: TextRange)

/**
 * The formatting bar's operations, on plain text (`docs/plans/post-writer.md` §5). Pure, so they are tested without a
 * screen; the writer applies the result to its `TextFieldState`.
 */
object Formatting {

    /**
     * Wraps the selection in [open] and [close] (`[*` and `*]`), keeping it selected; with nothing selected, inserts the
     * pair with the cursor between.
     */
    fun wrap(e: Edit, open: String, close: String): Edit {
        val (start, end) = e.selection.min to e.selection.max
        val text = e.text.substring(0, start) + open + e.text.substring(start, end) + close + e.text.substring(end)
        return Edit(text, TextRange(start + open.length, end + open.length))
    }

    /**
     * Puts [code] (`>` or `:`) before every line the selection touches, or takes it off when all of them have it
     * already. A code goes in front of any codes the line has (`>* text`, which WhirlCode reads as `> * text`).
     */
    fun toggleLinePrefix(e: Edit, code: Char): Edit = mapLines(e) { lines ->
        val parsed = lines.map(::split)
        val all = parsed.filter { it.text.isNotBlank() }.let { it.isNotEmpty() && it.all { line -> line.codes.startsWith(code) } }
        parsed.map { line ->
            when {
                line.text.isBlank() && line.codes.isEmpty() -> line.original
                all -> join(line.codes.removePrefix("$code"), line.text)
                else -> join("$code${line.codes}", line.text)
            }
        }
    }

    /**
     * A list on the selected lines: `*` bullets or `#` numbers ([marker]). A line in a list of the other kind switches
     * to this kind, keeping its depth; when every line is already this kind, the list comes off.
     */
    fun toggleList(e: Edit, marker: Char): Edit = mapLines(e) { lines ->
        val parsed = lines.map(::split)
        val all = parsed.filter { it.text.isNotBlank() }.let { it.isNotEmpty() && it.all { line -> line.list.lastOrNull() == marker } }
        parsed.map { line ->
            when {
                line.text.isBlank() && line.codes.isEmpty() -> line.original
                all -> join(line.outer, line.text)
                line.list.isNotEmpty() -> join(line.outer + marker.toString().repeat(line.list.length), line.text)
                else -> join(line.outer + marker, line.text)
            }
        }
    }

    /**
     * One list level deeper ([deeper]) or shallower on the selected lines that are list items: WhirlCode nests lists
     * three deep, with at most four codes on a line. Other lines are left as they are.
     */
    fun nestList(e: Edit, deeper: Boolean): Edit = mapLines(e) { lines ->
        lines.map(::split).map { line ->
            val last = line.list.lastOrNull()
            when {
                last == null -> line.original
                deeper && line.list.length < MAX_LIST_DEPTH && line.codes.length < MAX_CODES -> join(line.codes + last, line.text)
                !deeper && line.list.length > 1 -> join(line.codes.dropLast(1), line.text)
                else -> line.original
            }
        }
    }

    /** A line split into its structure codes (no spaces) and its text; [list] is the run of list codes at the end. */
    private class Line(val original: String, val codes: String, val text: String) {
        val list: String get() = codes.takeLastWhile { it == '*' || it == '#' }
        val outer: String get() = codes.dropLast(list.length)
    }

    /** As the converter reads a line: codes (spaces between them optional), then a space, then the text. */
    private fun split(line: String): Line {
        var end = 0
        while (end < line.length && (line[end] in STRUCTURE || line[end] == ' ')) end++
        // Codes that run into the text with no space are text ("**bold**").
        while (end > 0 && end < line.length && line[end - 1] != ' ') end--
        val prefix = line.substring(0, end)
        if (prefix.isEmpty() || prefix[0] !in STRUCTURE) return Line(line, "", line)
        return Line(line, prefix.filter { it in STRUCTURE }, line.substring(end))
    }

    private fun join(codes: String, text: String) = if (codes.isEmpty()) text else "$codes $text"

    /**
     * Applies [change] to the lines the selection touches. A cursor moves with its line's text; a selection covers the
     * changed lines.
     */
    private fun mapLines(e: Edit, change: (List<String>) -> List<String>): Edit {
        val start = if (e.selection.min == 0) 0 else e.text.lastIndexOf('\n', e.selection.min - 1) + 1
        val endLine = e.text.indexOf('\n', e.selection.max).let { if (it < 0) e.text.length else it }
        val lines = e.text.substring(start, endLine).split('\n')
        val changed = change(lines)
        val replaced = changed.joinToString("\n")
        val text = e.text.substring(0, start) + replaced + e.text.substring(endLine)
        val selection = if (e.selection.collapsed) {
            TextRange((e.selection.start + changed.first().length - lines.first().length).coerceIn(start, start + replaced.length))
        } else {
            TextRange(start, start + replaced.length)
        }
        return Edit(text, selection)
    }

    /** A link: `<a href="url">text</a>`, or the bare address when there is no text (the site links it). */
    fun link(e: Edit, url: String, label: String): Edit {
        val address = url.trim()
        val inserted = if (label.isBlank()) address else "<a href=\"${address.replace("\"", "%22")}\">$label</a>"
        val text = e.text.substring(0, e.selection.min) + inserted + e.text.substring(e.selection.max)
        return Edit(text, TextRange(e.selection.min + inserted.length))
    }

    /** A block on lines of its own (a rule, a table, a code fence), with blank lines around it as needed. */
    fun block(e: Edit, block: String, cursorAt: Int = block.length): Edit {
        val before = e.text.substring(0, e.selection.min)
        val after = e.text.substring(e.selection.max)
        val lead = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val trail = when {
            after.isEmpty() || after.startsWith("\n\n") -> ""
            after.startsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val text = before + lead + block + trail + after
        return Edit(text, TextRange(before.length + lead.length + cursorAt))
    }

    private const val STRUCTURE = ":>*#"
    private const val MAX_LIST_DEPTH = 3
    private const val MAX_CODES = 4
}
