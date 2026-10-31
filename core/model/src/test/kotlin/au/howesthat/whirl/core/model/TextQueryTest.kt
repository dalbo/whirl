package au.howesthat.whirl.core.model

import org.junit.jupiter.api.Test
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
}
