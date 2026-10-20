package au.howesthat.whirl

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.ui.WhirlTheme
import au.howesthat.whirl.core.notifications.WatchedNotifier
import au.howesthat.whirl.feature.setup.SetupScreen
import au.howesthat.whirl.navigation.WatchedRoute
import au.howesthat.whirl.navigation.WatchSnackbars
import au.howesthat.whirl.navigation.WhirlApp
import au.howesthat.whirl.navigation.routeForLink
import au.howesthat.whirl.navigation.route
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalTime

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
            WhirlTheme(darkTheme = isDark(loaded.settings)) {
                val account by viewModel.account.collectAsStateWithLifecycle()
                val alert by viewModel.alert.collectAsStateWithLifecycle()
                if (!loaded.setupDone) {
                    // Finishing setup or signing out flips setupDone, which swaps between these two trees.
                    SetupScreen(onFinished = {})
                } else {
                    // Fixed while this tree lives: a changed homepage setting must not re-route an open NavHost.
                    val startRoute = remember { loaded.settings.homepage.route() }
                    val link by viewModel.link.collectAsStateWithLifecycle()
                    WhirlApp(startRoute = startRoute, account = account, alert = alert, link = link, onLinkHandled = viewModel::linkHandled) { host ->
                        WatchSnackbars(host, viewModel.watchEvents, viewModel::undo, viewModel::commit)
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
    // Evaluated when the activity composes; a live switch at the window edge comes with the Phase 3 schedule work.
    ThemeMode.DARK_AT_NIGHT -> isNight(LocalTime.now(), settings.nightStart, settings.nightEnd)
}

/** True if [now] falls in the window [start, end), which may wrap past midnight. */
internal fun isNight(now: LocalTime, start: LocalTime, end: LocalTime): Boolean =
    if (start <= end) now >= start && now < end else now >= start || now < end
