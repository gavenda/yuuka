package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.DailySpend
import java.time.YearMonth

data class DaySeriesEntry(val day: Int, val date: String, val amount: Long)

/** One entry per day of the month, including the days with no spending. Mirrors `src/lib/chart.ts`. */
fun monthSeries(month: String, days: List<DailySpend>): List<DaySeriesEntry> {
    val ym = YearMonth.parse(month)
    val byDate = days.associate { it.date to it.amount }

    return (1..ym.lengthOfMonth()).map { day ->
        val date = "$month-${day.toString().padStart(2, '0')}"
        DaySeriesEntry(day, date, byDate[date] ?: 0)
    }
}

data class RankableEntry(val categoryId: String, val name: String, val color: String, val actual: Long)

private fun CategoryBreakdown.toRankable() = RankableEntry(categoryId, name, color, actual)

/**
 * Ranks entries by amount and folds the tail into a single "Other" row.
 *
 * The cap exists because the categorical palette has a fixed number of slots
 * and is never cycled: a ninth colour would repeat one already on screen, so
 * the ninth entry becomes part of "Other" instead. Mirrors `src/lib/chart.ts`.
 */
fun rankAndFold(entries: List<CategoryBreakdown>, limit: Int): List<RankableEntry> {
    val ranked = entries.filter { it.actual > 0 }.map { it.toRankable() }.sortedByDescending { it.actual }
    if (ranked.size <= limit) return ranked

    val tail = ranked.drop(limit - 1)
    return ranked.take(limit - 1) + RankableEntry("__other__", "Other (${tail.size})", "#898781", tail.sumOf { it.actual })
}
