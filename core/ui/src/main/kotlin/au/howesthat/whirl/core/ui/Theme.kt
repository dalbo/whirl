package au.howesthat.whirl.core.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Whirlpool's blue, used when dynamic colour is unavailable (Android 8–11).
private val WhirlpoolBlue = Color(0xFF3A5A8C)

private val LightColors = lightColorScheme(primary = WhirlpoolBlue)
private val DarkColors = darkColorScheme(primary = Color(0xFFA9C2EE))

@Composable
fun WhirlTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    /** Multiplies Android's font size for all of the app's text (Settings → Text size). */
    textScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors) {
        if (textScale == 1f) {
            // Keep the system Density: on Android 14+ it carries the non-linear font scaling for large text.
            content()
        } else {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale), content = content)
        }
    }
}
