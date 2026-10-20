package au.howesthat.whirl.core.network

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.io.File
import kotlin.test.assertEquals

/** Every captured API response decodes into the envelope model. */
class ApiFixturesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @TestFactory
    fun `every api fixture decodes`(): List<DynamicTest> =
        File(System.getProperty("whirl.fixtures"), "api").listFiles { f -> f.name.endsWith(".json") }!!.sorted().map { file ->
            DynamicTest.dynamicTest(file.name) {
                val env = json.decodeFromString(ApiEnvelope.serializer(), file.readText())
                if (file.name == "api_badkey.json") {
                    assertEquals("Not authenticated. Bad API key?", env.error)
                    assertEquals(0L, env.auth?.id)
                } else {
                    assertEquals("", env.error)
                    assertEquals(216903L, env.auth?.id)
                }
                env.threads.orEmpty().forEach { WhirlpoolApi.parseApiDate(it.firstDate); WhirlpoolApi.parseApiDate(it.lastDate) }
                env.watched.orEmpty().forEach { WhirlpoolApi.parseApiDate(it.lastDate) }
                env.news.orEmpty().forEach { WhirlpoolApi.parseApiDate(it.date) }
            }
        }

    @org.junit.jupiter.api.Test
    fun `combined call carries every requested key`() {
        val env = json.decodeFromString(ApiEnvelope.serializer(), File(System.getProperty("whirl.fixtures"), "api/api_combined.json").readText())
        assertEquals(54, env.forums?.size)
        assertEquals(30, env.news?.size)
        assertEquals(0, env.recent?.size)
        assertEquals(4, env.watched?.size)
    }
}
