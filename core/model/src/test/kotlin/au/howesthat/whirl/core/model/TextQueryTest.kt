package au.howesthat.whirl.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextQueryTest {
    @Test
    fun `case and accents are ignored`() {
        assertTrue(TextQuery("nbn").matches("NBN outage in Perth"))
        assertTrue(TextQuery("cafe").matches("Café wifi"))
        assertTrue(TextQuery("CAFÉ").matches("cafe wifi"))
    }

    @Test
    fun `words are substrings and may sit in different fields`() {
        assertTrue(TextQuery("nbn").matches("#nbn's new plans"))
        assertTrue(TextQuery("perth outage").matches("Outage today", "Perth"))
        assertTrue(TextQuery("  outage   perth ").matches("Perth outage"), "extra spaces")
        assertFalse(TextQuery("perth sydney").matches("Perth outage", "Broadband"), "every word must match")
    }

    @Test
    fun `missing fields are skipped`() {
        assertTrue(TextQuery("modem").matches(null, "Modems/Routers"))
        assertFalse(TextQuery("modem").matches(null, null))
    }

    @Test
    fun `blank queries and punctuation-only words match everything`() {
        assertTrue(TextQuery(null).isBlank)
        assertTrue(TextQuery("   ").matches("anything"))
        assertTrue(TextQuery("- ?").isBlank)
        assertTrue(TextQuery("telstra &").matches("Telstra and Optus"), "a lone & is ignored")
        assertTrue(TextQuery("c++").matches("C++ compilers"), "punctuation inside a word is kept")
        assertFalse(TextQuery("c++").matches("C compilers"))
    }

    @Test
    fun `ranges point into the original text, accents and all`() {
        assertEquals(listOf(0..3), TextQuery("cafe").ranges("Café au lait"))
        assertEquals(listOf(6..9, 17..20), TextQuery("CAFÉ").ranges("Nice, cafe or a 'café'"))
        // Decomposed: "e" then a combining accent, two characters for one letter.
        assertEquals(listOf(0..4), TextQuery("cafe").ranges("cafe\u0301 time"))
        assertEquals(listOf(2..6), TextQuery("naive").ranges("a naïve plan"))
    }

    @Test
    fun `ranges of several words merge where they touch or overlap`() {
        assertEquals(listOf(0..6, 11..14), TextQuery("plan nbn").ranges("nbnplan is plan"))
        assertEquals(listOf(0..3), TextQuery("nbn bnp").ranges("nbnpq"))
    }

    @Test
    fun `no ranges for a blank query or no match`() {
        assertEquals(emptyList(), TextQuery("").ranges("anything"))
        assertEquals(emptyList(), TextQuery("modem").ranges("router"))
    }

    @Test
    fun `matching and ranges agree on Greek final sigma`() {
        // A final sigma is "ς", but "Σ" lowercases to "σ" on its own: both count as one letter.
        val q = TextQuery("ΟΔΟΣ")
        assertTrue(q.matches("οδος"))
        assertEquals(listOf(0..3), q.ranges("οδος"))
        assertTrue(TextQuery("οδος").matches("ΟΔΟΣ"))
    }
}
