package dev.gavenda.yuuka.ui

import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import dev.gavenda.yuuka.R

/**
 * The web app's bottom nav and top links — `App.vue`'s `links` — plus Subscriptions, which the web app
 * keeps under the avatar menu and this app keeps in the navigation drawer. Only some of these are
 * bottom-nav tabs; `YuukaApp` puts the rest in the drawer. A wide window's rail lists the same three as
 * the bottom bar ([RAIL_DESTINATIONS]) and keeps the rest under "More" ([RAIL_MORE_DESTINATIONS]).
 */
enum class YuukaDestination(val route: String, @StringRes val labelRes: Int, @DrawableRes val icon: Int, @DrawableRes val iconSelected: Int) {
    DASHBOARD("dashboard", R.string.destination_dashboard, R.drawable.dashboard_48px, R.drawable.dashboard_fill_48px),
    TRANSACTIONS("transactions", R.string.destination_transactions, R.drawable.receipt_48px, R.drawable.receipt_fill_48px),
    BUDGET("budget", R.string.destination_budget, R.drawable.pie_chart_48px, R.drawable.pie_chart_fill_48px),
    ACCOUNTS("accounts", R.string.destination_accounts, R.drawable.account_balance_wallet_48px, R.drawable.account_balance_wallet_fill_48px),
    CATEGORIES("categories", R.string.destination_categories, R.drawable.category_48px, R.drawable.category_fill_48px),
    TAGS("tags", R.string.destination_tags, R.drawable.sell_48px, R.drawable.sell_fill_48px),
    SUBSCRIPTIONS("subscriptions", R.string.destination_subscriptions, R.drawable.event_repeat_48px, R.drawable.event_repeat_fill_48px),
}

/** The rail's row of daily destinations: the ones the phone's bottom bar holds. */
val RAIL_DESTINATIONS = listOf(YuukaDestination.DASHBOARD, YuukaDestination.TRANSACTIONS, YuukaDestination.ACCOUNTS)

/** Everything else, opened from the rail's "More" group, in the order they were listed before leaving the row. */
val RAIL_MORE_DESTINATIONS = YuukaDestination.entries - RAIL_DESTINATIONS.toSet()

/** Reached from the navigation drawer rather than a tab, same as the web app's settings link. */
const val SETTINGS_ROUTE = "settings"

/** Opened from a row on the Settings screen, which stays the marked destination while it is on show. */
const val ACCOUNT_TYPES_ROUTE = "settings/account-types"

/** Settings itself, or a screen opened from it. */
fun isSettingsRoute(route: String?): Boolean = route == SETTINGS_ROUTE || route == ACCOUNT_TYPES_ROUTE

/** Reached from the navigation drawer, grouped with the ledger destinations rather than [SETTINGS_ROUTE] — not a bottom-nav tab. */
const val SAVE_THE_CHANGE_ROUTE = "save-the-change"

/**
 * What is set up once and left alone, listed after the ledger screens in the drawer and the rail's "More"
 * group. They open as detail screens over the destination on show rather than replacing it.
 */
enum class DetailDestination(val route: String, @StringRes val labelRes: Int, @DrawableRes val icon: Int, @DrawableRes val iconSelected: Int) {
    SAVE_THE_CHANGE(SAVE_THE_CHANGE_ROUTE, R.string.destination_save_the_change, R.drawable.savings_48px, R.drawable.savings_fill_48px),
    SETTINGS(SETTINGS_ROUTE, R.string.destination_settings, R.drawable.settings_48px, R.drawable.settings_fill_48px);

    /** Settings stays marked while a screen opened from it is on show. */
    fun isCurrent(currentRoute: String?): Boolean = if (this == SETTINGS) isSettingsRoute(currentRoute) else currentRoute == route
}

/** Screens that show no amount, so the hide-amounts switch has nothing to mask there. */
val AMOUNT_FREE_ROUTES = setOf(
    YuukaDestination.CATEGORIES.route,
    YuukaDestination.TAGS.route,
    SETTINGS_ROUTE,
    ACCOUNT_TYPES_ROUTE,
    SAVE_THE_CHANGE_ROUTE,
)
