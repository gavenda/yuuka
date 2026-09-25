package dev.gavenda.yuuka.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R

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
 * A top-level destination's app bar: its title, the drawer button and the hide-amounts switch, with the
 * unsent-changes banner beneath.
 *
 * A wide window's rail already marks the current destination and carries both actions, so there is no
 * bar to draw there — only the banner, which belongs to no destination. Put this in the screen's own
 * `Scaffold(topBar = ...)`; it handles its own status-bar inset, so the screen's Scaffold takes none
 * (`contentWindowInsets = WindowInsets(0)`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = { NavigationMenuButton() },
    actions: @Composable RowScope.() -> Unit = { AmountVisibilityAction() },
) {
    val shell = LocalAppBarShell.current
    Column {
        if (!shell.useRail) {
            TopAppBar(title = { Text(title) }, navigationIcon = navigationIcon, actions = actions)
        }
        UnsentChangesBanner(count = shell.unsentChanges, sending = shell.sending)
    }
}

/**
 * A top-level destination's app bar in its large form (Accounts): the title starts a headline on its own
 * row and shrinks back into an ordinary bar as the page is scrolled, settling there once collapsed.
 *
 * Pair it with an `exitUntilCollapsed` behaviour — unlike the month bar's `enterAlways`, the collapsed
 * row stays put rather than leaving, so the drawer button and the hide-amounts switch are always
 * reachable. The screen must hang the behaviour's `nestedScrollConnection` on its own Scaffold.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LargeScreenTopBar(title: String, scrollBehavior: TopAppBarScrollBehavior) {
    val shell = LocalAppBarShell.current
    Column {
        // A rail window has no bar at all — it already marks the destination and carries both actions.
        if (!shell.useRail) {
            LargeFlexibleTopAppBar(
                title = { Text(title) },
                navigationIcon = { NavigationMenuButton() },
                actions = { AmountVisibilityAction() },
                scrollBehavior = scrollBehavior,
            )
        }
        UnsentChangesBanner(count = shell.unsentChanges, sending = shell.sending)
    }
}

/**
 * A month-scoped destination's app bar (Dashboard, Transactions): the month switcher itself where the
 * title would be, with the drawer button and the hide-amounts switch untouched around it.
 *
 * The switcher used to sit in the page, which cost a row of the screen and scrolled away with the
 * content. In the bar it is always a thumb away, and [scrollBehavior] — an `enterAlways` one — takes
 * the whole bar off the screen as the user scrolls down and brings it straight back on the first
 * scroll up. The screen must hang that behaviour's `nestedScrollConnection` on its own Scaffold, or
 * the bar never moves.
 *
 * A rail window has no bar to put it in, so there it keeps the card it always had.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthTopBar(month: String, onMonthChange: (String) -> Unit, scrollBehavior: TopAppBarScrollBehavior) {
    val shell = LocalAppBarShell.current
    Column {
        if (shell.useRail) {
            MonthSwitcher(month = month, onMonthChange = onMonthChange, modifier = Modifier.fillMaxWidth().padding(16.dp))
        } else {
            TopAppBar(
                title = { MonthTitle(month = month, onMonthChange = onMonthChange) },
                navigationIcon = { NavigationMenuButton() },
                actions = { AmountVisibilityAction() },
                scrollBehavior = scrollBehavior,
            )
        }
        UnsentChangesBanner(count = shell.unsentChanges, sending = shell.sending)
    }
}

/**
 * The app bar of a screen reached from the drawer rather than a tab (Settings, Save the Change): back
 * where the drawer button would be.
 *
 * These screens carry no Save — a choice made on one of them is written the moment it is made, the
 * same way the theme switches always were — so the bar has no action of its own to state and the rail
 * no leading action to lend them.
 */
@Composable
fun DetailTopBar(title: String) {
    ScreenTopBar(title = title, navigationIcon = { BackButton() })
}

/** Opens the phone's navigation drawer. */
@Composable
fun NavigationMenuButton() {
    val shell = LocalAppBarShell.current
    IconButton(onClick = shell.onOpenNavigation) {
        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
    }
}

/** Leaves a screen that was pushed on top of a tab. */
@Composable
fun BackButton() {
    val shell = LocalAppBarShell.current
    IconButton(onClick = shell.onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
    }
}

/** The "someone is looking over my shoulder" switch: masks every figure on screen at once. */
@Composable
fun AmountVisibilityAction() {
    val shell = LocalAppBarShell.current
    IconButton(onClick = shell.onToggleAmounts) {
        Icon(
            if (shell.amountsHidden) Icons.Filled.MoneyOff else Icons.Filled.AttachMoney,
            contentDescription = stringResource(
                if (shell.amountsHidden) R.string.cd_show_amounts else R.string.cd_hide_amounts,
            ),
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
    AnimatedVisibility(visible = count > 0) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = pluralStringResource(
                    if (sending) R.plurals.sync_sending else R.plurals.sync_unsent,
                    count,
                    count,
                ),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
