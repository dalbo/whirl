package au.howesthat.whirl.core.data

import au.howesthat.whirl.core.model.ThreadId
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.InputStream

/**
 * Settings › Backup's export and import file (docs/plans/settings-backup.md, Part 2): every setting under its stored
 * name, favourite forums and muted threads. **Never the API key or the account**: a shared file must be safe to post.
 *
 * ```json
 * {"app":"whirl","format":1,"exportedFrom":"1.10.0","settings":{"theme":"DARK",…},"favouriteForums":[92],"mutedThreads":["9244"]}
 * ```
 */
object SettingsFile {
    /** Bumped only when an older app would misread a newer file; new settings need no bump (unknown keys are skipped). */
    const val FORMAT = 1

    /** A real file is a few KB; anything this big is not one. */
    const val MAX_BYTES = 1 shl 20

    private const val APP = "whirl"
    private val json = Json { prettyPrint = true }

    /** All settings are written, defaults included, so the file says exactly what an import applies. */
    fun write(settings: Settings, favouriteForums: Set<Int>, mutedThreads: Set<ThreadId>, appVersion: String): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("app", APP)
                put("format", FORMAT)
                put("exportedFrom", appVersion)
                putJsonObject("settings") { SETTING_FIELDS.forEach { put(it.key.name, it.json(settings)) } }
                putJsonArray("favouriteForums") { favouriteForums.sorted().forEach { add(JsonPrimitive(it)) } }
                putJsonArray("mutedThreads") { mutedThreads.map { it.value }.sorted().forEach { add(JsonPrimitive(it)) } }
            },
        )

    /** [input] as UTF-8, or null if it is over [MAX_BYTES] (read no further than that). Blocking: call off Main. */
    fun readText(input: InputStream): String? {
        // Not readNBytes: that is API 33.
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
        return out.toByteArray().decodeToString()
    }

    fun read(text: String): Read {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (_: SerializationException) {
            return Read.Rejected(Problem.NOT_JSON)
        } catch (_: IllegalArgumentException) {
            return Read.Rejected(Problem.NOT_JSON)
        }
        if (root !is JsonObject || root.string("app") != APP) return Read.Rejected(Problem.NOT_WHIRL)
        val format = (root["format"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: return Read.Rejected(Problem.NOT_WHIRL)
        if (format > FORMAT) return Read.Rejected(Problem.NEWER_FORMAT)

        val stored = root["settings"] as? JsonObject ?: JsonObject(emptyMap())
        val known = SETTING_FIELDS.associateBy { it.key.name }
        val values = mutableListOf<Pair<SettingField<*, *>, JsonElement>>()
        val skipped = mutableListOf<String>()
        stored.forEach { (name, element) ->
            // A setting from a newer version is ignored; one this version cannot apply is reported.
            val field = known[name] ?: return@forEach
            if (field.applyJson(element, DEFAULTS_FOR_CHECK) != null) values += field to element else skipped += name
        }
        val favourites = root.list("favouriteForums") { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.intOrNull?.takeIf { id -> id > 0 } }
        val muted = root.list("mutedThreads") { e ->
            (e as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.length in 1..32 && it.all { c -> c.isAsciiLetterOrDigit() } }?.let(::ThreadId)
        }
        if (favourites.skipped) skipped += "favouriteForums"
        if (muted.skipped) skipped += "mutedThreads"
        return Read.Imported(Import(values, skipped, favourites.items, muted.items))
    }

    sealed interface Read {
        data class Rejected(val problem: Problem) : Read
        data class Imported(val import: Import) : Read
    }

    enum class Problem {
        /** Empty, or not JSON at all. */
        NOT_JSON,
        /** JSON, but not a Whirl settings file. */
        NOT_WHIRL,
        /** From a newer Whirl, in a format this version cannot read. */
        NEWER_FORMAT,
        /** Over [MAX_BYTES]; the caller checks while reading. */
        TOO_LARGE,
    }

    private val DEFAULTS_FOR_CHECK = Settings()

    private fun JsonObject.string(name: String) = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private class Items<T>(val items: Set<T>, val skipped: Boolean)

    private fun <T> JsonObject.list(name: String, item: (JsonElement) -> T?): Items<T> {
        val array = this[name] as? JsonArray ?: return Items(emptySet(), skipped = name in this)
        val items = array.mapNotNull(item)
        return Items(items.toSet(), skipped = items.size < array.size)
    }

    private fun Char.isAsciiLetterOrDigit() = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

/**
 * What a settings file would change, read but not yet applied. Settings it holds replace the current ones; a setting
 * it leaves out keeps its value. Favourites and mutes are added to the current ones, never removed.
 */
class Import internal constructor(
    private val values: List<Pair<SettingField<*, *>, JsonElement>>,
    /** Stored names of settings (and "favouriteForums", "mutedThreads") whose values this version could not apply. */
    val skipped: List<String>,
    val favouriteForums: Set<Int>,
    val mutedThreads: Set<ThreadId>,
) {
    fun applyTo(current: Settings): Settings = values.fold(current) { s, (field, element) -> field.applyJson(element, s) ?: s }

    /** The settings that would change, in [SETTING_FIELDS] order. */
    fun changes(current: Settings): List<SettingChange> {
        val after = applyTo(current)
        return SETTING_FIELDS.mapNotNull { field ->
            val from = field.valueIn(current)
            val to = field.valueIn(after)
            if (from != to) SettingChange(field.key.name, from, to) else null
        }
    }
}

/** One setting an import changes: its stored name, and its typed values ([Settings]' field types). */
data class SettingChange(val key: String, val from: Any, val to: Any)

/** The stored name of every setting, in order: for labels shown with an import (a test checks each has one). */
val SETTING_KEYS: List<String> get() = SETTING_FIELDS.map { it.key.name }
