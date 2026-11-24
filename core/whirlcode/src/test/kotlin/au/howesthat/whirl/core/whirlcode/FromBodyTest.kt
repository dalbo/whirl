package au.howesthat.whirl.core.whirlcode

import au.howesthat.whirl.core.model.Post
import au.howesthat.whirl.core.scraper.ArchivePageParser
import au.howesthat.whirl.core.scraper.PostBodyParser
import au.howesthat.whirl.core.scraper.ThreadPageParser
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Post → WhirlCode → HTML → post, over every post in every thread and archive fixture. A consistency check of the two
 * converters against each other (`docs/plans/post-writer.md` §3), not against the site: `WhirlCodeTest` does that.
 */
class FromBodyTest {

    private val fixtures = File(System.getProperty("whirl.fixtures") ?: error("whirl.fixtures not set"), "html")

    private fun posts(): List<Pair<String, Post>> = fixtures.listFiles()!!.sortedBy { it.name }.flatMap { f ->
        when {
            f.name.startsWith("thread_") -> runCatching { ThreadPageParser.parse(f.readText()).posts }.getOrDefault(emptyList())
            f.name.startsWith("archive_") -> ArchivePageParser.parse(f.readText()).posts
            else -> emptyList()
        }.map { f.name to it }
    }

    @Test
    fun `every fixture post survives a round trip, but the known exceptions`() {
        val failures = mutableListOf<String>()
        var total = 0
        for ((file, post) in posts()) {
            total++
            val body = PostBodyParser.parse(post.bodyHtml)
            val code = WhirlCode.fromBody(body)
            val back = PostBodyParser.parse(WhirlCode.toHtml(code))
            if (back != body) {
                val i = body.blocks.indices.firstOrNull { body.blocks.getOrNull(it) != back.blocks.getOrNull(it) } ?: 0
                failures += "$file ${post.replyId}\n  code:  ${code.lines().joinToString("⏎")}\n  want:  ${body.blocks.getOrNull(i)}\n  got:   ${back.blocks.getOrNull(i)}"
            }
        }
        File("build/roundtrip-report.txt").writeText("$total posts, ${failures.size} differ\n\n" + failures.joinToString("\n\n"))
        val ids = failures.map { it.lineSequence().first() }
        assertEquals(emptyList(), ids - KNOWN, "see build/roundtrip-report.txt")
        assertEquals(emptySet(), KNOWN - ids.toSet(), "a known exception now survives: take it off the list")
        assertTrue(total > 2_000, "only $total posts read from the fixtures")
    }

    private companion object {
        /** Posts that cannot survive, each with why. Reviewed by hand; a new entry needs a reason. */
        val KNOWN = setOf(
            // 2009 markup: lines of only spaces and runs of spaces around `<br>`. In WhirlCode a blank line is a new
            // paragraph and a line's trailing spaces are not kept, so these read back with the whitespace changed.
            "thread_archive_numeric.html 15845146",
            "thread_archive_numeric.html 15851699",
            "thread_archive_numeric.html 15852095",
            "thread_archive_numeric.html 15852107",
        )
    }
}
