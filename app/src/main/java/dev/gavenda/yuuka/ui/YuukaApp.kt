package dev.gavenda.yuuka.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.gavenda.yuuka.auth.AuthManager
import dev.gavenda.yuuka.auth.AuthState
import dev.gavenda.yuuka.auth.decodeIdTokenClaims
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.ui.accounts.AccountsScreen
import dev.gavenda.yuuka.ui.budget.BudgetScreen
import dev.gavenda.yuuka.ui.categories.CategoriesScreen
import dev.gavenda.yuuka.ui.common.UserAvatar
import dev.gavenda.yuuka.ui.dashboard.DashboardScreen
import dev.gavenda.yuuka.ui.settings.SettingsScreen
import dev.gavenda.yuuka.ui.transactions.TransactionsScreen
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * The signed-in app shell: Dashboard, Transactions and Accounts sit in a bottom navigation bar;
 * Budget, Categories and Settings stay in the hamburger-triggered drawer, whose header carries
 * a compact summary of the signed-in user instead of a dedicated Profile screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YuukaApp(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val amountVisibility = koinInject<AmountVisibility>()
    val hidden by amountVisibility.hidden.collectAsStateWithLifecycle()

    val ledgerRepository = koinInject<LedgerRepository>()
    var isSyncing by remember { mutableStateOf(false) }

    var settingsSaveEnabled by remember { mutableStateOf(false) }
    var settingsSaving by remember { mutableStateOf(false) }
    var settingsSaveAction by remember { mutableStateOf({}) }

    val authManager = koinInject<AuthManager>()
    val authState by authManager.authState.collectAsStateWithLifecycle()
    val claims = (authState as? AuthState.Authenticated)?.credentials?.idToken?.let(::decodeIdTokenClaims)

    val currentDestination = YuukaDestination.entries.firstOrNull { it.route == currentRoute }
    val title = currentDestination?.label ?: if (currentRoute == SETTINGS_ROUTE) "Settings" else "yuuka"

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val bottomNavDestinations = listOf(
        YuukaDestination.DASHBOARD,
        YuukaDestination.TRANSACTIONS,
        YuukaDestination.ACCOUNTS,
    )
    val bottomNavRoutes = bottomNavDestinations.map { it.route }
    val drawerDestinations = YuukaDestination.entries - bottomNavDestinations.toSet()

    // Shared by the bottom nav items and any programmatic jump to a top-level destination (e.g.
    // "View all" from the dashboard), so a single-level back stack is preserved either way.
    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

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
                            claims?.name ?: claims?.email ?: "yuuka",
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
                            label = { Text(destination.label) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            selected = destination == currentDestination,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    selected = currentRoute == SETTINGS_ROUTE,
                    onClick = {
                        navController.navigate(SETTINGS_ROUTE)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
                NavigationDrawerItem(
                    label = { Text("Sign out") },
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (currentDestination != null) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        } else if (currentRoute == SETTINGS_ROUTE) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (currentDestination != null) {
                            IconButton(
                                onClick = {
                                    if (!isSyncing) {
                                        scope.launch {
                                            isSyncing = true
                                            runCatching { ledgerRepository.refreshAll() }
                                            isSyncing = false
                                        }
                                    }
                                },
                                enabled = !isSyncing,
                            ) {
                                if (isSyncing) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Sync, contentDescription = "Sync")
                                }
                            }
                            IconButton(onClick = amountVisibility::toggle) {
                                Icon(
                                    if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = if (hidden) "Show amounts" else "Hide amounts",
                                )
                            }
                        } else if (currentRoute == SETTINGS_ROUTE) {
                            IconButton(onClick = settingsSaveAction, enabled = settingsSaveEnabled) {
                                if (settingsSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Save, contentDescription = "Save")
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
                                label = { Text(destination.label) },
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
                            settingsSaveEnabled = enabled
                            settingsSaving = saving
                            settingsSaveAction = save
                        },
                    )
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
