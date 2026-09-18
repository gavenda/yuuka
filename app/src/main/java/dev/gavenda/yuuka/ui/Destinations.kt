package dev.gavenda.yuuka.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gavenda.yuuka.R

/** The five tabs the web app's bottom nav and top links carry — `App.vue`'s `links`. */
enum class YuukaDestination(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    DASHBOARD("dashboard", R.string.destination_dashboard, Icons.Filled.Dashboard),
    TRANSACTIONS("transactions", R.string.destination_transactions, Icons.Filled.Receipt),
    BUDGET("budget", R.string.destination_budget, Icons.Filled.PieChart),
    ACCOUNTS("accounts", R.string.destination_accounts, Icons.Filled.AccountBalanceWallet),
    CATEGORIES("categories", R.string.destination_categories, Icons.Filled.Category),
}

/** Reached from the navigation drawer rather than a tab, same as the web app's settings link. */
const val SETTINGS_ROUTE = "settings"
