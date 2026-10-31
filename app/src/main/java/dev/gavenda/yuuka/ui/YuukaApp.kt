package dev.gavenda.yuuka.ui

import dev.gavenda.yuuka.ui.common.catPattern
import dev.gavenda.yuuka.ui.investments.HoldingScreen
import dev.gavenda.yuuka.ui.investments.InvestmentsScreen
import dev.gavenda.yuuka.ui.common.CatPrint
import dev.gavenda.yuuka.ui.common.CatFade
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import dev.gavenda.yuuka.ui.common.CatIcon
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.util.lerp
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.auth.AuthManager
import dev.gavenda.yuuka.auth.AuthState
import dev.gavenda.yuuka.auth.decodeIdTokenClaims
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.RailPreference
import dev.gavenda.yuuka.repository.SyncRepository
import dev.gavenda.yuuka.ui.accounts.AccountsScreen
import dev.gavenda.yuuka.ui.budget.BudgetScreen
import dev.gavenda.yuuka.ui.categories.CategoriesScreen
import dev.gavenda.yuuka.ui.common.AppBarShell
import dev.gavenda.yuuka.ui.common.YuukaMood
import dev.gavenda.yuuka.ui.common.YuukaPortrait
import dev.gavenda.yuuka.ui.common.FullScreenDialogHost
import dev.gavenda.yuuka.ui.common.FullScreenDialogPane
import dev.gavenda.yuuka.ui.common.FullScreenDialogHostState
import dev.gavenda.yuuka.ui.common.LocalAppBarShell
import dev.gavenda.yuuka.ui.common.LocalFullScreenDialogHost
import dev.gavenda.yuuka.ui.common.FabEntry
import dev.gavenda.yuuka.ui.common.LocalRailFab
import dev.gavenda.yuuka.ui.common.LocalRailMonth
import dev.gavenda.yuuka.ui.common.MonthEntry
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.LocalSnackbarOverlays
import dev.gavenda.yuuka.ui.common.RailSlot
import dev.gavenda.yuuka.ui.dashboard.DashboardScreen
import dev.gavenda.yuuka.ui.accounttypes.AccountTypesScreen
import dev.gavenda.yuuka.ui.savethechange.SaveTheChangeScreen
import dev.gavenda.yuuka.ui.settings.SettingsScreen
import dev.gavenda.yuuka.ui.subscriptions.SubscriptionsScreen
import dev.gavenda.yuuka.ui.tags.TagsScreen
import dev.gavenda.yuuka.ui.theme.ShapeXl
import dev.gavenda.yuuka.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import dev.gavenda.yuuka.sync.Outbox

/**
 * The signed-in app shell. Navigation follows the window, as the web app's does:
 *
 * - On a phone (a Compact window width) Dashboard, Transactions and Accounts sit in a bottom navigation
 *   bar; Budget, Categories and Settings stay in the hamburger-triggered drawer, whose header carries a
 *   compact summary of the signed-in user instead of a dedicated Profile screen.
 * - On a wider window a navigation rail takes over both (see [YuukaNavRail]). It marks the current
 *   destination, so there is no top app bar, and it carries the screen's leading action as its FAB
 *   (`ScreenFab`) — including Save on Settings and Save the Change.
 * - Where the page beside the rail has room for two panes, Settings and Account types stand side by side
 *   (Material 3 Adaptive's [ListDetailSceneStrategy]); anywhere narrower one covers the other.
 *
 * The shell draws no app bar of its own: a screen knows its own title and its own actions, so each one
 * puts a `ScreenTopBar` (or a `DetailTopBar`) in its own Scaffold. What those bars share — the drawer
 * button, the hide-amounts switch, back, and whether a rail has taken the bar's place — is handed down
 * in [LocalAppBarShell].
 */
