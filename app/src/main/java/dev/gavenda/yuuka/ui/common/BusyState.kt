package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.remote.ApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Tracks which keyed mutate actions (e.g. "delete:42") are in flight, so only that action's button disables and spins rather than the whole screen. */
@Composable
fun rememberBusyState(): BusyState {
    val scope = rememberCoroutineScope()
    var busyKeys by remember { mutableStateOf(emptySet<String>()) }
    val genericErrorMessage = stringResource(R.string.error_generic)
    return remember(scope) {
        BusyState(scope, get = { busyKeys }, set = { busyKeys = it }, genericErrorMessage = genericErrorMessage)
    }
}

class BusyState internal constructor(
    private val scope: CoroutineScope,
    private val get: () -> Set<String>,
    private val set: (Set<String>) -> Unit,
    private val genericErrorMessage: String,
) {
    fun isBusy(key: String): Boolean = key in get()

    /**
     * Runs [action] under [key], disabling/spinning whatever button checks [isBusy] for it. Surfaces
     * an [ApiError]'s message as a snackbar; on success, calls [onSuccess] and, if given, shows
     * [successMessage] as a confirmation snackbar. A second call for a [key] already running is ignored.
     */
    fun run(
        key: String,
        snackbarHostState: SnackbarHostState,
        successMessage: String? = null,
        onSuccess: (() -> Unit)? = null,
        action: suspend () -> Unit,
    ) {
        if (isBusy(key)) return
        scope.launch {
            set(get() + key)
            val failure = try {
                action()
                null
            } catch (e: ApiError) {
                e
            } finally {
                // Released before any snackbar: showSnackbar suspends for as long as the message is
                // on screen, and the button must not spin, refusing presses, all that while.
                set(get() - key)
            }
            if (failure == null) {
                onSuccess?.invoke()
                if (successMessage != null) snackbarHostState.showSnackbar(successMessage)
            } else {
                snackbarHostState.showSnackbar(failure.message ?: genericErrorMessage)
            }
        }
    }

    /**
     * Runs [action] under [key] like [run], but leaves success/error handling entirely to the
     * caller — for the rare mutation whose failure isn't just a snackbar (e.g. a 409 that turns
     * into a confirmation step rather than an error).
     */
    fun launch(key: String, action: suspend () -> Unit) {
        if (isBusy(key)) return
        scope.launch {
            set(get() + key)
            try {
                action()
            } finally {
                set(get() - key)
            }
        }
    }
}
