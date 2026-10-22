package au.howesthat.whirl.core.ui

import android.content.Context
import android.os.Build
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
 * A colour palette (Settings › Colours), read from `assets/palettes/<id>.json`. Colours fill Material's colour scheme by
 * role name, plus Whirl's own roles ([EXTRA_ROLES]); roles left out keep Material's baseline. "Wallpaper" is not a
 * file: it is Material You's scheme from the wallpaper (Android 12+). See `docs/styles.md`.
 */
@Serializable
data class Palette(
    val id: String,
    val name: String,
    /** Who designed it, shown after the name in Settings. */
    val credit: String? = null,
    val light: Map<String, String> = emptyMap(),
    val dark: Map<String, String> = emptyMap(),
) {
    val isWallpaper: Boolean get() = id == WALLPAPER

    /** [light] or [dark] applied to Material's baseline scheme. */
    fun colorScheme(dark: Boolean): ColorScheme =
        (if (dark) this.dark else light).entries.fold(if (dark) darkColorScheme() else lightColorScheme()) { scheme, (role, hex) ->
            if (role in EXTRA_ROLES) scheme else scheme.withRole(role, parseColour(hex))
        }

    /** One of Whirl's own roles ([EXTRA_ROLES]), or null when the palette leaves it to the default. */
    fun extra(role: String, dark: Boolean): Color? = (if (dark) this.dark else light)[role]?.let(::parseColour)

    companion object {
        const val WALLPAPER = "wallpaper"
        const val WHIRL = "whirl"

        /** Colour roles that are Whirl's, not Material's. */
        val EXTRA_ROLES = setOf("unread", "quoteRepliedUser", "quoteOther", "topBar", "onTopBar")

        val Wallpaper = Palette(WALLPAPER, "Wallpaper")

        /** Whirl blue as in `whirl.json`, for when the assets cannot be read. */
        val Fallback = Palette(WHIRL, "Whirl blue", light = mapOf("primary" to "#3A5A8C"), dark = mapOf("primary" to "#A9C2EE"))
    }
}

/** A display layout (Settings › Layout), read from `assets/layouts/<id>.json`: how screens draw, not their colours. */
@Serializable
data class LayoutStyle(
    val id: String,
    val name: String,
    val credit: String? = null,
    val layout: StyleLayout = StyleLayout(),
) {
    companion object {
        const val CLASSIC = "classic"
        val Fallback = LayoutStyle(CLASSIC, "Classic")
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

/** The layout and palette in use, with colours resolved for light or dark. */
@Immutable
data class ResolvedStyle(
    val layout: StyleLayout,
    /** Background of unread rows ([Unread.ACCENT]); null leaves them untinted. */
    val unreadContainer: Color?,
    val quoteRepliedUser: Color,
    val quoteOther: Color,
    /** Top app bars; null keeps Material's (the surface colour). */
    val topBar: Color?,
    val onTopBar: Color?,
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
    ResolvedStyle(StyleLayout(), null, QuoteColors.repliedUserLight, QuoteColors.otherLight, null, null)
}

/** The bundled palettes and layouts, read once per process. */
object Styles {
    private val json = Json { ignoreUnknownKeys = false }
    @Volatile private var palettes: List<Palette>? = null
    @Volatile private var layouts: List<LayoutStyle>? = null

    fun parsePalette(text: String): Palette = json.decodeFromString(Palette.serializer(), text)
    fun parseLayout(text: String): LayoutStyle = json.decodeFromString(LayoutStyle.serializer(), text)

    /** Wallpaper first where Android has it (12+), then Whirl blue, then the rest by name. */
    fun palettes(context: Context): List<Palette> = palettes ?: run {
        val files = load(context, "palettes", ::parsePalette)
        val whirl = files.firstOrNull { it.id == Palette.WHIRL } ?: Palette.Fallback
        val wallpaper = listOf(Palette.Wallpaper).filter { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }
        (wallpaper + whirl + files.filter { it.id != Palette.WHIRL }.sortedBy { it.name }).also { palettes = it }
    }

    /** Classic first, then the rest by name. */
    fun layouts(context: Context): List<LayoutStyle> = layouts ?: run {
        val files = load(context, "layouts", ::parseLayout)
        val classic = files.firstOrNull { it.id == LayoutStyle.CLASSIC } ?: LayoutStyle.Fallback
        (listOf(classic) + files.filter { it.id != LayoutStyle.CLASSIC }.sortedBy { it.name }).also { layouts = it }
    }

    /** The palette with [id]; an unknown id (or Wallpaper before Android 12) gives Whirl blue. */
    fun palette(context: Context, id: String): Palette =
        palettes(context).let { all -> all.firstOrNull { it.id == id } ?: all.first { it.id == Palette.WHIRL } }

    fun layout(context: Context, id: String): LayoutStyle = layouts(context).let { all -> all.firstOrNull { it.id == id } ?: all.first() }

    private fun <T> load(context: Context, folder: String, parse: (String) -> T): List<T> {
        val assets = context.assets
        return assets.list(folder).orEmpty().filter { it.endsWith(".json") }.mapNotNull { file ->
            // A broken file must not stop the app: it is left out (the unit tests parse every bundled file).
            runCatching { parse(assets.open("$folder/$file").bufferedReader().use { it.readText() }) }.getOrNull()
        }
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
