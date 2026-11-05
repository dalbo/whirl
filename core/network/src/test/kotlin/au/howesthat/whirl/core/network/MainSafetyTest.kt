package au.howesthat.whirl.core.network

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Source
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Called from a single-threaded "main" dispatcher, the API and the web layer read response bodies elsewhere. */
class MainSafetyTest {
    private val server = MockWebServer().apply { start() }
    private val mainExecutor = Executors.newSingleThreadExecutor { Thread(it, "fake-main") }
    private val main = mainExecutor.asCoroutineDispatcher()

    /** The threads that read response bodies. */
    private val readers = ConcurrentLinkedQueue<String>()

    private val client: OkHttpClient = WhirlpoolHttp.client("Whirl/test").newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder().body(recording(response.body)).build()
        }
        .build()

    private fun recording(body: ResponseBody): ResponseBody {
        val source: Source = object : ForwardingSource(body.source()) {
            override fun read(sink: Buffer, byteCount: Long): Long {
                readers += Thread.currentThread().name
                return super.read(sink, byteCount)
            }
        }
        return source.buffer().asResponseBody(body.contentType(), body.contentLength())
    }

    @AfterEach
    fun tearDown() {
        main.close()
        server.close()
    }

    private fun fixture(path: String) = File(System.getProperty("whirl.fixtures"), path).readText()

    @Test
    fun `web pages are read off the calling thread`() {
        server.enqueue(MockResponse.Builder().body(fixture("html/forum92_p1.html")).build())
        val web = WhirlpoolWeb(client, WhirlpoolUrls(server.url("/")), RetryPolicy.NONE)
        val html = runBlocking(main) { web.get(web.urls.forum(92)) }
        assertTrue(html.isNotEmpty())
        assertTrue(readers.isNotEmpty(), "the body was read")
        assertTrue(readers.none { it.startsWith("fake-main") }, "read on ${readers.toSet()}")
    }

    @Test
    fun `API responses are read off the calling thread`() {
        server.enqueue(MockResponse.Builder().addHeader("Content-Type", "application/json").body(fixture("api/api_user.json")).build())
        val api = WhirlpoolApi(client, { "k" }, server.url("/api/"), RetryPolicy.NONE)
        val user = runBlocking(main) { api.user() }
        assertEquals(216903L, user.id)
        assertTrue(readers.isNotEmpty(), "the body was read")
        assertTrue(readers.none { it.startsWith("fake-main") }, "read on ${readers.toSet()}")
    }
}
