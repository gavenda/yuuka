package dev.gavenda.yuuka.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The month the screen on show is looking at, as the navigation rail draws it: [month] (`YYYY-MM`) is what
 * the open rail labels the button with, and [onPick] opens the screen's own month picker.
 */
@Stable
class MonthEntry internal constructor(month: String, val onPick: () -> Unit) {
    var month by mutableStateOf(month)
        internal set
}

/**
 * Where a month-scoped screen puts its month when the navigation rail carries the button that changes it —
 * mirrors `src/lib/monthAction.ts`. Screens register through [MonthTopBar] and never see the host; the rail
 * reads [entry].
 *
 * Two screens can be composed at once while one slides in over the other, so an entry is only taken back
 * if it is still the one on show: the outgoing screen leaving must not take the incoming one's button with it.
 */
@Stable
class RailMonthHost {
    var entry by mutableStateOf<MonthEntry?>(null)
        private set

    fun show(entry: MonthEntry) {
        this.entry = entry
    }

    fun hide(entry: MonthEntry) {
        if (this.entry === entry) this.entry = null
    }
}

/** Provided by the app shell; without one (a preview) there is no rail to hand a month to. */
val LocalRailMonthHost = staticCompositionLocalOf<RailMonthHost?> { null }

/** Puts a screen's month in the rail for as long as this is composed. Does nothing where there is no rail. */
@Composable
fun RegisterRailMonth(month: String, onPick: () -> Unit) {
    val host = LocalRailMonthHost.current ?: return
    val currentOnPick by rememberUpdatedState(onPick)
    val entry = remember { MonthEntry(month) { currentOnPick() } }
    // The entry is changed where it stands rather than shown again, so a screen on its way out cannot take the slot back.
    SideEffect { entry.month = month }
    DisposableEffect(host, entry) {
        host.show(entry)
        onDispose { host.hide(entry) }
    }
}
