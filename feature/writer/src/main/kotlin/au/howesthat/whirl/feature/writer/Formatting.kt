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
     * Puts [code] (`>`, `*`, `#`) before every line the selection touches, or takes it off when all of them have it
     * already. A line that already starts with a code gets the new one in front with no space (`>* text`, which
     * WhirlCode reads as `> * text`); a plain line gets the code and a space.
     */
    fun toggleLinePrefix(e: Edit, code: Char): Edit {
        val start = e.text.lastIndexOf('\n', (e.selection.min - 1).coerceAtLeast(0)).let { if (e.selection.min == 0 || it < 0) 0 else it + 1 }
        val endLine = e.text.indexOf('\n', e.selection.max).let { if (it < 0) e.text.length else it }
        val lines = e.text.substring(start, endLine).split('\n')
        val all = lines.all { line -> line.startsWith(code) }
        val changed = lines.map { line ->
            when {
                all -> line.removePrefix("$code").removePrefix(" ")
                line.isEmpty() -> line
                line.first() in STRUCTURE -> "$code$line"
                else -> "$code $line"
            }
        }
        val replaced = changed.joinToString("\n")
        val text = e.text.substring(0, start) + replaced + e.text.substring(endLine)
        val delta = replaced.length - (endLine - start)
        // A cursor moves with its line's text; a selection covers the changed lines.
        val selection = if (e.selection.collapsed) {
            TextRange((e.selection.start + changed.first().length - lines.first().length).coerceIn(start, start + replaced.length))
        } else {
            TextRange(start, endLine + delta)
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
}
