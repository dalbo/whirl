package au.howesthat.whirl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import au.howesthat.whirl.core.data.Settings
import au.howesthat.whirl.core.data.ThemeMode
import au.howesthat.whirl.core.ui.WhirlTheme
import au.howesthat.whirl.feature.settings.SettingsScreen
import au.howesthat.whirl.feature.setup.SetupScreen
import au.howesthat.whirl.home.HomeScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.serialization.Serializable
import java.time.LocalTime

@Serializable object SetupRoute
@Serializable object HomeRoute
@Serializable object SettingsRoute

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { viewModel.state.value == null }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val loaded = state ?: return@setContent
            WhirlTheme(darkTheme = isDark(loaded.settings)) {
                val nav = rememberNavController()
                NavHost(nav, startDestination = if (loaded.setupDone) HomeRoute else SetupRoute) {
                    composable<SetupRoute> {
                        SetupScreen(onFinished = { nav.navigate(HomeRoute) { popUpTo(SetupRoute) { inclusive = true } } })
                    }
                    composable<HomeRoute> {
                        HomeScreen(onOpenSettings = { nav.navigate(SettingsRoute) })
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            onBack = { nav.popBackStack() },
                            onSignedOut = { nav.navigate(SetupRoute) { popUpTo(0) { inclusive = true } } },
                        )
                    }
                }
            }
        }
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
