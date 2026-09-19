package dev.gavenda.yuuka.ui.common

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
import androidx.compose.ui.graphics.vector.ImageVector

/** One entry of a rail FAB's menu, for a screen whose leading action is really a few. */
class FabAction(
    val label: String,
    val icon: @Composable () -> Unit,
    val onClick: () -> Unit,
)

/**
 * A screen's leading action as the navigation rail draws it. [actions], when there are any, turn the
 * button into a menu: pressing it lists them instead of running [onClick]. [busy] swaps the icon for a
 * progress indicator, and a button that is not [enabled] is drawn muted and does nothing.
 */
class FabEntry(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val busy: Boolean = false,
    val actions: List<FabAction> = emptyList(),
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
 * on a phone it is the floating button it always was — [phoneFab], an extended FAB unless the screen
 * brings its own (Accounts, whose button opens a menu).
 */
@Composable
fun ScreenFab(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    actions: List<FabAction> = emptyList(),
    phoneFab: @Composable () -> Unit = {
        ExtendedFloatingActionButton(
            onClick = onClick,
            icon = { Icon(icon, contentDescription = null) },
            text = { Text(label) },
        )
    },
) {
    if (LocalRailFabHost.current == null) {
        phoneFab()
    } else {
        RegisterRailFab(FabEntry(label = label, icon = icon, onClick = onClick, actions = actions))
    }
}

/** Puts [entry] in the rail for as long as this is composed. Does nothing where there is no rail. */
@Composable
fun RegisterRailFab(entry: FabEntry) {
    val host = LocalRailFabHost.current ?: return
    val owner = remember { Any() }
    // Every composition, so the rail's copy never holds a click handler from an earlier one.
    SideEffect { host.show(owner, entry) }
    DisposableEffect(owner) { onDispose { host.hide(owner) } }
}
