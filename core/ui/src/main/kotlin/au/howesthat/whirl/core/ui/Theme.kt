package au.howesthat.whirl.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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

@Composable
fun WhirlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /** Multiplies Android's font size for all of the app's text (Settings → Text size). */
    textScale: Float = 1f,
    /** Settings → App style; Classic is Whirlpool's blue on Material's baseline. */
    style: AppStyle = AppStyle.Fallback,
    content: @Composable () -> Unit,
) {
    val wallpaper = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val colors = when {
        wallpaper -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> remember(style, darkTheme) { style.colorScheme(darkTheme) }
    }
    val resolved = remember(style, darkTheme, colors, wallpaper) {
        ResolvedStyle(
            layout = style.layout,
            // Under wallpaper colours, a style's tint becomes a light wash of the wallpaper's primary colour.
            unreadContainer = style.extra("unread", darkTheme)?.let { own ->
                if (wallpaper) colors.primary.copy(alpha = 0.08f).compositeOver(colors.surface) else own
            },
            quoteRepliedUser = style.extra("quoteRepliedUser", darkTheme)
                ?: if (darkTheme) QuoteColors.repliedUserDark else QuoteColors.repliedUserLight,
            quoteOther = style.extra("quoteOther", darkTheme) ?: if (darkTheme) QuoteColors.otherDark else QuoteColors.otherLight,
        )
    }
    val typography = if (style.layout.boldTitles) BoldTitles else MaterialTheme.typography
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
