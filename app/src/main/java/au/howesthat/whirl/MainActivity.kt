package au.howesthat.whirl

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.ui.AppStyles
import au.howesthat.whirl.core.ui.LocalBrowserApp
import au.howesthat.whirl.core.ui.WhirlTheme
import au.howesthat.whirl.core.notifications.WatchedNotifier
import au.howesthat.whirl.feature.setup.SetupScreen
import au.howesthat.whirl.navigation.WatchedRoute
import au.howesthat.whirl.navigation.WatchSnackbars
import au.howesthat.whirl.navigation.WhirlApp
import au.howesthat.whirl.navigation.routeForLink
import au.howesthat.whirl.navigation.route
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.state.value == null }
        super.onCreate(savedInstanceState)
        // A recreated activity has already handled its launch intent.
        if (savedInstanceState == null) viewModel.openLink(routeFor(intent))
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val loaded = state ?: return@setContent
            val dark = isDark(loaded.settings)
            // The bars follow the app's theme, not the system's (Dark in the app with a light phone kept dark icons).
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            val style = remember(loaded.settings.style) { AppStyles.find(this, loaded.settings.style) }
            WhirlTheme(darkTheme = dark, dynamicColor = loaded.settings.dynamicColor, textScale = loaded.settings.textScale.factor, style = style) {
                val account by viewModel.account.collectAsStateWithLifecycle()
                val alert by viewModel.alert.collectAsStateWithLifecycle()
                if (!loaded.setupDone) {
                    // Finishing setup or signing out flips setupDone, which swaps between these two trees.
                    SetupScreen(onFinished = {})
                } else {
                    // Fixed while this tree lives: a changed homepage setting must not re-route an open NavHost.
                    val startRoute = remember { loaded.settings.homepage.route() }
                    val link by viewModel.link.collectAsStateWithLifecycle()
                    CompositionLocalProvider(LocalBrowserApp provides loaded.settings.browserApp) {
                        WhirlApp(
                            startRoute = startRoute,
                            account = account,
                            alert = alert,
                            link = link,
                            onLinkHandled = viewModel::linkHandled,
                            animations = loaded.settings.screenAnimations,
                        ) { host ->
                            WatchSnackbars(host, viewModel.watchEvents, viewModel::undo, viewModel::commit)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.openLink(routeFor(intent))
    }

    /** A Whirlpool link, or the watched-thread notification (which opens Watched). */
    private fun routeFor(intent: Intent): Any? = when {
        intent.getBooleanExtra(WatchedNotifier.EXTRA_OPEN_WATCHED, false) -> WatchedRoute
        else -> intent.data?.let { routeForLink(it) }
    }
}

@Composable
private fun isDark(settings: Settings): Boolean = when (settings.theme) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.DARK_AT_NIGHT -> rememberIsNight(settings.nightStart, settings.nightEnd)
}

/**
 * Whether it is night now, switching at the window's edges while the app is open. A timer covers the open app; the
 * check on resume covers a timer that ran late while the phone slept.
 */
@Composable
private fun rememberIsNight(start: LocalTime, end: LocalTime): Boolean {
    var night by remember(start, end) { mutableStateOf(isNight(LocalTime.now(), start, end)) }
    LifecycleResumeEffect(start, end) {
        night = isNight(LocalTime.now(), start, end)
        onPauseOrDispose {}
    }
    LaunchedEffect(start, end) {
        while (true) {
            delay(untilNextEdge(LocalTime.now(), start, end))
            night = isNight(LocalTime.now(), start, end)
        }
    }
    return night
}

/** Time from [now] until just after the next start or end of the window (a clock change only costs an extra wake). */
internal fun untilNextEdge(now: LocalTime, start: LocalTime, end: LocalTime): Duration {
    fun until(edge: LocalTime): Long = java.time.Duration.between(now, edge).toMillis().let { if (it <= 0) it + DAY_MILLIS else it }
    return (minOf(until(start), until(end)) + EDGE_SLACK_MILLIS).milliseconds
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
private const val EDGE_SLACK_MILLIS = 500L

/** True if [now] falls in the window [start, end), which may wrap past midnight. */
internal fun isNight(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (start <= end) now >= start && now < end else now >= start || now < end
