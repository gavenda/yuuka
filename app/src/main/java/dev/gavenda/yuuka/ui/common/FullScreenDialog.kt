package dev.gavenda.yuuka.ui.common

import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import dev.gavenda.yuuka.R

/**
 * A task that takes the screen over — a form with more fields than a sheet holds comfortably, or a list to
 * search — as Material 3's full-screen dialog: close where the drawer button would be, the title, and Save
 * as the bar's one action. Material keeps that shape for a phone, so a window with a rail gets an ordinary
 * dialog of the same content instead, with its buttons at the foot.
 *
 * - [onSave] left out means there is nothing to save (a list that writes each choice as it is made), and the
 *   dialog has only its close. [saveEnabled] is what a form binds `form.valid(...)` to.
 * - [dirty] says the form no longer holds what it opened with; closing it then asks first, since a stray back
 *   would otherwise throw the entry away. Nothing closes while [submitting].
 * - It opens in a window of its own, above the app's Scaffold, so it hosts the shared snackbar itself.
 *
 * The content is laid out as a form's column, scrolling under the bar; pass [scrollable] false for content
 * that scrolls itself, such as a lazy list.
 *
 * Where the page beside a rail has room for it (see [FullScreenDialogHostState.paneRoom]) it is neither: it is
 * Material's supporting pane, standing at the page's end with the screen that opened it still usable beside it,
 * and with the full-screen dialog's bar, since it is as tall as the page. [beside] false keeps a dialog a dialog
 * even then — the display currency, which Settings opens while it may already have Account types beside it.
 *
 * On a phone it slides up over the screen and back down off it. A screen shows a form by composing this and
 * closes it by no longer doing so, which leaves nothing here to play the way out — so the dialog is drawn by
 * [FullScreenDialogHost] at the root of the app, which keeps it for as long as the slide down takes. This
 * call only says what it should hold.
 */
@Composable
fun FullScreenDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    saveEnabled: Boolean = true,
    submitting: Boolean = false,
    dirty: Boolean = false,
    scrollable: Boolean = true,
    beside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = FullScreenDialogSpec(title, onDismiss, onSave, saveEnabled, submitting, dirty, scrollable, beside, content)
    val host = LocalFullScreenDialogHost.current
    if (host == null) {
        // No host above (a preview): drawn here, and gone the moment it is no longer composed.
        FullScreenDialogWindow(spec, leaving = false, onGone = {})
        return
    }
    val locals = currentCompositionLocalContext
    val entry = remember { FullScreenDialogEntry(spec, locals) }
    entry.spec = spec
    entry.locals = locals
    DisposableEffect(host, entry) {
        host.entries += entry
        onDispose { entry.leaving = true }
    }
}

/** Everything a full-screen dialog is given, as one value the host can hold on to. */
internal class FullScreenDialogSpec(
    val title: String,
    val onDismiss: () -> Unit,
    val onSave: (() -> Unit)?,
    val saveEnabled: Boolean,
    val submitting: Boolean,
    val dirty: Boolean,
    val scrollable: Boolean,
    val beside: Boolean,
    val content: @Composable ColumnScope.() -> Unit,
)

/** One dialog the host is drawing: what it last held, and whether the screen that opened it has let go of it. */
class FullScreenDialogEntry internal constructor(spec: FullScreenDialogSpec, locals: CompositionLocalContext) {
    internal var spec by mutableStateOf(spec)
    internal var locals by mutableStateOf(locals)
    internal var leaving by mutableStateOf(false)

    /** Standing in the supporting pane, closing it has asked whether to discard its changes. */
    internal var confirmingDiscard by mutableStateOf(false)

    /** Close as the pane's close button would: asking first if it holds changes, and not while it is saving. */
    internal fun requestClose() {
        if (spec.submitting || leaving) return
        if (spec.dirty) confirmingDiscard = true else spec.onDismiss()
    }
}

/** The dialogs that are open, or still on their way out, and whether the page has room to stand one beside it. */
class FullScreenDialogHostState {
    internal val entries = mutableStateListOf<FullScreenDialogEntry>()

    /** Set by the shell: the page is wide enough to keep its screen usable beside a supporting pane. */
    var paneRoom by mutableStateOf(false)

    /** Another form asked for while the one in the pane holds changes, waiting on the answer to discarding them. */
    internal var pendingSwitch by mutableStateOf<(() -> Unit)?>(null)

    internal fun inPane(entry: FullScreenDialogEntry) = paneRoom && entry.spec.beside

    /** Whether a form is standing in the supporting pane (not counting one on its way out). */
    val paneOpen: Boolean get() = entries.any { !it.leaving && inPane(it) }

