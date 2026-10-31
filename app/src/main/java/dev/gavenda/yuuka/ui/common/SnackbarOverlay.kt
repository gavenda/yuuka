package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Renders [content] with its own SnackbarHost anchored to the bottom, bound to the same
 * [LocalSnackbarHostState]. For a full-screen window over the Scaffold's — the search view — whose
 * bottom is the screen's, so the app-wide host beneath it would be hidden. Never for a dialog that sits
 * over the page: the snackbar would draw inside the dialog rather than along the screen's foot.
 */
@Composable
fun WithSnackbarOverlay(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        content()
        OverlaySnackbarHost(modifier = Modifier.align(Alignment.BottomCenter))
    }
}
