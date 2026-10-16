package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.delay
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.Alignment
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.formatMonth
import kotlinx.coroutines.launch

/**
 * What a screen's own app bar needs from the shell around it: whether a navigation rail has taken the
 * bar's place, the shell-wide actions a bar carries (the hide-amounts switch, the drawer, going back)
 * and how much has yet to reach the server.
 *
 * Every screen draws its own bar — `YuukaApp` has none — so this is what the bars share rather than a
 * bar of its own. A screen never reads it directly; [ScreenTopBar] and [DetailTopBar] do.
 */
@Immutable
class AppBarShell(
    val useRail: Boolean = false,
    val amountsHidden: Boolean = false,
    val unsentChanges: Int = 0,
    val sending: Boolean = false,
    val onOpenNavigation: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onToggleAmounts: () -> Unit = {},
)

/** Provided by the app shell for as long as a signed-in screen is on show. */
val LocalAppBarShell = compositionLocalOf { AppBarShell() }

/**
 * Hangs an app bar's scroll behaviour on a screen's `Scaffold` — but only where there is a bar. A rail window
 * draws none, so nothing ever tells the behaviour how tall it is; left connected it would take every scroll as the
 * bar collapsing and the page would not move.
 */
@Composable
fun Modifier.appBarScroll(scrollBehavior: TopAppBarScrollBehavior): Modifier =
    if (LocalAppBarShell.current.useRail) this else this.nestedScroll(scrollBehavior.nestedScrollConnection)

/**
 * How far in from the page's edges a row of buttons directly under a screen's bar starts, so that its buttons
 * stand under the bar's own. Material keeps a bar's buttons 4dp from the edge, which is what puts their icons
 * on the page's 16dp margin. A rail window has no bar to line up with, so there the row keeps to the margin
 * itself, as the field above it does.
 */
val barAlignedEdgePadding: Dp
    @Composable get() = if (LocalAppBarShell.current.useRail) 16.dp else 4.dp

/** The same, for a screen whose bar is [MonthSearchTopBar]: Material's app bar with search scrolls by a behaviour of its own. */
@Composable
fun Modifier.appBarScroll(scrollBehavior: SearchBarScrollBehavior): Modifier =
    if (LocalAppBarShell.current.useRail) this else this.nestedScroll(scrollBehavior.nestedScrollConnection)

/**
 * A top-level destination's app bar: the drawer button, its title centred on the bar and the hide-amounts
 * switch, with the unsent-changes banner beneath.
 *
 * A wide window's rail already marks the current destination and carries both actions, so there is no
 * bar to draw there — only the banner, which belongs to no destination. Put this in the screen's own
 * `Scaffold(topBar = ...)`; it handles its own status-bar inset, so the screen's Scaffold takes none
 * (`contentWindowInsets = WindowInsets(0)`). The bar stays where it is and keeps one colour, so a screen
 * needs no [scrollBehavior] for it; one that passes one hangs it on its Scaffold with [appBarScroll].
 */
@Composable
fun ScreenTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = { NavigationMenuButton() },
    actions: @Composable RowScope.() -> Unit = { AmountVisibilityAction() },
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val shell = LocalAppBarShell.current
    Column {
        if (!shell.useRail) {
            CenterAlignedTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = navigationIcon,
                actions = actions,
                colors = steadyTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        }
        UnsentChangesBanner(count = shell.unsentChanges, sending = shell.sending)
    }
}

/**
 * A month-scoped destination's app bar (Dashboard, Budget): the drawer button, the title centred on the bar,
 * the hide-amounts switch and, after it, the button that opens the month picker. [scrollBehavior] — an
 * `enterAlways` one — takes the bar off the screen as the user scrolls down. The screen must hang that
 * behaviour's `nestedScrollConnection` on its own Scaffold.
 *
 * A rail window has no bar, so there the month is handed to the rail, which keeps the button at its foot
 * under the hide-amounts switch. Either way the picker it opens is this screen's own.
 */