    /**
     * Runs [action] — opening another form — straight away, unless the form in the pane holds changes: the
     * screen beside a pane can still be pressed, so opening a row there would otherwise throw them away. Then
     * it asks first, as closing the form would, and runs [action] on "Yes, discard them".
     */
    fun whenDiscardable(action: () -> Unit) {
        val open = entries.lastOrNull { !it.leaving && inPane(it) }
        if (open != null && open.spec.dirty) pendingSwitch = action else action()
    }
}

/**
 * What a screen opens a form through: it runs at once, unless the form standing in the supporting pane holds
 * changes, which are asked about first ([FullScreenDialogHostState.whenDiscardable]).
 */
@Composable
fun rememberFormSwitch(): (() -> Unit) -> Unit {
    val host = LocalFullScreenDialogHost.current
    return remember(host) { { action -> host?.whenDiscardable(action) ?: action() } }
}

val LocalFullScreenDialogHost = staticCompositionLocalOf<FullScreenDialogHostState?> { null }

/** Draws every [FullScreenDialog] the screens below have asked for; one sits at the root of the app. */
@Composable
fun FullScreenDialogHost(state: FullScreenDialogHostState) {
    // One the page has room to stand beside it is drawn by [FullScreenDialogPane] instead.
    state.entries.filterNot { state.inPane(it) }.forEach { entry ->
        key(entry) {
            // The dialog is composed here, not where it was asked for, so it is handed that place's locals.
            CompositionLocalProvider(entry.locals) {
                FullScreenDialogWindow(
                    spec = entry.spec,
                    leaving = entry.leaving,
                    onGone = { state.entries -= entry },
                )
            }
        }
    }
}

/** The dialog itself. [leaving] plays its way out, and [onGone] is called once that has finished. */
@Composable
private fun FullScreenDialogWindow(spec: FullScreenDialogSpec, leaving: Boolean, onGone: () -> Unit) {
    val title = spec.title
    val onDismiss = spec.onDismiss
    val onSave = spec.onSave
    val submitting = spec.submitting
    val dirty = spec.dirty
    val content = spec.content
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val close = {
        if (!submitting && !leaving) {
            if (dirty) confirmingDiscard = true else onDismiss()
        }
    }
    val canSave = spec.saveEnabled && !submitting
    val scroll = if (spec.scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier

    if (LocalAppBarShell.current.useRail) {
        // An ordinary dialog comes and goes as the platform's dialogs do.
        if (leaving) {
            LaunchedEffect(Unit) { onGone() }
            return
        }
        Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(modifier = Modifier.padding(24.dp).widthIn(max = 560.dp)) {
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp),
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).then(scroll).padding(horizontal = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            content = content,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        ) {
                            TextButton(onClick = close, enabled = !submitting) {
                                Text(stringResource(if (onSave != null) R.string.action_cancel else R.string.action_close))
                            }
                            if (onSave != null) {
                                Button(onClick = onSave, enabled = canSave) {
                                    if (submitting) MutationLoadingIndicator() else Text(stringResource(R.string.action_save))
                                }
                            }
                        }
                    }
                }
                // No snackbar host: one here would draw on the dialog itself. A save closes it before confirming.
            }
        }
    } else {
        Dialog(
            onDismissRequest = close,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            // The dialog is a window of its own, edge to edge, and starts with the platform's light icons whatever
            // the theme: on a light scheme the clock and battery would be white on white. The slide is the whole
            // of its entrance, so the window brings neither the platform's own dialog animation nor a scrim.
            val view = LocalView.current
            val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
            SideEffect {
                (view.parent as? DialogWindowProvider)?.window?.let { window ->
                    window.setWindowAnimations(0)
                    window.setDimAmount(0f)
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = lightBars
                        isAppearanceLightNavigationBars = lightBars
                    }
                }
            }

            val visible = remember { MutableTransitionState(false) }
            visible.targetState = !leaving
            if (leaving) {
                // The keyboard goes down with the form rather than hanging over the screen behind it.
                val focusManager = LocalFocusManager.current
                LaunchedEffect(Unit) { focusManager.clearFocus() }
                if (visible.isIdle && !visible.currentState) LaunchedEffect(Unit) { onGone() }
            }

            // Effects specs rather than spatial ones: the expressive spatial springs overshoot, and a full-screen
            // slide that overshoots shows the screen behind it along its bottom edge.
            AnimatedVisibility(
                visibleState = visible,
                enter = slideInVertically(MaterialTheme.motionScheme.slowEffectsSpec()) { it },
                exit = slideOutVertically(MaterialTheme.motionScheme.defaultEffectsSpec()) { it },
                // On its way out the form holds what it was last given, so nothing on it may be pressed again.
                modifier = if (leaving) Modifier.pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                } else Modifier,
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            navigationIcon = {
                                IconButton(onClick = close, enabled = !submitting) {
                                    Icon(painterResource(R.drawable.close_48px), contentDescription = stringResource(R.string.action_close))
                                }
                            },
                            actions = {
                                if (onSave != null) {
                                    Button(onClick = onSave, enabled = canSave) {
                                        if (submitting) MutationLoadingIndicator() else Text(stringResource(R.string.action_save))
                                    }
                                }
                            },
                        )
                    },
                    // A save closes the form and confirms itself in the same breath. The confirmation belongs to the
                    // screen the form is uncovering, so a form on its way out hands the snackbar back rather than
                    // carrying it off the bottom of the screen.
                    snackbarHost = { if (!leaving) OverlaySnackbarHost() },
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .consumeWindowInsets(padding)
                            .imePadding()
                            .then(scroll)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        content = content,
                    )
                }
            }
        }
    }

    if (confirmingDiscard && !leaving) {
        DiscardChangesDialog(onDiscard = { confirmingDiscard = false; onDismiss() }, onKeepEditing = { confirmingDiscard = false })
    }
}

