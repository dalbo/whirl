package au.howesthat.whirl.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Find in list and find on page: what the user typed, matched against the text shown. Every word must appear somewhere
 * in the fields, in any order and any field, ignoring case and accents ("cafe" finds "Café"); words are substrings, so
 * "nbn" finds "#NBN". A word with no letters or digits is ignored, and a blank query matches everything.
 */
class TextQuery(text: String?) {
    private val words = text.orEmpty().split(WHITESPACE).map { Folded(it).text }.filter { word -> word.any(Char::isLetterOrDigit) }

    val isBlank: Boolean get() = words.isEmpty()

    /** Equal when the words are: the same search typed again. */
    override fun equals(other: Any?): Boolean = other is TextQuery && other.words == words

    override fun hashCode(): Int = words.hashCode()

    fun matches(vararg fields: String?): Boolean {
        if (words.isEmpty()) return true
        val haystack = fields.mapNotNull { it?.let { f -> Folded(f).text } }
        return words.all { word -> haystack.any { word in it } }
    }

    /** Where each word occurs in [text], as ranges of [text] itself (folding can change lengths), merged and in order. */
    fun ranges(text: String): List<IntRange> {
        if (words.isEmpty()) return emptyList()
        val folded = Folded(text)
        val found = words.flatMap { word ->
            generateSequence(folded.text.indexOf(word).takeIf { it >= 0 }) { at ->
                folded.text.indexOf(word, at + 1).takeIf { it >= 0 }
            }.map { at -> folded.start[at]..folded.end[at + word.length - 1] }.toList()
        }.sortedBy { it.first }
        val merged = mutableListOf<IntRange>()
        for (r in found) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, r.last) else merged += r
        }
        return merged
    }

    /**
     * [original] folded one character at a time, so each folded character knows the original characters it came
     * from ([start] and [end], inclusive). Folding the whole string at once would lose that, since stripping accents
     * changes the length.
     */
    private class Folded(original: String) {
        val text: String
        val start: IntArray
        val end: IntArray

        init {
            val out = StringBuilder()
            val starts = ArrayList<Int>()
            val ends = ArrayList<Int>()
            var i = 0
            while (i < original.length) {
                val next = i + Character.charCount(original.codePointAt(i))
                val f = fold(original.substring(i, next))
                // A character that folds to nothing (a separate accent) belongs to the letter before it.
                if (f.isEmpty() && ends.isNotEmpty()) ends[ends.lastIndex] = next - 1
                repeat(f.length) {
                    starts += i
                    ends += next - 1
                }
                out.append(f)
                i = next
            }
            text = out.toString()
            start = starts.toIntArray()
            end = ends.toIntArray()
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val COMBINING = Regex("\\p{Mn}+")

        /** One character: no accents, lower case, and a final sigma as a plain one (it depends on its neighbours). */
        fun fold(text: String): String =
            COMBINING.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT).replace('ς', 'σ')
    }
}
