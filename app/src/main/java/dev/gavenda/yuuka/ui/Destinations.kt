package dev.gavenda.yuuka.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.ui.graphics.vector.ImageVector

/** The five tabs the web app's bottom nav and top links carry — `App.vue`'s `links`. */
enum class YuukaDestination(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Dashboard", Icons.Filled.Dashboard),
    TRANSACTIONS("transactions", "Transactions", Icons.Filled.Receipt),
    BUDGET("budget", "Budget", Icons.Filled.PieChart),
    ACCOUNTS("accounts", "Accounts", Icons.Filled.AccountBalanceWallet),
    CATEGORIES("categories", "Categories", Icons.Filled.Category),
}

/** Reached from the navigation drawer rather than a tab, same as the web app's settings link. */
const val SETTINGS_ROUTE = "settings"