/**
 * The supporting pane a form stands in where the page has room for one: the page's end, beside the screen that
 * opened it. The shell sizes it and decides when it shows ([FullScreenDialogHostState.paneOpen]); this draws
 * the form in it, with a full-screen dialog's bar since it is as tall as the page. Opening another form fades
 * the one before it out rather than sliding the pane away and back.
 */
@Composable
fun FullScreenDialogPane(state: FullScreenDialogHostState, modifier: Modifier = Modifier) {
    // Back closes the form standing in the pane before it leaves the screen beside it. The newest handler
    // takes back, and the nav display registers its own afresh as it moves, so this one is composed only
    // while a form stands here — registered as it opens, it is the newest.
    val open = state.entries.lastOrNull { !it.leaving && state.inPane(it) }
    if (open != null) key(open) { BackHandler { open.requestClose() } }
    Box(modifier) {
        state.entries.filter { state.inPane(it) }.forEach { entry ->
            key(entry) {
                CompositionLocalProvider(entry.locals) {
                    FormPane(entry = entry, onGone = { state.entries -= entry })
                }
            }
        }
    }
    state.pendingSwitch?.let { action ->
        DiscardChangesDialog(
            onDiscard = { state.pendingSwitch = null; action() },
            onKeepEditing = { state.pendingSwitch = null },
        )
    }
}

@Composable
private fun FormPane(entry: FullScreenDialogEntry, onGone: () -> Unit) {
    val spec = entry.spec
    val leaving = entry.leaving
    val onSave = spec.onSave
    val submitting = spec.submitting
    val close = entry::requestClose

    val visible = remember { MutableTransitionState(false) }
    visible.targetState = !leaving
    if (leaving) {
        val focusManager = LocalFocusManager.current
        LaunchedEffect(Unit) { focusManager.clearFocus() }
        if (visible.isIdle && !visible.currentState) LaunchedEffect(Unit) { onGone() }
    }
    AnimatedVisibility(
        visibleState = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                TopAppBar(
                    title = { Text(spec.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = close, enabled = !submitting) {
                            Icon(painterResource(R.drawable.close_48px), contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    actions = {
                        if (onSave != null) {
                            // Off the pane's rounded edge, which a phone's bar has no need to keep from.
                            Button(onClick = onSave, enabled = spec.saveEnabled && !submitting, modifier = Modifier.padding(end = 8.dp)) {
                                if (submitting) MutationLoadingIndicator() else Text(stringResource(R.string.action_save))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .imePadding()
                        .then(if (spec.scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    content = spec.content,
                )
            }
        }
    }

    if (entry.confirmingDiscard && !leaving) {
        DiscardChangesDialog(
            onDiscard = { entry.confirmingDiscard = false; spec.onDismiss() },
            onKeepEditing = { entry.confirmingDiscard = false },
        )
    }
}

/** "Discard your changes?", asked before a form holding changes is closed or replaced by another. */
@Composable
private fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        icon = { Icon(painterResource(R.drawable.delete_48px), contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(stringResource(R.string.discard_changes_title)) },
        text = { Text(stringResource(R.string.discard_changes_body)) },
        confirmButton = {
            Button(
                onClick = onDiscard,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(stringResource(R.string.action_discard)) }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.action_keep_editing)) }
        },
    )
}
