package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/** The app-wide snackbar host, provided once by [dev.gavenda.yuuka.ui.YuukaApp] so any screen can surface a toast without threading a callback through every composable. */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState> {
    error("No SnackbarHostState provided — LocalSnackbarHostState must be supplied by YuukaApp")
}
