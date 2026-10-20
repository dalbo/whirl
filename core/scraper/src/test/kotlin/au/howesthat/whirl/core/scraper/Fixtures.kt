package au.howesthat.whirl.core.scraper

import java.io.File

/** Raw server HTML captured by tools/capture. Never the post-JavaScript copies in `rendered/`. */
object Fixtures {
    val dir: File = File(System.getProperty("whirl.fixtures") ?: error("whirl.fixtures not set"), "html")

    fun html(name: String): String {
        require(!name.contains('/')) { "Use raw fixtures only (no rendered/): $name" }
        return File(dir, name).readText()
    }

    fun allRawHtml(): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".html") }!!.sortedBy { it.name }
}
