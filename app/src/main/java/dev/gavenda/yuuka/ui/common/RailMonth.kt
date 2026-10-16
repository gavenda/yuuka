package dev.gavenda.yuuka.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The month the screen on show is looking at, as the navigation rail draws it: [month] (`YYYY-MM`) is what
 * the open rail labels the button with, and [onPick] opens the screen's own month picker.
 */
class MonthEntry(val month: String, val onPick: () -> Unit)

/** Provided by the app shell; without one (a preview) there is no rail to hand a month to. */
val LocalRailMonth = staticCompositionLocalOf<RailSlot<MonthEntry>?> { null }

/** Puts a screen's month in the rail for as long as this is composed. Does nothing where there is no rail. */
@Composable
fun RegisterRailMonth(month: String, onPick: () -> Unit) {
    LocalRailMonth.current?.Register(MonthEntry(month, onPick))
}
