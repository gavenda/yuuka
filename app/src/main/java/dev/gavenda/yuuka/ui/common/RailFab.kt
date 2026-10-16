package dev.gavenda.yuuka.ui.common

import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.annotation.DrawableRes

/** A screen's leading action as the navigation rail draws it. */
class FabEntry(
    val label: String,
    @DrawableRes val icon: Int,
    val onClick: () -> Unit,
)

/**
 * Where the current screen's leading action is put when the navigation rail carries it — mirrors
 * `src/lib/fab.ts`. Screens register through [ScreenFab] and never see the host; the rail reads [entry].
 *
 * Two screens can be composed at once while one slides in over the other, so a registration is owned:
 * the outgoing screen leaving must not take the incoming one's button with it.
 */
@Stable
class RailFabHost {
    var entry by mutableStateOf<FabEntry?>(null)
        private set

    private var owner: Any? = null

    fun show(owner: Any, entry: FabEntry) {
        this.owner = owner
        this.entry = entry
    }

    fun hide(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            entry = null
        }
    }
}

/** Provided by the app shell only while a rail is on screen; without one a screen draws its own FAB. */
val LocalRailFabHost = staticCompositionLocalOf<RailFabHost?> { null }

/**
 * A screen's leading action. With a navigation rail it is handed to the rail and nothing is drawn here;
 * on a phone it is the extended floating button it always was.
 */
@Composable
fun ScreenFab(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    val host = LocalRailFabHost.current
    if (host == null) {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(painterResource(icon), contentDescription = null) },
            text = { Text(label) },
        )
    } else {
        val entry = FabEntry(label = label, icon = icon, onClick = onClick)
        val owner = remember { Any() }
        // Every composition, so the rail's copy never holds a click handler from an earlier one.
        SideEffect { host.show(owner, entry) }
        DisposableEffect(owner) { onDispose { host.hide(owner) } }
    }
}
