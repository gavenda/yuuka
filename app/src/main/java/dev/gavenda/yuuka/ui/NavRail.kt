package dev.gavenda.yuuka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.BuildConfig
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.auth.IdTokenClaims
import dev.gavenda.yuuka.ui.common.FabEntry
import dev.gavenda.yuuka.ui.common.MutationLoadingIndicator
import dev.gavenda.yuuka.ui.common.RailFabHost
import dev.gavenda.yuuka.ui.common.UserAvatar

/** The gap Material's wide rail leaves between its header and its items (`HeaderSpaceMinimum`, which is not public). */
private val RAIL_HEADER_GAP = 40.dp

/**
 * The navigation rail of a wide window, mirroring `NavRail.vue`: slim it shows the daily destinations
 * and the hide-amounts switch under a menu button; open, the same items gain their labels and a "More"
 * group (Budget, Categories, Tags, Subscriptions, Save the Change, Settings, Sign out) and the signed-in
 * account appear.
 *
 * [docked] decides how it opens. Docked, it is a plain rail that pushes the page aside; otherwise it
 * is a modal rail that floats over the page behind a scrim, and closes again once a choice is made —
 * a floating rail is in the way once it has done its job.
 *
 * The items are one scrolling column rather than direct children of the rail, which lays out its
 * children without scrolling: on a short window the open rail would otherwise run off the bottom.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YuukaNavRail(
    docked: Boolean,
    state: WideNavigationRailState,
    currentRoute: String?,
    claims: IdTokenClaims?,
    amountsHidden: Boolean,
    fabHost: RailFabHost,
    onExpandedChange: (Boolean) -> Unit,
    onNavigate: (String) -> Unit,
    onNavigateDetail: (String) -> Unit,
    onToggleAmounts: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    val afterChoice = { if (!docked && expanded) onExpandedChange(false) }

    val header: @Composable () -> Unit = {
        Column {
            // The app's mark, in either state, with its name and version beside it once the rail is open. It
            // is a mark rather than a control — no click, and never the menu button beneath it — so nothing
            // announces it or mistakes it for one.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 28.dp, bottom = 12.dp).clearAndSetSemantics {},
            ) {
                Image(
                    painter = painterResource(R.drawable.yuuka_logo),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandHorizontally(),
                    exit = fadeOut() + shrinkHorizontally(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 12.dp, end = 16.dp),
                    ) {
                        Text(
                            stringResource(R.string.brand_name),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline(),
                        )
                        Text(
                            "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                }
            }
            IconButton(modifier = Modifier.padding(start = 24.dp), onClick = { onExpandedChange(!expanded) }) {
                Icon(
                    if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                    contentDescription = stringResource(if (expanded) R.string.cd_collapse_navigation else R.string.cd_expand_navigation),
                )
            }
            RailFab(host = fabHost, railExpanded = expanded, afterRun = afterChoice)
        }
    }

    val items: @Composable () -> Unit = {
        // The rail spaces its children apart while slim and packs them once open; do the same by hand.
        val spacing by animateDpAsState(if (expanded) 0.dp else 4.dp, label = "railItemSpacing")

        // The rail measures its content to the space under the header, then places it a header gap lower
        // still, so without the gap taken back here the foot would sit under the rail's bottom edge.
        Column(modifier = Modifier.fillMaxHeight().padding(bottom = RAIL_HEADER_GAP + 16.dp)) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing),
            ) {
                RAIL_DESTINATIONS.forEach { destination ->
                    RailItem(
                        label = stringResource(destination.labelRes),
                        icon = destination.icon,
                        selected = destination.route == currentRoute,
                        expanded = expanded,
                        onClick = {
                            onNavigate(destination.route)
                            afterChoice()
                        },
                    )
                }

                // Everything past the daily destinations lives behind the menu: the other ledger screens, then
                // what is set up once and left alone. Save the Change sits with the screens and Settings is a
                // screen too.
                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column {
                        Text(
                            stringResource(R.string.nav_more),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
                        )
                        RAIL_MORE_DESTINATIONS.forEach { destination ->
                            RailItem(
                                label = stringResource(destination.labelRes),
                                icon = destination.icon,
                                selected = destination.route == currentRoute,
                                expanded = true,
                                onClick = {
                                    onNavigate(destination.route)
                                    afterChoice()
                                },
                            )
                        }
                        RailItem(
                            label = stringResource(R.string.destination_save_the_change),
                            icon = Icons.Filled.Savings,
                            selected = currentRoute == SAVE_THE_CHANGE_ROUTE,
                            expanded = true,
                            onClick = {
                                onNavigateDetail(SAVE_THE_CHANGE_ROUTE)
                                afterChoice()
                            },
                        )
                        RailItem(
                            label = stringResource(R.string.destination_settings),
                            icon = Icons.Filled.Settings,
                            selected = currentRoute == SETTINGS_ROUTE,
                            expanded = true,
                            onClick = {
                                onNavigateDetail(SETTINGS_ROUTE)
                                afterChoice()
                            },
                        )
                        RailItem(
                            label = stringResource(R.string.action_sign_out),
                            icon = Icons.AutoMirrored.Filled.Logout,
                            selected = false,
                            expanded = true,
                            onClick = {
                                afterChoice()
                                onSignOut()
                            },
                        )
                    }
                }

            }

            // The foot stays at the bottom of the rail however far the destinations above scroll. The display
            // switch is in reach in both states: a phone keeps it in its top bar, but a wide window has none.
            Column(verticalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.padding(vertical = 16.dp)) {
                RailItem(
                    label = stringResource(if (amountsHidden) R.string.cd_show_amounts else R.string.cd_hide_amounts),
                    icon = if (amountsHidden) Icons.Filled.MoneyOff else Icons.Filled.AttachMoney,
                    selected = false,
                    expanded = expanded,
                    onClick = onToggleAmounts,
                    labelWhenSlim = false,
                )

                // Only in the open rail, which grows out of nothing rather than appearing.
                AnimatedVisibility(
                    visible = expanded && claims != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Card(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                        onClick = {},
                        shape = RoundedCornerShape(32.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )) {
                        AccountSummary(
                            claims = claims,
                            fallbackName = stringResource(R.string.brand_name),
                            modifier = Modifier.widthIn(max = 220.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }

                }
            }
        }
    }

    if (docked) {
        WideNavigationRail(modifier = modifier, state = state, header = header, content = items)
    } else {
        ModalWideNavigationRail(modifier = modifier, state = state, header = header, content = items)
    }
}

/**
 * The screen's leading action, under the menu button as Material places it: an icon FAB while the rail is
 * slim, an extended one when open. Its slot opens when a screen brings a button and closes when the last
 * one goes, sliding the destinations with it. A button with [FabEntry.actions] lists them in a menu.
 */
