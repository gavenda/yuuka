package dev.gavenda.yuuka.ui

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import dev.gavenda.yuuka.repository.SyncRepository
import dev.gavenda.yuuka.ui.accounts.AccountsScreen
import dev.gavenda.yuuka.ui.budget.BudgetScreen
import dev.gavenda.yuuka.ui.categories.CategoriesScreen
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.MutationLoadingIndicator
import dev.gavenda.yuuka.ui.common.UserAvatar
import dev.gavenda.yuuka.ui.dashboard.DashboardScreen
import dev.gavenda.yuuka.ui.savethechange.SaveTheChangeScreen
import dev.gavenda.yuuka.ui.settings.SettingsScreen
import dev.gavenda.yuuka.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The signed-in app shell: Dashboard, Transactions and Accounts sit in a bottom navigation bar;
 * Budget, Categories and Settings stay in the hamburger-triggered drawer, whose header carries
 * a compact summary of the signed-in user instead of a dedicated Profile screen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val bottomNavDestinations = listOf(
        YuukaDestination.DASHBOARD,
        YuukaDestination.TRANSACTIONS,
        YuukaDestination.ACCOUNTS,
    )
    val bottomNavRoutes = bottomNavDestinations.map { it.route }
    val drawerDestinations = YuukaDestination.entries - bottomNavDestinations.toSet()

    // Shared by the bottom nav items, drawer destinations and any programmatic jump to a
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

    CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
        ModalNavigationDrawer(
            modifier = modifier,
            drawerState = drawerState,
            gesturesEnabled = currentDestination != null,
            drawerContent = {
                ModalDrawerSheet {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        UserAvatar(name = claims?.name ?: claims?.email, pictureUrl = claims?.picture, size = 40)
                        Column {
                            Text(
                                claims?.name ?: claims?.email ?: brandName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            // Only a second line when the name isn't already standing in for the email above.
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
                    },
                    bottomBar = {
                        if (currentDestination != null) {
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
                    NavHost(
                        navController = navController,
                        startDestination = YuukaDestination.DASHBOARD.route,
                        modifier = Modifier.padding(padding),
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
