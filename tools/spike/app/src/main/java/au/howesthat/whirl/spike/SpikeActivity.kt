package au.howesthat.whirl.spike

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Day-0 Cloudflare spike (PLAN.md §2.1). Fetches the API and two forum pages with several
 * client/User-Agent combinations and reports status, cf-mitigated and whether the body is the
 * real page. Results go to the screen and to logcat tag WhirlSpike. The API key (intent extra
 * "key") is never displayed or logged.
 */
class SpikeActivity : Activity() {

    private lateinit var out: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { setTextIsSelectable(true); textSize = 11f; setPadding(24, 48, 24, 24) }
        setContentView(ScrollView(this).apply { addView(out) })
        val key = intent.getStringExtra("key").orEmpty()
        Thread { runAll(key) }.start()
    }

    private data class Target(val name: String, val url: String, val marker: String)

    private fun runAll(key: String) {
        val apiKey = key.ifEmpty { "nokey" }
        val targets = listOf(
            Target("api get=user", "https://whirlpool.net.au/api/?key=$apiKey&output=json&get=user", "\"AUTH\""),
            Target("forum 92", "https://forums.whirlpool.net.au/forum/92", "class=\"thread"),
            Target("thread p2", "https://forums.whirlpool.net.au/thread/3pxj858k?p=2", "data-replyid"),
        )
        val okDefaultUa = "okhttp/" + okhttp3.OkHttp.VERSION
        val clients = listOf(
            "okhttp" to "Whirldroid",
            "okhttp" to "Whirl/0.0.1 (Android; au.howesthat.whirl)",
            "okhttp" to okDefaultUa,
            "huc" to "Whirldroid",
        )
        emit("START key=${if (key.isEmpty()) "absent" else "present(${key.length} chars)"} okhttp=${okhttp3.OkHttp.VERSION} sdk=${android.os.Build.VERSION.SDK_INT}")
        for ((client, ua) in clients) {
            for (t in targets) {
                val r = try {
                    if (client == "okhttp") viaOkHttp(t.url, ua) else viaHuc(t.url, ua)
                } catch (e: Exception) {
                    Result(-1, null, null, null, 0, "", e.javaClass.simpleName + ": " + e.message)
                }
                val ok = r.code == 200 && r.body.contains(t.marker)
                emit(
                    "RESULT client=$client ua=\"$ua\" target=\"${t.name}\" code=${r.code} " +
                        "cf-mitigated=${r.cfMitigated} server=${r.server} proto=${r.protocol} bytes=${r.body.length} " +
                        "challenge=${r.body.contains("Just a moment")} marker=${r.body.contains(t.marker)} " +
                        "title=\"${titleOf(r.body)}\" ms=${r.ms} PASS=$ok" + (r.error?.let { " error=\"$it\"" } ?: ""),
                )
            }
        }
        emit("DONE")
    }

    private data class Result(
        val code: Int, val cfMitigated: String?, val server: String?, val protocol: String?,
        val ms: Long, val body: String, val error: String?,
    )

    private val ok = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun viaOkHttp(url: String, ua: String): Result {
        val start = System.nanoTime()
        ok.newCall(Request.Builder().url(url).header("User-Agent", ua).build()).execute().use { resp ->
            val body = resp.body.string()
            return Result(
                resp.code, resp.header("cf-mitigated"), resp.header("server"), resp.protocol.toString(),
                (System.nanoTime() - start) / 1_000_000, body, null,
            )
        }
    }

    private fun viaHuc(url: String, ua: String): Result {
        val start = System.nanoTime()
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", ua)
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        try {
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Result(
                code, conn.getHeaderField("cf-mitigated"), conn.getHeaderField("server"), "huc",
                (System.nanoTime() - start) / 1_000_000, body, null,
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun titleOf(body: String): String =
        Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL).find(body)?.groupValues?.get(1)?.trim()?.take(60)
            ?: body.take(60).replace(Regex("\\s+"), " ").replace("\"", "'")

    private fun emit(line: String) {
        Log.i("WhirlSpike", line)
        runOnUiThread { out.append(line + "\n\n") }
    }
}
