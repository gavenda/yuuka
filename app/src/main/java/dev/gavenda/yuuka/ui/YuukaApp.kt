package dev.gavenda.yuuka.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import dev.gavenda.yuuka.ui.common.FabEntry
import dev.gavenda.yuuka.ui.common.LocalRailFabHost
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.MutationLoadingIndicator
import dev.gavenda.yuuka.ui.common.RailFabHost
import dev.gavenda.yuuka.ui.common.RegisterRailFab
import dev.gavenda.yuuka.ui.dashboard.DashboardScreen
import dev.gavenda.yuuka.ui.savethechange.SaveTheChangeScreen
import dev.gavenda.yuuka.ui.settings.SettingsScreen
import dev.gavenda.yuuka.ui.subscriptions.SubscriptionsScreen
import dev.gavenda.yuuka.ui.tags.TagsScreen
import dev.gavenda.yuuka.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.pluralStringResource
import dev.gavenda.yuuka.sync.Outbox

/**
 * The signed-in app shell. Navigation follows the window, as the web app's does:
 *
 * - On a phone (a Compact window width) Dashboard, Transactions and Accounts sit in a bottom navigation
 *   bar; Budget, Categories and Settings stay in the hamburger-triggered drawer, whose header carries a
 *   compact summary of the signed-in user instead of a dedicated Profile screen.
 * - On a wider window a navigation rail takes over both (see [YuukaNavRail]). It marks the current
 *   destination, so there is no top app bar, and it carries the screen's leading action as its FAB
 *   ([ScreenFab]) — including Save on Settings and Save the Change.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun YuukaApp(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val amountVisibility = koinInject<AmountVisibility>()
    val hidden by amountVisibility.hidden.collectAsStateWithLifecycle()

    val syncRepository = koinInject<SyncRepository>()
    val syncErrorMessage = stringResource(R.string.error_generic)
    var isSyncing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // Lifted by whichever drawer-only detail screen (Settings, Save the Change) is
    // currently shown, so its Save action can live in the shared top app bar. At
    // most one such screen is ever the current route, so one set of vars suffices.
    var detailSaveEnabled by remember { mutableStateOf(false) }
    var detailSaving by remember { mutableStateOf(false) }
    var detailSaveAction by remember { mutableStateOf({}) }

    val authManager = koinInject<AuthManager>()
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val claims = (authState as? AuthState.Authenticated)?.credentials?.idToken?.let(::decodeIdTokenClaims)

    val currentDestination = YuukaDestination.entries.firstOrNull { it.route == currentRoute }
    val brandName = stringResource(R.string.brand_name)
    val settingsLabel = stringResource(R.string.destination_settings)
    val saveTheChangeLabel = stringResource(R.string.destination_save_the_change)
    val drawerDetailRoutes = setOf(SETTINGS_ROUTE, SAVE_THE_CHANGE_ROUTE)
    val title = currentDestination?.let { stringResource(it.labelRes) }
        ?: when (currentRoute) {
            SETTINGS_ROUTE -> settingsLabel
            SAVE_THE_CHANGE_ROUTE -> saveTheChangeLabel
            else -> brandName
        }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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
    val activity = LocalActivity.current
    val widthClass = activity?.let { calculateWindowSizeClass(it).widthSizeClass }
    val useRail = widthClass != null && widthClass != WindowWidthSizeClass.Compact
    val railDocked = widthClass == WindowWidthSizeClass.Expanded

    val fabHost = remember { RailFabHost() }
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

    val bottomNavDestinations = listOf(
        YuukaDestination.DASHBOARD,
        YuukaDestination.TRANSACTIONS,
        YuukaDestination.ACCOUNTS,
    )
    val bottomNavRoutes = bottomNavDestinations.map { it.route }
    val drawerDestinations = YuukaDestination.entries - bottomNavDestinations.toSet()

    // Shared by the bottom nav items, drawer destinations, the rail and any programmatic jump to a
    // top-level destination (e.g. "View all" from the dashboard). This pushes onto the real
    // back stack instead of collapsing it back to the start destination, so visiting tabs in
    // order (Dashboard -> Transactions -> Accounts) leaves a genuine [Dashboard, Transactions,
    // Accounts] stack and back/predictive-back steps through them in that order rather than
    // jumping straight to Dashboard. popUpTo(route, inclusive = true) still collapses a
    // *revisited* destination's old position so repeated taps don't pile up duplicate entries.
    // Tapping the destination already on screen is a no-op: the pop-and-repush above would
    // otherwise replay the enter/exit transition on the very screen being shown.
    val navigateToTopLevel: (String) -> Unit = { route ->
        if (route != currentRoute) {
            navController.navigate(route) {
                launchSingleTop = true
                popUpTo(route) { inclusive = true }
            }
        }
    }

    // Settings and Save the Change are pushed on top of whatever tab was showing, so they
    // just need the same guard against re-navigating to the screen already shown.
    val navigateToDetail: (String) -> Unit = { route ->
        if (route != currentRoute) navController.navigate(route)
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
            // Wraps the whole Scaffold (app bar included) rather than nesting inside its body:
            // Scaffold always draws its topBar after the body, so an indicator nested in the body
            // would render behind the app bar's opaque surface whenever the two overlap.
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = isSyncing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    // The rail marks the current destination, holds the hide-amounts switch and carries the
                    // Save action, so a wide window has no bar at all.
                    if (!useRail) {
                        TopAppBar(
                            title = { Text(title) },
                            navigationIcon = {
                                if (currentDestination != null) {
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu))
                                    }
                                } else if (currentRoute in drawerDetailRoutes) {
                                    IconButton(onClick = { navController.popBackStack() }) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                                    }
                                }
                            },
                            actions = {
                                if (currentDestination != null) {
                                    IconButton(onClick = amountVisibility::toggle) {
                                        Icon(
                                            if (hidden) Icons.Filled.MoneyOff else Icons.Filled.AttachMoney,
                                            contentDescription = stringResource(if (hidden) R.string.cd_show_amounts else R.string.cd_hide_amounts),
                                        )
                                    }
                                } else if (currentRoute in drawerDetailRoutes) {
                                    IconButton(onClick = detailSaveAction, enabled = detailSaveEnabled) {
                                        if (detailSaving) {
                                            MutationLoadingIndicator(size = 24.dp)
                                        } else {
                                            Icon(Icons.Filled.Save, contentDescription = stringResource(R.string.action_save))
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
                bottomBar = {
                    if (!useRail && currentDestination != null) {
                        NavigationBar {
                            bottomNavDestinations.forEach { destination ->
                                NavigationBarItem(
                                    label = { Text(stringResource(destination.labelRes)) },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    selected = destination == currentDestination,
                                    onClick = { navigateToTopLevel(destination.route) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Column(modifier = Modifier.padding(padding)) {
                    UnsentChangesBanner(count = unsentChanges, sending = isSending)

                NavHost(
                    navController = navController,
                    startDestination = YuukaDestination.DASHBOARD.route,
                    enterTransition = {
                        val direction = tabSlideDirection(bottomNavRoutes) ?: 1
                        slideInHorizontally { fullWidth -> direction * fullWidth }
                    },
                    exitTransition = {
                        val direction = tabSlideDirection(bottomNavRoutes) ?: 1
                        slideOutHorizontally { fullWidth -> -direction * fullWidth }
                    },
                    popEnterTransition = {
                        val direction = tabSlideDirection(bottomNavRoutes) ?: -1
                        slideInHorizontally { fullWidth -> direction * fullWidth }
                    },
                    popExitTransition = {
                        val direction = tabSlideDirection(bottomNavRoutes) ?: -1
                        slideOutHorizontally { fullWidth -> -direction * fullWidth }
                    },
                    // Without these, the Android 16 predictive back gesture preview falls back to
                    // NavHost's default fade/scale instead of following the same swipe as a completed pop.
                    predictivePopEnterTransition = {
                        val direction = tabSlideDirection(bottomNavRoutes) ?: -1
                        slideInHorizontally { fullWidth -> direction * fullWidth }
                    },
                    predictivePopExitTransition = {
                        val direction = tabSlideDirection(bottomNavRoutes) ?: -1
                        slideOutHorizontally { fullWidth -> -direction * fullWidth }
                    },
                ) {
                    composable(YuukaDestination.DASHBOARD.route) {
                        DashboardScreen(onViewAllTransactions = { navigateToTopLevel(YuukaDestination.TRANSACTIONS.route) })
                    }
                    composable(YuukaDestination.TRANSACTIONS.route) { TransactionsScreen() }
                    composable(YuukaDestination.BUDGET.route) { BudgetScreen() }
                    composable(YuukaDestination.ACCOUNTS.route) { AccountsScreen() }
                    composable(YuukaDestination.CATEGORIES.route) { CategoriesScreen() }
                    composable(YuukaDestination.TAGS.route) { TagsScreen() }
                    composable(YuukaDestination.SUBSCRIPTIONS.route) { SubscriptionsScreen() }
                    composable(SETTINGS_ROUTE) {
                        SettingsScreen(
                            onSaveStateChange = { enabled, saving, save ->
                                detailSaveEnabled = enabled
                                detailSaving = saving
                                detailSaveAction = save
                            },
                        )
                    }
                    composable(SAVE_THE_CHANGE_ROUTE) {
                        SaveTheChangeScreen(
                            onSaveStateChange = { enabled, saving, save ->
                                detailSaveEnabled = enabled
                                detailSaving = saving
                                detailSaveAction = save
                            },
                        )
                    }
                }
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        // Only a rail has a place to put a screen's FAB; without one each screen draws its own.
        LocalRailFabHost provides if (useRail) fabHost else null,
    ) {
        if (useRail) {
            // Settings and Save the Change save from their leading action too, as the web app's do.
            if (currentRoute in drawerDetailRoutes) {
                RegisterRailFab(
                    FabEntry(
                        label = stringResource(R.string.action_save),
                        icon = Icons.Filled.Save,
                        onClick = detailSaveAction,
                        enabled = detailSaveEnabled,
                        busy = detailSaving,
                    ),
                )
            }
            Row(modifier = modifier) {
                YuukaNavRail(
                    docked = railDocked,
                    state = railState,
                    currentRoute = currentRoute,
                    claims = claims,
                    amountsHidden = hidden,
                    fabHost = fabHost,
                    onExpandedChange = setRailExpanded,
                    onNavigate = navigateToTopLevel,
                    onNavigateDetail = navigateToDetail,
                    onToggleAmounts = amountVisibility::toggle,
                    onSignOut = onSignOut,
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
                        AccountSummary(
                            claims = claims,
                            fallbackName = brandName,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                        HorizontalDivider()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            drawerDestinations.forEach { destination ->
                                NavigationDrawerItem(
                                    label = { Text(stringResource(destination.labelRes)) },
                                    icon = { Icon(destination.icon, contentDescription = null) },
                                    selected = destination == currentDestination,
                                    onClick = {
                                        navigateToTopLevel(destination.route)
                                        scope.launch { drawerState.close() }
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                )
                            }
                            // Grouped with Budget/Categories rather than Settings below the
                            // divider — it's a ledger concern, not app configuration.
                            NavigationDrawerItem(
                                label = { Text(saveTheChangeLabel) },
                                icon = { Icon(Icons.Filled.Savings, contentDescription = null) },
                                selected = currentRoute == SAVE_THE_CHANGE_ROUTE,
                                onClick = {
                                    navigateToDetail(SAVE_THE_CHANGE_ROUTE)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }
                        HorizontalDivider()
                        NavigationDrawerItem(
                            label = { Text(settingsLabel) },
                            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            selected = currentRoute == SETTINGS_ROUTE,
                            onClick = {
                                navigateToDetail(SETTINGS_ROUTE)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                        NavigationDrawerItem(
                            label = { Text(stringResource(R.string.action_sign_out)) },
                            icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                            selected = false,
                            onClick = {
                                scope.launch { drawerState.close() }
                                onSignOut()
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                },
            ) {
                screens()
            }
        }
    }
}

/**
 * +1 (slide from the right) or -1 (slide from the left) based on the two routes' order among
 * the bottom-nav tabs, or null when either side isn't a tab. Compose Navigation's `popUpTo` +
 * `restoreState` dance around the bottom nav means the same tab switch is sometimes classified
 * as a push and sometimes as a pop, which flips the direction if enter/exit and popEnter/popExit
 * each pick their own fixed direction. Basing it on tab order instead keeps it consistent regardless
 * of which pair of callbacks Navigation happens to fire.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlideDirection(bottomNavRoutes: List<String>): Int? {
    val fromIndex = bottomNavRoutes.indexOf(initialState.destination.route)
    val toIndex = bottomNavRoutes.indexOf(targetState.destination.route)
    if (fromIndex == -1 || toIndex == -1) return null
    return if (toIndex >= fromIndex) 1 else -1
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
