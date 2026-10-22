package au.howesthat.whirl.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density

/** Semibold titles and headings ([StyleLayout.boldTitles]). */
private val BoldTitles = Typography().let { d ->
    d.copy(
        titleLarge = d.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = d.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = d.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** Whether [palette] can be shown here: Wallpaper needs Android 12. */
fun usesWallpaper(palette: Palette): Boolean = palette.isWallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun WhirlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Settings › Colours; Wallpaper is Material You (Android 12+, else Whirl blue). */
    palette: Palette = Palette.Fallback,
    /** Settings › Layout. */
    layout: StyleLayout = StyleLayout(),
    /** Multiplies Android's font size for all of the app's text (Settings → Text size). */
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val wallpaper = usesWallpaper(palette)
    val own = if (palette.isWallpaper) Palette.Fallback else palette
    val colors = when {
        wallpaper -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> remember(own, darkTheme) { own.colorScheme(darkTheme) }
    }
    val resolved = remember(own, layout, darkTheme, colors, wallpaper) {
        val p = own.takeUnless { wallpaper }
        ResolvedStyle(
            layout = layout,
            // A palette without an unread tint gets a light wash of its primary colour where the layout tints rows.
            unreadContainer = p?.extra("unread", darkTheme)
                ?: colors.primary.copy(alpha = 0.08f).compositeOver(colors.surface).takeIf { layout.unread == Unread.ACCENT },
            quoteRepliedUser = p?.extra("quoteRepliedUser", darkTheme)
                ?: if (darkTheme) QuoteColors.repliedUserDark else QuoteColors.repliedUserLight,
            quoteOther = p?.extra("quoteOther", darkTheme) ?: if (darkTheme) QuoteColors.otherDark else QuoteColors.otherLight,
            topBar = p?.extra("topBar", darkTheme),
            onTopBar = p?.extra("onTopBar", darkTheme),
        )
    }
    val typography = if (layout.boldTitles) BoldTitles else MaterialTheme.typography
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalAppStyle provides resolved) {
            if (textScale == 1f) {
                // Keep the system Density: on Android 14+ it carries the non-linear font scaling for large text.
                content()
            } else {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale), content = content)
            }
        }
    }
}

/** Top app bar colours: the palette's `topBar` and `onTopBar` when it has them, else Material's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun whirlTopBarColors(): TopAppBarColors {
    val style = LocalAppStyle.current
    val bar = style.topBar ?: return TopAppBarDefaults.topAppBarColors()
    val on = style.onTopBar ?: contentColorFor(bar)
    return TopAppBarDefaults.topAppBarColors(
        containerColor = bar,
        scrolledContainerColor = bar,
        navigationIconContentColor = on,
        titleContentColor = on,
        actionIconContentColor = on,
    )
}

/** Secondary text in a top bar ("Updated 2 min ago", "Page 1 of 13"). */
@Composable
fun topBarSubtleColor(): Color = LocalAppStyle.current.let { s ->
    s.topBar?.let { (s.onTopBar ?: contentColorFor(it)).copy(alpha = 0.8f) } ?: MaterialTheme.colorScheme.onSurfaceVariant
}