@OptIn(
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3AdaptiveApi::class,
)
@Composable
fun YuukaApp(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    // A history per top-level destination, each kept as it was left while another is on show. Back
    // walks the current one down to its root and then returns to Dashboard, the way out of the app.
    val navigationState = rememberNavigationState(startRoute = Route.Transactions, topLevelRoutes = TOP_LEVEL_ROUTES)
    val navigator = remember(navigationState) { Navigator(navigationState) }
    val currentRoute: NavKey? = navigationState.currentRoute

    val amountVisibility = koinInject<AmountVisibility>()
    val hidden by amountVisibility.hidden.collectAsStateWithLifecycle()

    val syncRepository = koinInject<SyncRepository>()
    val syncErrorMessage = stringResource(R.string.error_generic)
    var isSyncing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    val authManager = koinInject<AuthManager>()
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val claims = (authState as? AuthState.Authenticated)?.credentials?.idToken?.let(::decodeIdTokenClaims)

    val currentDestination = YuukaDestination.entries.firstOrNull { it.route == currentRoute }
    val brandName = stringResource(R.string.brand_name)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dialogHost = remember { FullScreenDialogHostState() }
    // Signing out discards the queue, so it asks first rather than taking unsent work with it on a slip.
    var confirmingSignOut by remember { mutableStateOf(false) }

    // How much of what is on screen the server has not been told about yet.
    // Writes are local-first, so a save never fails for want of a connection —
    // but a figure that has not reached the server is worth saying so about,
    // and the count is the queue's own rather than a guess.
    val outbox = koinInject<Outbox>()
    val unsentChanges by outbox.unsentCount.collectAsStateWithLifecycle(initialValue = 0)
    val isSending by outbox.syncing.collectAsStateWithLifecycle()

    // A change the server refused. Local-first means the user is told late —
    // when the batch lands, which may be a day later — but they are told, and
    // the refresh that follows takes the row back off the screen.
    LaunchedEffect(outbox) {
        outbox.rejections.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Navigation follows the window's width class rather than a width of our own. Compact, a phone held
    // upright, keeps the bottom bar and drawer; anything wider has room for a rail, and Expanded has room
    // for it to stay open beside the page instead of floating over it.
    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val sizeClass = windowAdaptiveInfo.windowSizeClass
    val useRail = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val railDocked = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    val fabSlot = remember { RailSlot<FabEntry>() }
    val monthSlot = remember { RailSlot<MonthEntry>() }
    val railPreference = koinInject<RailPreference>()
    // Only a window with room for it brings the rail back open: a floating one on launch would greet
    // a narrow window with a scrim.
    val railState = rememberWideNavigationRailState(
        initialValue = if (railDocked && railPreference.expanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed,
    )
    val setRailExpanded: (Boolean) -> Unit = { open ->
        railPreference.expanded = open
        scope.launch { if (open) railState.expand() else railState.collapse() }
    }
    // A window that narrows below the docked width must not keep an open rail floating over the page.
    LaunchedEffect(railDocked) {
        if (!railDocked) railState.snapTo(WideNavigationRailValue.Collapsed)
    }

    val entryProvider = entryProvider<NavKey> {
        entry<Route.Dashboard> { DashboardScreen() }
        entry<Route.Transactions>(metadata = tabOrder(Route.Transactions)) { TransactionsScreen() }
        entry<Route.Budget> { BudgetScreen() }
        entry<Route.Accounts>(metadata = tabOrder(Route.Accounts)) { AccountsScreen() }
        entry<Route.Categories> { CategoriesScreen() }
        entry<Route.Tags> { TagsScreen() }
        entry<Route.Subscriptions> { SubscriptionsScreen() }
        // Investments and the holding it opens are a list and its detail, as Settings and Account types are.
        entry<Route.Investments>(metadata = ListDetailSceneStrategy.listPane() + tabOrder(Route.Investments)) {
            InvestmentsScreen(onOpenHolding = { navigator.navigate(Route.Holding(it)) })
        }
        entry<Route.Holding>(metadata = ListDetailSceneStrategy.detailPane()) { HoldingScreen(it.symbol) }
        // Settings and the Account types it opens are a list and its detail: side by side where the page
        // has room for both (see TWO_PANE_MIN_WIDTH), one over the other where it has not.
        entry<Route.Settings>(metadata = ListDetailSceneStrategy.listPane()) {
            SettingsScreen(onOpenAccountTypes = { navigator.navigate(Route.AccountTypes) })
        }
        entry<Route.AccountTypes>(metadata = ListDetailSceneStrategy.detailPane()) { AccountTypesScreen() }
        entry<Route.SaveTheChange> { SaveTheChangeScreen() }
    }

    // The screens themselves, hosted either beside the rail or inside the phone's drawer.
    val screens: @Composable () -> Unit = {
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = {
                scope.launch {
                    isSyncing = true
                    val failure = try {
                        syncRepository.fullSync()
                        null
                    } catch (e: ApiError) {
                        e
                    } finally {
                        isSyncing = false
                    }
                    // After the spinner stops: showSnackbar suspends until the message is dismissed.
                    failure?.let { snackbarHostState.showSnackbar(it.message ?: syncErrorMessage) }
                }
            },
            state = pullToRefreshState,
            // Wraps the whole Scaffold rather than nesting inside a screen's body: a Scaffold always
            // draws its topBar after the body, so an indicator nested in the body would render behind
            // the screen's app bar whenever the two overlap.
            indicator = {
                // The cat print as the indicator: it slides down and fades in as the pull grows, then
                // turns while the sync runs.
                val turn by rememberInfiniteTransition(label = "refresh").animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
                    label = "turn",
                )
                val fraction = pullToRefreshState.distanceFraction
                if (fraction > 0f || isSyncing) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .graphicsLayer {
                                val shown = if (isSyncing) 1f else fraction.coerceIn(0f, 1f)
                                translationY = shown * 72.dp.toPx() - size.height
                                alpha = shown
                            },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shadowElevation = 6.dp,
                    ) {
                        Icon(
                            CatIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(10.dp)
                                .size(28.dp)
                                .graphicsLayer { rotationZ = if (isSyncing) turn else fraction * 180f },
                        )
                    }
                }
            },
        ) {
            Scaffold(
                // A dialog or sheet with a host of its own draws the message instead, or it would show twice.
                snackbarHost = { if (LocalSnackbarOverlays.current.none) SnackbarHost(snackbarHostState) },
                // Each screen brings its own app bar, which pads itself for the status bar; the shell
                // must not pad for it a second time. A rail window has no bar at all, so there the
                // shell is still what keeps the page out from under the status bar.
                contentWindowInsets = if (useRail) {
                    ScaffoldDefaults.contentWindowInsets
                } else {
                    ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                },
                bottomBar = {
                    if (!useRail && currentDestination != null) {
                        NavigationBar {
                            RAIL_DESTINATIONS.forEach { destination ->
                                NavigationBarItem(
                                    label = { Text(stringResource(destination.labelRes)) },
                                    icon = { Icon(painterResource(if (destination == currentDestination) destination.iconSelected else destination.icon), contentDescription = null) },
                                    selected = destination == currentDestination,
                                    onClick = { navigator.navigate(destination.route) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                // Two panes are judged by the page, not the window: an open rail docked beside it can leave
                // an Expanded window too little room for a second one. Until a detail is opened the list
                // stretches across the whole page, which leaves the scaffold's empty detail no width, and
                // opening one draws the list in to its own width: the scene stays the same throughout, so
                // the page is never slid away and back for what is only a second pane arriving.
                val detailOpen = currentRoute in DETAIL_ROUTES || currentRoute is Route.Holding
                // 0 is the list at its own width beside a detail, 1 the list across the whole page.
                val listSpread = remember { Animatable(if (detailOpen) 0f else 1f) }
                val spreadSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
                LaunchedEffect(detailOpen) { listSpread.animateTo(if (detailOpen) 0f else 1f, spreadSpec) }
                val paneExpansionState = rememberPaneExpansionState()
                // A form opened where the page has room stands beside the screen as Material's supporting
                // pane rather than covering it (FullScreenDialogPane); the screen gives up its width as the
                // pane comes in, and takes it back as the pane goes.
                val formPaneShare = remember { Animatable(0f) }
                val formPaneOpen = dialogHost.paneOpen
                LaunchedEffect(formPaneOpen) { formPaneShare.animateTo(if (formPaneOpen) 1f else 0f, spreadSpec) }
                BoxWithConstraints(modifier = Modifier.padding(padding)) {
                    val formPaneRoom = maxWidth >= FORM_PANE_MIN_WIDTH
                    SideEffect { dialogHost.paneRoom = formPaneRoom }
                    Row(modifier = Modifier.fillMaxSize()) {
                        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val besideWidth = with(LocalDensity.current) { LIST_PANE_WIDTH.roundToPx() }
                            val pageWidth = constraints.maxWidth
                            LaunchedEffect(paneExpansionState, besideWidth, pageWidth) {
                                snapshotFlow { listSpread.value }.collect { spread ->
                                    paneExpansionState.setFirstPaneWidth(lerp(besideWidth, pageWidth, spread))
                                }
                            }
                            val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(
                                // One back is one screen: the scaffold reads the same with or without a detail
                                // (the list's empty one is still a pane), so popping until it changes would leave
                                // Settings along with Account types.
                                backNavigationBehavior = BackNavigationBehavior.PopLatest,
                                directive = calculatePaneScaffoldDirective(windowAdaptiveInfo).copy(
                                    maxHorizontalPartitions = if (maxWidth >= TWO_PANE_MIN_WIDTH) 2 else 1,
                                    horizontalPartitionSpacerSize = PANE_SPACER,
                                    defaultPanePreferredWidth = LIST_PANE_WIDTH,
                                ),
                                paneExpansionState = paneExpansionState,
                            )
                            NavDisplay(
                                entries = navigationState.toEntries(entryProvider),
                                onBack = navigator::goBack,
                                sceneStrategies = listOf(listDetailStrategy),
                                transitionSpec = { tabSlide(fallback = 1) },
                                popTransitionSpec = { tabSlide(fallback = -1) },
                                // The predictive back preview follows the same swipe as a completed pop.
                                predictivePopTransitionSpec = { tabSlide(fallback = -1) },
                            )
                        }
                        FullScreenDialogPane(
                            state = dialogHost,
                            modifier = Modifier
                                .fillMaxHeight()
                                .clipToBounds()
                                // Measured at its own width throughout; only how much of it shows moves.
                                .layout { measurable, constraints ->
                                    val slot = FORM_PANE_SLOT.roundToPx()
                                    val placeable = measurable.measure(Constraints.fixed(slot, constraints.maxHeight))
                                    // The spatial spring overshoots past closed; a width cannot.
                                    val shown = (slot * formPaneShare.value).roundToInt().coerceIn(0, slot)
                                    layout(shown, placeable.height) { placeable.place(0, 0) }
                                }
                                .padding(start = FORM_PANE_GAP, top = FORM_PANE_MARGIN, end = FORM_PANE_MARGIN, bottom = FORM_PANE_MARGIN),
                        )
                    }
                }
            }
        }
    }

    // What every screen's own app bar shares. Remembered rather than rebuilt, so a screen's bar
    // recomposes when one of these actually moves and not on every pass through the shell.
    val appBarShell = remember(useRail, hidden, unsentChanges, isSending) {
        AppBarShell(
            useRail = useRail,
            amountsHidden = hidden,
            unsentChanges = unsentChanges,
            sending = isSending,
            onOpenNavigation = { scope.launch { drawerState.open() } },
            onBack = navigator::goBack,
            onToggleAmounts = amountVisibility::toggle,
        )
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalAppBarShell provides appBarShell,
        LocalFullScreenDialogHost provides dialogHost,
        // Only a rail has a place to put a screen's FAB; without one each screen draws its own.
        LocalRailFab provides if (useRail) fabSlot else null,
        // Where a month-scoped screen's bar puts its month once a rail has taken the bar's place.
        LocalRailMonth provides monthSlot,
    ) {
        if (useRail) {
            Row(modifier = modifier) {
                YuukaNavRail(
                    docked = railDocked,
                    state = railState,
                    currentRoute = currentRoute,
                    claims = claims,
                    amountsHidden = hidden,
                    fabSlot = fabSlot,
                    monthSlot = monthSlot,
                    onExpandedChange = setRailExpanded,
                    onNavigate = navigator::navigate,
                    onToggleAmounts = amountVisibility::toggle,
                    onSignOut = { confirmingSignOut = true },
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // The rail already pads for the system bars along its edge; the page beside it must not do so twice.
                        .consumeWindowInsets(WideNavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)),
                ) {
                    screens()
                }
            }
        } else {
            ModalNavigationDrawer(
                modifier = modifier,
                drawerState = drawerState,
                gesturesEnabled = currentDestination != null,
                drawerContent = {
                    ModalDrawerSheet {
                        // A strip of the rail's print at the top, reaching up behind the status bar and
                        // thinning out towards the sheet's edge. No mark and no name: the rail keeps those.
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(DRAWER_PRINT_HEIGHT)
                                .catPattern(CatPrint.Small, CatFade.Trailing) {
                                    Rect(0f, -DRAWER_PRINT_RISE.toPx(), size.width, size.height)
                                },
                        )
                        // The same card the open rail puts the signed-in account in, so the two
                        // shells introduce the user the same way — here it spans the sheet, and
                        // lines up with the destinations below it.
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            onClick = {},
                            shape = ShapeXl,
                        ) {
                            AccountSummary(
                                claims = claims,
                                fallbackName = brandName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .catPattern(CatPrint.Small, CatFade.None)
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            NAV_GROUPS.forEach { group ->
                                Text(
                                    stringResource(group.labelRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
                                )
                                group.items.forEach { item ->
                                    val selected = item.isCurrent(currentRoute)
                                    NavigationDrawerItem(
                                        label = { Text(stringResource(item.labelRes)) },
                                        icon = { Icon(painterResource(if (selected) item.iconSelected else item.icon), contentDescription = null) },
                                        selected = selected,
                                        onClick = {
                                            navigator.navigate(item.route)
                                            scope.launch { drawerState.close() }
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                            }
                        }
                        // Settings and Sign out are about the app rather than the money, so they take no heading.
                        DetailDrawerItem(DetailDestination.SETTINGS, currentRoute) {
                            navigator.navigate(it)
                            scope.launch { drawerState.close() }
                        }
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.action_sign_out)) },
                            icon = { Icon(painterResource(R.drawable.logout_48px), contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                confirmingSignOut = true
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                },
            ) {
                screens()
            }
        }
        // Without a rail there is no page for a pane to stand beside.
        if (!useRail) SideEffect { dialogHost.paneRoom = false }
        FullScreenDialogHost(dialogHost)

        if (confirmingSignOut) {
            // Only a sign-out that loses work is a destructive one, so only that takes the error colour.
            val losesWork = unsentChanges > 0
            AlertDialog(
                onDismissRequest = { confirmingSignOut = false },
                icon = { YuukaPortrait(YuukaMood.Uneasy, width = 144.dp) },
                title = { Text(stringResource(R.string.sign_out_confirm_title)) },
                text = {
                    Text(
                        if (losesWork) {
                            pluralStringResource(R.plurals.sign_out_unsent_body, unsentChanges, unsentChanges)
                        } else {
                            stringResource(R.string.sign_out_body)
                        },
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { confirmingSignOut = false; onSignOut() },
                        colors = if (losesWork) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                        } else {
                            ButtonDefaults.buttonColors()
                        },
                    ) { Text(stringResource(R.string.action_yes_sign_out)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingSignOut = false }) { Text(stringResource(R.string.action_no_stay_signed_in)) }
                },
            )
        }
    }
}

/** The list pane's width beside its detail, and the gap between them: Material's defaults for an Expanded window. */
private val LIST_PANE_WIDTH = 360.dp
private val PANE_SPACER = 24.dp

/** Room for a list pane and a detail pane at least as wide as it, with the gap between them. */
private val TWO_PANE_MIN_WIDTH = LIST_PANE_WIDTH * 2 + PANE_SPACER

/** A form's supporting pane: Material's width for one, the gap from the page beside it, and its own margin. */
private val FORM_PANE_WIDTH = 412.dp
private val FORM_PANE_GAP = 8.dp
private val FORM_PANE_MARGIN = 16.dp
private val FORM_PANE_SLOT = FORM_PANE_GAP + FORM_PANE_WIDTH + FORM_PANE_MARGIN

/** Room for a form's pane with the screen beside it still a list's width. */
private val FORM_PANE_MIN_WIDTH = LIST_PANE_WIDTH + FORM_PANE_SLOT

/** The destinations entered as a `detailPane()`: only with one of these on top does its list make room for it. */
private val DETAIL_ROUTES: Set<NavKey> = setOf(Route.AccountTypes)

/** Where a destination sits in the bottom bar, so a switch between two tabs slides the way the bar reads. */
private data object TabOrder : NavMetadataKey<Int>

private fun tabOrder(route: Route): Map<String, Any> =
    metadata { put(TabOrder, RAIL_DESTINATIONS.indexOfFirst { it.route == route }) }

/**
 * +1 (slide from the right) or -1 (slide from the left) based on the two screens' order among
 * the bottom-nav tabs, or null when either side isn't a tab. Leaving Accounts for Transactions by
 * back is a pop and by the bar is a switch, but both slide the way the bar reads.
 */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.tabSlideDirection(): Int? {
    val fromIndex = initialState.metadata[TabOrder] ?: return null
    val toIndex = targetState.metadata[TabOrder] ?: return null
    return if (toIndex >= fromIndex) 1 else -1
}

/** A destination slides in from the side it lies on in the bottom bar; anything else from [fallback]'s. */
private fun AnimatedContentTransitionScope<Scene<NavKey>>.tabSlide(fallback: Int): ContentTransform {
    val direction = tabSlideDirection() ?: fallback
    return slideInHorizontally { fullWidth -> direction * fullWidth } togetherWith
        slideOutHorizontally { fullWidth -> -direction * fullWidth }
}

@Composable
private fun DetailDrawerItem(destination: DetailDestination, currentRoute: NavKey?, onNavigate: (Route) -> Unit) {
    val selected = destination.isCurrent(currentRoute)
    NavigationDrawerItem(
        label = { Text(stringResource(destination.labelRes)) },
        icon = { Icon(painterResource(if (selected) destination.iconSelected else destination.icon), contentDescription = null) },
        selected = selected,
        onClick = { onNavigate(destination.route) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** How far the drawer header's print reaches up, so it runs behind the status bar as the rail's does. */
private val DRAWER_PRINT_RISE = 64.dp
private val DRAWER_PRINT_HEIGHT = 56.dp