@Composable
private fun RailFab(host: RailFabHost, railExpanded: Boolean, afterRun: () -> Unit) {
    val entry = host.entry
    // The slot animates shut after the entry is gone, so remember what it last held to draw while it does.
    val lastShown = remember { arrayOfNulls<FabEntry>(1) }
    if (entry != null) lastShown[0] = entry
    var menuOpen by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = entry != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val shown = lastShown[0] ?: return@AnimatedVisibility
        val active = shown.enabled && !shown.busy

        Box(modifier = Modifier.padding(start = 20.dp, top = 12.dp)) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (active) {
                        if (shown.actions.isEmpty()) {
                            shown.onClick()
                            afterRun()
                        } else {
                            menuOpen = true
                        }
                    }
                },
                expanded = railExpanded,
                icon = {
                    if (shown.busy) MutationLoadingIndicator(size = 24.dp) else Icon(
                        shown.icon,
                        contentDescription = null
                    )
                },
                text = { Text(shown.label) },
                // A collapsed button shows only its icon, so the label has to be said some other way.
                modifier = Modifier.semantics { contentDescription = shown.label },
                containerColor = if (shown.enabled) {
                    FloatingActionButtonDefaults.containerColor
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                },
                contentColor = if (shown.enabled) {
                    contentColorFor(FloatingActionButtonDefaults.containerColor)
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                shown.actions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        leadingIcon = action.icon,
                        onClick = {
                            menuOpen = false
                            action.onClick()
                            afterRun()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RailItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    /** A slim rail labels its items under the icon; an action too wordy for that goes bare, its label said only to accessibility. */
    labelWhenSlim: Boolean = true,
) {
    val labelled = expanded || labelWhenSlim
    WideNavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = if (labelled) null else label) },
        label = if (labelled) ({ Text(label) }) else null,
        railExpanded = expanded,
    )
}

/**
 * Who is signed in: the avatar, their name and — only when the name isn't already standing in for it —
 * their email. Shared by the phone's drawer header and the open rail, in place of a Profile screen.
 */
@Composable
fun AccountSummary(claims: IdTokenClaims?, fallbackName: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        UserAvatar(name = claims?.name ?: claims?.email, pictureUrl = claims?.picture, size = 40)
        Column {
            Text(
                claims?.name ?: claims?.email ?: fallbackName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (claims?.name != null && claims.email != null) {
                Text(
                    claims.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