@Composable
fun MonthTopBar(month: String, onMonthChange: (String) -> Unit, scrollBehavior: TopAppBarScrollBehavior, title: String? = null) {
    val shell = LocalAppBarShell.current
    val pickMonth = rememberMonthPicker(month, onMonthChange)

    Column {
        if (shell.useRail) {
            RegisterRailMonth(month = month, onPick = pickMonth)
        } else {
            CenterAlignedTopAppBar(
                title = { if (title != null) Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { NavigationMenuButton() },
                actions = {
                    AmountVisibilityAction()
                    MonthAction(month = month, onClick = pickMonth)
                },
                colors = steadyTopAppBarColors(),
                scrollBehavior = scrollBehavior,
            )
        }
        UnsentChangesBanner(count = shell.unsentChanges, sending = shell.sending)
    }
}

/**
 * [MonthTopBar] with the screen's search in it (Transactions): Material's app bar with search — the drawer
 * button, the search bar, then the hide-amounts switch and the month button. Material's bar is not typed in
 * where it stands: pressing it opens the full-screen search view, where [search] is typed and [results] is
 * what shows beneath the field as it is. Back, or the keyboard's search key, returns to the screen with the
 * bar still holding what was typed. [scrollBehavior] — an `enterAlways` one — takes the bar off the screen as
 * the user scrolls down; the screen hangs it on its own Scaffold with [appBarScroll].
 *
 * A rail window has no bar, so there the search bar is all that is left of this one: Material's search bar on
 * its own at the top of the page, opening into the same full-screen view, with the month handed to the rail.
 * A screen that is not month-scoped (Accounts) leaves out [month] and gets the bar without the month button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthSearchTopBar(
    search: TextFieldState,
    searchHint: String,
    scrollBehavior: SearchBarScrollBehavior,
    month: String? = null,
    onMonthChange: (String) -> Unit = {},
    results: @Composable ColumnScope.() -> Unit,
) {
    val shell = LocalAppBarShell.current
    val pickMonth = month?.let { rememberMonthPicker(it, onMonthChange) }

    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    val expanded = searchBarState.currentValue == SearchBarValue.Expanded
    val collapse: () -> Unit = { scope.launch { searchBarState.animateToCollapsed() } }
    val hasText = search.text.isNotEmpty()

    // Drawn twice by Material, once where the search bar rests and once in the view it opens into.
    val inputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = search,
            searchBarState = searchBarState,
            // The list is filtered as it is typed, so the search key has nothing left to send: it puts the view away.
            onSearch = { collapse() },
            // Between a phone bar's buttons there is room for a word; anywhere wider there is room to say what is searched.
            placeholder = {
                Text(
                    if (expanded || shell.useRail) searchHint else stringResource(R.string.search),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingIcon = {
                if (expanded) {
                    IconButton(onClick = collapse) {
                        Icon(painterResource(R.drawable.arrow_back_48px), contentDescription = stringResource(R.string.cd_back))
                    }
                } else {
                    Icon(painterResource(R.drawable.search_48px), contentDescription = null)
                }
            },
            // Left out rather than left empty: an empty slot would still take its width from the bar.
            trailingIcon = if (hasText) ({ ClearSearchButton(search) }) else null,
        )
    }

    Column {
        if (shell.useRail) {
            if (month != null && pickMonth != null) RegisterRailMonth(month = month, onPick = pickMonth)
            // With no bar to slide out from under, the banner leads the page and the search bar follows it.
            UnsentChangesBanner(count = shell.unsentChanges, sending = shell.sending)
            SearchBar(
                state = searchBarState,
                inputField = inputField,
                // The page's width between its margins, rather than the width Material would stop a search bar at.
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        } else {
            // Wrap in a Box that stays pinned behind the status bar
            Box(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface) // Keeps a solid color behind system icons
                    .statusBarsPadding() // Ensures the app bar layout STARTS below the system bar
                    .clipToBounds() // CRITICAL: Clips the search bar so it hides instead of overlapping system icons
            ) {
                AppBarWithSearch(
                    state = searchBarState,
                    windowInsets = WindowInsets.statusBars,
                    inputField = inputField,
                    navigationIcon = { NavigationMenuButton() },
                    actions = {
                        AmountVisibilityAction()
                        if (month != null && pickMonth != null) MonthAction(month = month, onClick = pickMonth)
                    },
                    // One colour, as every bar here keeps: neither the bar nor the field in it lifts when the list scrolls beneath.
                    colors = SearchBarDefaults.appBarWithSearchColors(
                        scrolledSearchBarContainerColor = Color.Unspecified,
                        scrolledAppBarContainerColor = Color.Unspecified,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            }

            UnsentChangesBanner(count = shell.unsentChanges, sending = shell.sending)
        }
        ExpandedFullScreenSearchBar(state = searchBarState, inputField = inputField, content = results)
    }
}

/** A month-scoped bar's month picker, shown for as long as it is open. Returns what opens it. */
@Composable
private fun rememberMonthPicker(month: String, onMonthChange: (String) -> Unit): () -> Unit {
    var picking by rememberSaveable { mutableStateOf(false) }

    if (picking) {
        MonthPickerDialog(
            month = month,
            onDismiss = { picking = false },
            onPicked = {
                picking = false
                onMonthChange(it)
            },
        )
    }

    return { picking = true }
}

@Composable
private fun ClearSearchButton(state: TextFieldState) {
    IconButton(onClick = { state.clearText() }) {
        Icon(painterResource(R.drawable.close_48px), contentDescription = stringResource(R.string.cd_clear))
    }
}

/**
 * The app bar of a detail screen (Settings, and Account types opened from it): back where the drawer
 * button would be. Save the Change is reached from the drawer too but keeps the drawer button, through
 * [ScreenTopBar] with no actions.
 *
 * They show no amounts either, so the bar has no hide-amounts switch.
 *
 * These screens carry no Save — a choice made on one of them is written the moment it is made, the
 * same way the theme switches always were — so the bar has no action of its own to state and the rail
 * no leading action to lend them.
 */
@Composable
fun DetailTopBar(title: String) {
    ScreenTopBar(title = title, navigationIcon = { BackButton() }, actions = {})
}

/**
 * A bar's colours with one left out: Material lifts a bar a tonal step once content has scrolled beneath it,
 * and here a bar keeps the colour it rests on wherever the page is.
 */
@Composable
private fun steadyTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors().let { it.copy(scrolledContainerColor = it.containerColor) }

/** Opens the phone's navigation drawer. */
@Composable
private fun NavigationMenuButton() {
    val shell = LocalAppBarShell.current
    IconButton(onClick = shell.onOpenNavigation) {
        Icon(painterResource(R.drawable.menu_48px), contentDescription = stringResource(R.string.cd_menu))
    }
}

/** Leaves a screen that was pushed on top of a tab. */
@Composable
private fun BackButton() {
    val shell = LocalAppBarShell.current
    IconButton(onClick = shell.onBack) {
        Icon(painterResource(R.drawable.arrow_back_48px), contentDescription = stringResource(R.string.cd_back))
    }
}

/** The "someone is looking over my shoulder" switch: masks every figure on screen at once. */
@Composable
private fun AmountVisibilityAction() {
    val shell = LocalAppBarShell.current
    IconButton(onClick = shell.onToggleAmounts) {
        Icon(
            painterResource(if (shell.amountsHidden) R.drawable.money_off_48px else R.drawable.attach_money_48px),
            contentDescription = stringResource(
                if (shell.amountsHidden) R.string.cd_show_amounts else R.string.cd_hide_amounts,
            ),
        )
    }
}

/** Opens a month-scoped screen's month picker. An icon cannot say which month is on show, so its description does. */
@Composable
private fun MonthAction(month: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.calendar_month_48px),
            contentDescription = stringResource(R.string.cd_pick_month, formatMonth(month)),
        )
    }
}

