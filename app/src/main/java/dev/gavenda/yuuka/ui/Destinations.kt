package dev.gavenda.yuuka.ui

import androidx.annotation.StringRes
import androidx.annotation.DrawableRes
import androidx.navigation3.runtime.NavKey
import dev.gavenda.yuuka.R
import kotlinx.serialization.Serializable

/**
 * Every screen the shell can show, as the key Navigation 3 keeps on the back stack. Serializable, so
 * the back stack survives the process being killed.
 */
@Serializable
sealed interface Route : NavKey {
    @Serializable data object Dashboard : Route
    @Serializable data object Transactions : Route
    @Serializable data object Budget : Route
    @Serializable data object Accounts : Route
    @Serializable data object Categories : Route
    @Serializable data object Tags : Route
    @Serializable data object Subscriptions : Route

    /** Reached from the navigation drawer rather than a tab, same as the web app's settings link. */
    @Serializable data object Settings : Route

    /** Opened from a row on the Settings screen, which stays the marked destination while it is on show. */
    @Serializable data object AccountTypes : Route

    /** Reached from the navigation drawer, grouped with the ledger destinations rather than [Settings] — not a bottom-nav tab. */
    @Serializable data object SaveTheChange : Route
}

/**
 * The web app's bottom nav and top links — `App.vue`'s `links` — plus Subscriptions, which the web app
 * keeps under the avatar menu and this app keeps in the navigation drawer. Only some of these are
 * bottom-nav tabs; `YuukaApp` puts the rest in the drawer. A wide window's rail lists the same three as
 * the bottom bar ([RAIL_DESTINATIONS]) and keeps the rest under "More" ([RAIL_MORE_DESTINATIONS]).
 */
enum class YuukaDestination(val route: Route, @StringRes val labelRes: Int, @DrawableRes val icon: Int, @DrawableRes val iconSelected: Int) {
    DASHBOARD(Route.Dashboard, R.string.destination_dashboard, R.drawable.dashboard_48px, R.drawable.dashboard_fill_48px),
    TRANSACTIONS(Route.Transactions, R.string.destination_transactions, R.drawable.receipt_48px, R.drawable.receipt_fill_48px),
    BUDGET(Route.Budget, R.string.destination_budget, R.drawable.pie_chart_48px, R.drawable.pie_chart_fill_48px),
    ACCOUNTS(Route.Accounts, R.string.destination_accounts, R.drawable.account_balance_wallet_48px, R.drawable.account_balance_wallet_fill_48px),
    CATEGORIES(Route.Categories, R.string.destination_categories, R.drawable.category_48px, R.drawable.category_fill_48px),
    TAGS(Route.Tags, R.string.destination_tags, R.drawable.sell_48px, R.drawable.sell_fill_48px),
    SUBSCRIPTIONS(Route.Subscriptions, R.string.destination_subscriptions, R.drawable.event_repeat_48px, R.drawable.event_repeat_fill_48px),
}

/** The rail's row of daily destinations: the ones the phone's bottom bar holds. */
val RAIL_DESTINATIONS = listOf(YuukaDestination.DASHBOARD, YuukaDestination.TRANSACTIONS, YuukaDestination.ACCOUNTS)

/** Everything else, opened from the rail's "More" group, in the order they were listed before leaving the row. */
val RAIL_MORE_DESTINATIONS = YuukaDestination.entries - RAIL_DESTINATIONS.toSet()

/** Settings itself, or a screen opened from it. */
fun isSettingsRoute(route: NavKey?): Boolean = route == Route.Settings || route == Route.AccountTypes

/**
 * What is set up once and left alone, listed after the ledger screens in the drawer and the rail's "More"
 * group. Each keeps a history of its own, as the ledger destinations do.
 */
enum class DetailDestination(val route: Route, @StringRes val labelRes: Int, @DrawableRes val icon: Int, @DrawableRes val iconSelected: Int) {
    SAVE_THE_CHANGE(Route.SaveTheChange, R.string.destination_save_the_change, R.drawable.savings_48px, R.drawable.savings_fill_48px),
    SETTINGS(Route.Settings, R.string.destination_settings, R.drawable.settings_48px, R.drawable.settings_fill_48px);

    /** Settings stays marked while a screen opened from it is on show. */
    fun isCurrent(currentRoute: NavKey?): Boolean = if (this == SETTINGS) isSettingsRoute(currentRoute) else currentRoute == route
}

/** Screens that show no amount, so the hide-amounts switch has nothing to mask there. */
val AMOUNT_FREE_ROUTES: Set<NavKey> = setOf(
    Route.Categories,
    Route.Tags,
    Route.Settings,
    Route.AccountTypes,
    Route.SaveTheChange,
)

/** The destinations that each keep a history of their own; anything else opens on top of one of them. */
val TOP_LEVEL_ROUTES: Set<NavKey> =
    (YuukaDestination.entries.map { it.route } + DetailDestination.entries.map { it.route }).toSet()
