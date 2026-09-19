package dev.gavenda.yuuka.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import dev.gavenda.yuuka.R

/**
 * The web app's bottom nav and top links — `App.vue`'s `links` — plus Subscriptions, which the web app
 * keeps under the avatar menu and this app keeps in the navigation drawer. Only some of these are
 * bottom-nav tabs; `YuukaApp` puts the rest in the drawer. A wide window's rail lists the same three as
 * the bottom bar ([RAIL_DESTINATIONS]) and keeps the rest under "More" ([RAIL_MORE_DESTINATIONS]).
 */
enum class YuukaDestination(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    DASHBOARD("dashboard", R.string.destination_dashboard, Icons.Filled.Dashboard),
    TRANSACTIONS("transactions", R.string.destination_transactions, Icons.Filled.Receipt),
    BUDGET("budget", R.string.destination_budget, Icons.Filled.PieChart),
    ACCOUNTS("accounts", R.string.destination_accounts, Icons.Filled.AccountBalanceWallet),
    CATEGORIES("categories", R.string.destination_categories, Icons.Filled.Category),
    TAGS("tags", R.string.destination_tags, Icons.Filled.Sell),
    SUBSCRIPTIONS("subscriptions", R.string.destination_subscriptions, Icons.Filled.EventRepeat),
}

/** The rail's row of daily destinations: the ones the phone's bottom bar holds. */
val RAIL_DESTINATIONS = listOf(YuukaDestination.DASHBOARD, YuukaDestination.TRANSACTIONS, YuukaDestination.ACCOUNTS)

/** Everything else, opened from the rail's "More" group, in the order they were listed before leaving the row. */
val RAIL_MORE_DESTINATIONS = YuukaDestination.entries - RAIL_DESTINATIONS.toSet()

/** Reached from the navigation drawer rather than a tab, same as the web app's settings link. */
const val SETTINGS_ROUTE = "settings"

/** Reached from the navigation drawer, grouped with the ledger destinations rather than [SETTINGS_ROUTE] — not a bottom-nav tab. */
const val SAVE_THE_CHANGE_ROUTE = "save-the-change"
