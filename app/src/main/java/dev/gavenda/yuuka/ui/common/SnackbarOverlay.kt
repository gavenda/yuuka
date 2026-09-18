package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Renders [content] with its own SnackbarHost anchored to the bottom, bound to the same
 * [LocalSnackbarHostState]. A ModalBottomSheet or AlertDialog opens in its own Android window,
 * layered above the main Scaffold's window, so the app-wide SnackbarHost there can never draw on
 * top of it — this gives the modal a host of its own onto the shared state instead.
 */
@Composable
fun WithSnackbarOverlay(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val snackbarHostState = LocalSnackbarHostState.current
    Box(modifier) {
        content()
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}
