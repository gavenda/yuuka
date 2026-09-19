package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.DailySpend
import java.text.NumberFormat
import java.time.YearMonth
import java.util.Locale

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

/** The average spent on a day that had any spending, or null when no day did. Zero days would only drag it down. */
fun dailyAverage(days: List<DaySeriesEntry>): Long? {
    val spent = days.filter { it.amount > 0 }
    if (spent.isEmpty()) return null
    return spent.sumOf { it.amount } / spent.size
}

/**
 * A value axis reaching [top] in steps of [step], both in minor units: the smallest round number that
 * clears the tallest bar, so the axis reads 0, 5k, 10k rather than 0, 4.6k, 9.2k.
 */
data class AxisScale(val top: Long, val step: Long) {
    /** Every labelled value, from zero to [top]. */
    val ticks: List<Long> get() = (0..top / step).map { it * step }
}

private val NICE_STEPS = listOf(1.0, 2.0, 2.5, 5.0, 10.0)

/** An axis for a chart whose tallest bar is [max], with about three intervals. */
fun axisScale(max: Long): AxisScale {
    if (max <= 0) return AxisScale(top = 100, step = 100)

    val raw = max / AXIS_INTERVALS.toDouble()
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(raw)))
    val step = (NICE_STEPS.map { it * magnitude }.first { it >= raw }).toLong().coerceAtLeast(1)
    return AxisScale(top = (max + step - 1) / step * step, step = step)
}

private const val AXIS_INTERVALS = 3

/**
 * An axis label for [minor] — `1500000` is `15k` — short enough for the narrow strip beside a chart.
 * The currency is left out: the chart says it once elsewhere.
 */
fun compactAmount(minor: Long, locale: Locale = Locale.getDefault()): String {
    val major = minor / 100.0
    val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 1
        minimumFractionDigits = 0
    }
    return when {
        major >= 1_000_000 -> format.format(major / 1_000_000) + "M"
        major >= 1_000 -> format.format(major / 1_000) + "k"
        else -> format.format(major)
    }
}
