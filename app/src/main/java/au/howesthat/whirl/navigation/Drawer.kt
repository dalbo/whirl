package au.howesthat.whirl.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import au.howesthat.whirl.R
import au.howesthat.whirl.core.data.Account
import kotlin.reflect.KClass

/** A drawer entry: [route] is the destination object, [label] a string resource. */
data class DrawerEntry(val route: Any, val label: Int, val icon: ImageVector)

/** 2.14.12's drawer order, Whims dropped, Latest added after Recent. */
val TOP_LEVEL = listOf(
    DrawerEntry(NewsRoute, R.string.nav_news, Icons.Default.DateRange),
    DrawerEntry(RecentRoute, R.string.nav_recent, Icons.Default.Person),
    DrawerEntry(LatestRoute, R.string.nav_latest, Icons.Default.Notifications),
    DrawerEntry(WatchedRoute, R.string.nav_watched, Icons.Default.Star),
    DrawerEntry(PopularRoute, R.string.nav_popular, Icons.Default.ThumbUp),
    DrawerEntry(ForumsRoute, R.string.nav_forums, Icons.AutoMirrored.Filled.List),
)

private val SECONDARY = listOf(
    DrawerEntry(SettingsRoute, R.string.settings, Icons.Default.Settings),
    DrawerEntry(AboutRoute, R.string.nav_about, Icons.Default.Info),
)

@Composable
fun DrawerContent(account: Account?, alert: String, isSelected: (KClass<*>) -> Boolean, onSelect: (DrawerEntry) -> Unit) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp)) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                account?.let {
                    Text(
                        stringResource(R.string.logged_in_as, it.userName, it.userId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Whirlpool's site-wide ALERT, shown verbatim.
                if (alert.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(alert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            TOP_LEVEL.forEach { Item(it, isSelected(it.route::class), onSelect) }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            SECONDARY.forEach { Item(it, isSelected(it.route::class), onSelect) }
        }
    }
}

@Composable
private fun Item(entry: DrawerEntry, selected: Boolean, onSelect: (DrawerEntry) -> Unit) {
    NavigationDrawerItem(
        label = { Text(stringResource(entry.label)) },
        icon = { Icon(entry.icon, contentDescription = null) },
        selected = selected,
        onClick = { onSelect(entry) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
