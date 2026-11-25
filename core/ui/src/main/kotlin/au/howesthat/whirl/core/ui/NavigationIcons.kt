package au.howesthat.whirl.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp

/**
 * The top bar's navigation icon. Screens that can be both a drawer destination and a pushed screen (thread lists)
 * take one of these from the caller.
 */
sealed interface NavIcon {
    data class Drawer(val open: () -> Unit) : NavIcon
    data class Back(val back: () -> Unit) : NavIcon
}

@Composable
fun NavIconButton(icon: NavIcon) = when (icon) {
    is NavIcon.Drawer -> IconButton(onClick = icon.open) { Icon(Icons.Default.Menu, stringResource(R.string.open_menu)) }
    is NavIcon.Back -> IconButton(onClick = icon.back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
}

/**
 * Opens the drawer when a swipe towards it is left over after a pager (or anything that scrolls sideways) has gone as
 * far as it can: on a pager's first page, where the drawer's own swipe never arrives because the pager takes the
 * gesture. Does nothing unless [icon] is the drawer's.
 */
@Composable
fun Modifier.openDrawerPastStart(icon: NavIcon): Modifier {
    val open = (icon as? NavIcon.Drawer)?.open ?: return this
    val threshold = with(LocalDensity.current) { 24.dp.toPx() }
    val towardsDrawer = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val connection = remember(open, threshold, towardsDrawer) {
        object : NestedScrollConnection {
            var dragged = 0f
            var opened = false

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || opened) return Offset.Zero
                dragged += available.x * towardsDrawer
                if (dragged > threshold) {
                    opened = true
                    open()
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                dragged = 0f
                opened = false
                return Velocity.Zero
            }
        }
    }
    return nestedScroll(connection)
}
