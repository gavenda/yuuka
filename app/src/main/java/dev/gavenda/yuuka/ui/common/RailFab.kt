package dev.gavenda.yuuka.ui.common

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.annotation.DrawableRes

/** A screen's leading action as the navigation rail draws it. */
class FabEntry(
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

/** Provided by the app shell only while a rail is on screen; without one a screen draws its own FAB. */
val LocalRailFab = staticCompositionLocalOf<RailSlot<FabEntry>?> { null }

/**
 * A screen's leading action. With a navigation rail it is handed to the rail and nothing is drawn here;
 * on a phone it is the extended floating button it always was.
 */
@Composable
fun ScreenFab(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    val slot = LocalRailFab.current
    if (slot == null) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(painterResource(icon), contentDescription = null) },
            text = { Text(label) },
        )
    } else {
        slot.Register(FabEntry(label = label, icon = icon, onClick = onClick))
    }
}
