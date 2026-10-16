package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.CategoryKind
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
