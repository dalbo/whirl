package au.howesthat.whirl.core.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An app style (Settings → App style), read from `assets/styles/<id>.json`. Colours fill Material's colour scheme by
 * role name, plus Whirl's own roles ([EXTRA_ROLES]); roles left out keep Material's baseline. The layout switches pick
 * between the ways each screen can draw itself. Adding a style is adding a JSON file; see `docs/styles.md`.
 */
@Serializable
data class AppStyle(
    val id: String,
    val name: String,
    /** Who designed it, shown under the choice in Settings. */
    val credit: String? = null,
    /** The style has a full palette of its own, so choosing it turns off wallpaper colours (they can go back on). */
    val ownColours: Boolean = false,
    val light: Map<String, String> = emptyMap(),
    val dark: Map<String, String> = emptyMap(),
    val layout: StyleLayout = StyleLayout(),
) {
    /** [light] or [dark] applied to Material's baseline scheme. */
    fun colorScheme(dark: Boolean): ColorScheme =
        (if (dark) this.dark else light).entries.fold(if (dark) darkColorScheme() else lightColorScheme()) { scheme, (role, hex) ->
            if (role in EXTRA_ROLES) scheme else scheme.withRole(role, parseColour(hex))
        }

    /** One of Whirl's own roles ([EXTRA_ROLES]), or null when the style leaves it to the default. */
    fun extra(role: String, dark: Boolean): Color? = (if (dark) this.dark else light)[role]?.let(::parseColour)

    companion object {
        const val CLASSIC = "classic"

        /** Classic as in `classic.json`, for when the assets cannot be read. */
        val Fallback = AppStyle(CLASSIC, "Classic", light = mapOf("primary" to "#3A5A8C"), dark = mapOf("primary" to "#A9C2EE"))

        /** Colour roles that are Whirl's, not Material's. */
        val EXTRA_ROLES = setOf("unread", "quoteRepliedUser", "quoteOther")

        private val json = Json { ignoreUnknownKeys = false }

        fun parse(text: String): AppStyle = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class StyleLayout(
    /** Corner radius of cards, in dp. */
    val corner: Int = 0,
    /** Space between cards and the screen edge, in dp. */
    val gutter: Int = 0,
    /** Thread lists, forums and settings: one card per section, or full-width rows. */
    val lists: Surfaces = Surfaces.FLAT,
    /** Posts: one card each, or full width with dividers. */
    val posts: Surfaces = Surfaces.FLAT,
    /** Quoted paragraphs: coloured text, or coloured text in a recessed box with a rule. */
    val quotes: Quotes = Quotes.TEXT,
    /** Section headings: a tinted band, or plain text after a small accent marker. */
    val headings: Headings = Headings.BAND,
    /** Watched's Unread/All switch: Material tabs, or pills in a track (with the unread count). */
    val tabs: Tabs = Tabs.UNDERLINE,
    /** Unread rows: bold title and a count badge, or a tinted row, an accent bar and "N new". */
    val unread: Unread = Unread.BADGE,
    /** Semibold screen titles and headings. */
    val boldTitles: Boolean = false,
) {
    val cornerDp: Dp get() = corner.dp
    val gutterDp: Dp get() = gutter.dp
}

@Serializable enum class Surfaces { @SerialName("flat") FLAT, @SerialName("cards") CARDS }
@Serializable enum class Quotes { @SerialName("text") TEXT, @SerialName("boxed") BOXED }
@Serializable enum class Headings { @SerialName("band") BAND, @SerialName("marker") MARKER }
@Serializable enum class Tabs { @SerialName("underline") UNDERLINE, @SerialName("pills") PILLS }
@Serializable enum class Unread { @SerialName("badge") BADGE, @SerialName("accent") ACCENT }

/** The style in use with its colours resolved for light or dark (and wallpaper colours, when on). */
@Immutable
data class ResolvedStyle(
    val layout: StyleLayout,
    /** Background of unread rows; null leaves them untinted. */
    val unreadContainer: Color?,
    val quoteRepliedUser: Color,
    val quoteOther: Color,
)

/**
 * Whirldroid 2.14.12's quote colours (PLAN.md §1.5), the default for every style. They stay fixed under Material You
 * so quotes look like Whirlpool's; 2.14.12's single `#9F6E19` for other quotes was 4.2:1 on both light and dark
 * surfaces, so it has a darker and a lighter shade of the same hue that reach 4.5:1 (5.1 and 5.4).
 */
internal object QuoteColors {
    val repliedUserLight = Color(0xFF4455AA)
    val repliedUserDark = Color(0xFF7885C4)
    val otherLight = Color(0xFF8D6216)
    val otherDark = Color(0xFFD49221)
}

val LocalAppStyle = staticCompositionLocalOf {
    ResolvedStyle(StyleLayout(), null, QuoteColors.repliedUserLight, QuoteColors.otherLight)
}

/** The bundled styles, Classic first. Read once per process. */
object AppStyles {
    @Volatile private var cached: List<AppStyle>? = null

    fun all(context: Context): List<AppStyle> = cached ?: load(context).also { cached = it }

    fun find(context: Context, id: String): AppStyle = all(context).let { styles -> styles.firstOrNull { it.id == id } ?: styles.first() }

    private fun load(context: Context): List<AppStyle> {
        val assets = context.assets
        val styles = assets.list("styles").orEmpty().filter { it.endsWith(".json") }.mapNotNull { file ->
            // A broken file must not stop the app: it is left out (the unit tests parse every bundled file).
            runCatching { AppStyle.parse(assets.open("styles/$file").bufferedReader().use { it.readText() }) }.getOrNull()
        }
        val classic = styles.firstOrNull { it.id == AppStyle.CLASSIC } ?: AppStyle.Fallback
        return listOf(classic) + styles.filter { it.id != AppStyle.CLASSIC }.sortedBy { it.name }
    }
}

/** `#RRGGBB` or `#AARRGGBB`. */
internal fun parseColour(hex: String): Color {
    val digits = hex.removePrefix("#")
    require((digits.length == 6 || digits.length == 8) && digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) { "Not a colour: $hex" }
    val argb = digits.toLong(16) or (if (digits.length == 6) 0xFF000000 else 0)
    return Color(argb.toInt())
}

/** Material's colour roles by name, as written in the style files. */
internal fun ColorScheme.withRole(role: String, c: Color): ColorScheme = when (role) {
    "primary" -> copy(primary = c)
    "onPrimary" -> copy(onPrimary = c)
    "primaryContainer" -> copy(primaryContainer = c)
    "onPrimaryContainer" -> copy(onPrimaryContainer = c)
    "inversePrimary" -> copy(inversePrimary = c)
    "secondary" -> copy(secondary = c)
    "onSecondary" -> copy(onSecondary = c)
    "secondaryContainer" -> copy(secondaryContainer = c)
    "onSecondaryContainer" -> copy(onSecondaryContainer = c)
    "tertiary" -> copy(tertiary = c)
    "onTertiary" -> copy(onTertiary = c)
    "tertiaryContainer" -> copy(tertiaryContainer = c)
    "onTertiaryContainer" -> copy(onTertiaryContainer = c)
    "background" -> copy(background = c)
    "onBackground" -> copy(onBackground = c)
    "surface" -> copy(surface = c)
    "onSurface" -> copy(onSurface = c)
    "surfaceVariant" -> copy(surfaceVariant = c)
    "onSurfaceVariant" -> copy(onSurfaceVariant = c)
    "surfaceTint" -> copy(surfaceTint = c)
    "inverseSurface" -> copy(inverseSurface = c)
    "inverseOnSurface" -> copy(inverseOnSurface = c)
    "error" -> copy(error = c)
    "onError" -> copy(onError = c)
    "errorContainer" -> copy(errorContainer = c)
    "onErrorContainer" -> copy(onErrorContainer = c)
    "outline" -> copy(outline = c)
    "outlineVariant" -> copy(outlineVariant = c)
    "scrim" -> copy(scrim = c)
    "surfaceBright" -> copy(surfaceBright = c)
    "surfaceDim" -> copy(surfaceDim = c)
    "surfaceContainerLowest" -> copy(surfaceContainerLowest = c)
    "surfaceContainerLow" -> copy(surfaceContainerLow = c)
    "surfaceContainer" -> copy(surfaceContainer = c)
    "surfaceContainerHigh" -> copy(surfaceContainerHigh = c)
    "surfaceContainerHighest" -> copy(surfaceContainerHighest = c)
    else -> throw IllegalArgumentException("Unknown colour role: $role")
}
