package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.Subscription
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the dashboard works out from a month's summary, kept apart from the screen so it can be tested.
 * Mirrors the web app's `src/lib/dashboard.ts`.
 */

/** The share of the month's income it ended with, in whole percent, or null when nothing came in to be a share of. */
fun savingsRate(net: Long, income: Long): Int? {
    if (income <= 0) return null
    return (net.toDouble() / income.toDouble() * 100).roundToInt()
}

/** The expense categories spent past their plan, the furthest over first. An unplanned category has no plan to be over. */
fun overBudget(categories: List<CategoryBreakdown>): List<CategoryBreakdown> =
    categories.filter { it.kind == CategoryKind.expense && it.planned > 0 && it.remaining < 0 }.sortedBy { it.remaining }

/**
 * How the month's spending stands against its plan. [percent] is unclamped, so an overrun reads past 100.
 * [day] is today's day of the month while [days] runs, and null for a month that has ended or not begun: the
 * pace a month is kept at is only a question while it is under way.
 */
data class BudgetPace(val spent: Long, val planned: Long, val percent: Int, val day: Int?, val days: Int) {
    /** Past the plan is critical; spending ahead of the share of the month gone is a warning. */
    val health: BudgetHealth
        get() = when {
            spent > planned -> BudgetHealth.CRITICAL
            day != null && spent.toDouble() / planned > day.toDouble() / days -> BudgetHealth.WARNING
            else -> BudgetHealth.GOOD
        }
}

/** The expense categories' spending against their plans, as the Budget screen totals them, or null with nothing planned. */
fun budgetPace(categories: List<CategoryBreakdown>, month: String, today: String = today()): BudgetPace? {
    val expenses = categories.filter { it.kind == CategoryKind.expense }
    val planned = expenses.sumOf { it.planned }
    if (planned <= 0) return null
    val spent = expenses.sumOf { it.actual }
    val days = YearMonth.parse(month).lengthOfMonth()
    val day = if (today.take(7) == month) today.substring(8, 10).toInt() else null
    return BudgetPace(spent, planned, (spent.toDouble() / planned * 100).roundToInt(), day, days)
}

/** Income received against what was planned, in whole percent and unclamped, or null with no plan. */
fun incomeProgress(income: Long, plannedIncome: Long): Int? {
    if (plannedIncome <= 0) return null
    return (income.toDouble() / plannedIncome * 100).roundToInt()
}

/** The accounts still in use, under their types in alphabetical order, the largest balance first within each. */
fun accountGroups(accounts: List<Account>): List<Pair<String, List<Account>>> =
    accounts.filter { !it.archived }
        .groupBy { it.typeName.orEmpty() }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .map { (type, members) -> type to members.sortedByDescending { it.balance } }

/** Whether the accounts in use hold more than one currency — when net worth, which adds them as they stand, is not one currency's figure. */
fun mixedCurrencies(accounts: List<Account>): Boolean = accounts.filter { !it.archived }.map { it.currency.uppercase() }.distinct().size > 1

/**
 * How far each category in [current] moved from [previous] (another month's breakdown), keyed by category: a
 * positive change is more than before. A category the other month never had moved from nothing.
 */
fun categoryChanges(current: List<CategoryBreakdown>, previous: List<CategoryBreakdown>): Map<String, Long> {
    val before = previous.associate { it.categoryId to it.actual }
    return current.associate { it.categoryId to it.actual - (before[it.categoryId] ?: 0) }
}

/** `2026-09` -> `September`: the month alone, for a comparison whose year goes without saying. */
fun monthName(month: String): String = YearMonth.parse(month).month.getDisplayName(TextStyle.FULL, Locale.getDefault())

/**
 * The active subscriptions whose next run falls in [month], the soonest first: what is still to post in it.
 * Only a question for the month under way — every month after it is the whole list again.
 */
fun stillToPost(subscriptions: List<Subscription>, month: String): List<Subscription> =
    subscriptions.filter { it.enabled && it.nextRunOn.take(7) == month }.sortedBy { it.nextRunOn }

/**
 * What can still be spent each day for the plan to hold: what is left of it shared over the days left, today
 * included. [perDay] rounds down, so keeping to it never quite reaches the plan.
 */
data class SafeToSpend(val perDay: Long, val left: Long, val daysLeft: Int)

/** Null unless the month is under way with something of the plan left: past the plan there is nothing safe to spend. */
fun safeToSpend(pace: BudgetPace?): SafeToSpend? {
    val day = pace?.day ?: return null
    val left = pace.planned - pace.spent
    if (left <= 0) return null
    val daysLeft = pace.days - day + 1
    return SafeToSpend(left / daysLeft, left, daysLeft)
}

/**
 * The planned expense categories still within their plan but spending it faster than the month is passing,
 * the furthest ahead first. Only a question while the month runs; one past its plan is over budget instead.
 */
fun aheadOfPace(categories: List<CategoryBreakdown>, month: String, today: String = today()): List<CategoryBreakdown> {
    if (today.take(7) != month) return emptyList()
    val share = today.substring(8, 10).toInt().toDouble() / YearMonth.parse(month).lengthOfMonth()
    return categories
        .filter { it.kind == CategoryKind.expense && it.planned > 0 && it.actual <= it.planned && it.actual.toDouble() / it.planned > share }
        .sortedByDescending { it.actual.toDouble() / it.planned }
}

/** One stock's worth across every broker account that holds it. */
data class StockValue(val symbol: String, val name: String, val marketValue: Long)

/** The holdings at a glance: what they are worth, what they have gained, and the largest of them. */
data class InvestmentSummary(
    val marketValue: Long,
    val costBasis: Long,
    val dayChange: Long,
    /** The latest trading day a held stock was priced at. */
    val asOf: String?,
    val largest: List<StockValue>,
) {
    val unrealised: Long get() = marketValue - costBasis
}

/** The positions still holding shares, summed; null with none. A stock held in two accounts is one of the [top] largest, not two. */
fun investmentSummary(holdings: List<Holding>, top: Int = 3): InvestmentSummary? {
    val held = holdings.filter { it.shares > 0 }
    if (held.isEmpty()) return null
    val largest = held.groupBy { it.symbol }
        .map { (symbol, rows) -> StockValue(symbol, rows.first().name, rows.sumOf { it.marketValue }) }
        .sortedByDescending { it.marketValue }
        .take(top)
    return InvestmentSummary(
        marketValue = held.sumOf { it.marketValue },
        costBasis = held.sumOf { it.costBasis },
        dayChange = held.sumOf { it.dayChange },
        asOf = held.mapNotNull { it.asOf }.maxOrNull(),
        largest = largest,
    )
}
