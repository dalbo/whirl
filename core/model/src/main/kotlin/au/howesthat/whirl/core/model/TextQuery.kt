package au.howesthat.whirl.core.model

import java.text.Normalizer
import java.util.Locale

/**
 * Find in list: what the user typed, matched against the text a row shows. Every word must appear somewhere in the
 * row's fields, in any order and any field, ignoring case and accents ("cafe" finds "Café"); words are substrings, so
 * "nbn" finds "#NBN". A word with no letters or digits is ignored, and a blank query matches everything.
 */
class TextQuery(text: String?) {
    private val words = text.orEmpty().split(WHITESPACE).map(::fold).filter { word -> word.any(Char::isLetterOrDigit) }

    val isBlank: Boolean get() = words.isEmpty()

    fun matches(vararg fields: String?): Boolean {
        if (words.isEmpty()) return true
        val haystack = fields.mapNotNull { it?.let(::fold) }
        return words.all { word -> haystack.any { word in it } }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val COMBINING = Regex("\\p{Mn}+")

        fun fold(text: String): String = COMBINING.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
    }
}