/**
 * What has not reached the server yet.
 *
 * Writes are local-first, so a save no longer fails for want of a connection
 * and there is nothing to warn about — but a figure the server has not been
 * told about is worth naming, both so the user knows their morning's spending
 * is still only on this phone and so a queue that has stopped draining is
 * visible rather than silent. It says nothing at all when there is nothing
 * waiting, which is almost always.
 */
@Composable
private fun UnsentChangesBanner(count: Int, sending: Boolean) {
    // The queue can empty a moment after the banner appears, so it stays up for a beat saying what it last
    // said, long enough to be read, and only then goes.
    var shown by remember { mutableStateOf(count > 0) }
    var last by remember { mutableStateOf(count to sending) }
    if (count > 0) last = count to sending
    LaunchedEffect(count > 0) {
        if (count > 0) {
            shown = true
        } else {
            delay(BANNER_LINGER_MS)
            shown = false
        }
    }
    // It slides down from under the bar and back up into it, rather than growing out of its corner.
    AnimatedVisibility(
        visible = shown,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
    ) {
        // Worth noticing, but neither a failure nor an action: tertiary, not error or primary.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.tertiaryContainer,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // The cat print's mark, small, over the line.
                Icon(CatIcon, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(
                    // Once the queue is empty it says so, and in her words, for as long as it lingers.
                    text = if (count > 0) {
                        pluralStringResource(
                            if (last.second) R.plurals.sync_sending else R.plurals.sync_unsent,
                            last.first,
                            last.first,
                        )
                    } else {
                        stringResource(R.string.sync_all_sent)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private const val BANNER_LINGER_MS = 2500L
