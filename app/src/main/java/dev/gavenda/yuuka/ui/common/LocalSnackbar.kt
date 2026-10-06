package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/** The app-wide snackbar host, provided once by [dev.gavenda.yuuka.ui.YuukaApp] so any screen can surface a toast without threading a callback through every composable. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided — LocalSnackbarHostState must be supplied by YuukaApp")
}

/**
 * The windows above the app's Scaffold that are drawing the shared snackbar themselves, lowest first. Every
 * host is bound to the one [LocalSnackbarHostState], so without this each of them would draw the same
 * message at once: under a dialog's scrim as well as on the dialog, or on a form that is on its way out as
 * well as on the screen it is uncovering. Only the topmost draws; the app's own host draws when there is none.
 */
class SnackbarOverlays {
    private val hosts = mutableStateListOf<Any>()
    val none: Boolean get() = hosts.isEmpty()
    internal fun isTop(host: Any): Boolean = hosts.lastOrNull() === host
    internal fun add(host: Any) { hosts += host }
    internal fun remove(host: Any) { hosts -= host }
}

val LocalSnackbarOverlays = staticCompositionLocalOf { SnackbarOverlays() }

/** The shared snackbar, drawn in a window of its own above the app's Scaffold. It takes the message over from whatever is below it while it is composed. */
@Composable
fun OverlaySnackbarHost(modifier: Modifier = Modifier) {
    val overlays = LocalSnackbarOverlays.current
    val token = remember { Any() }
    DisposableEffect(overlays) {
        overlays.add(token)
        onDispose { overlays.remove(token) }
    }
    if (overlays.isTop(token)) SnackbarHost(LocalSnackbarHostState.current, modifier = modifier)
}
