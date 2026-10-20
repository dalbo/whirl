package au.howesthat.whirl.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

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
