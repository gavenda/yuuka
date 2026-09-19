package dev.gavenda.yuuka.domain

private val UNITS = listOf(1_000_000_000L to "b", 1_000_000L to "m", 1_000L to "k")

/**
 * A count short enough to sit at the end of a row: 950, 1.2k, 3.4m, 2b. Mirrors `src/lib/count.ts`.
 *
 * It truncates to one decimal rather than rounding, so a count never reads as more than it is
 * (999,999 is 999.9k, not 1000k), and drops a ".0". Whole numbers below a thousand are shown as they are.
 */
fun formatCount(count: Long): String {
    val whole = count.coerceAtLeast(0)
    val (from, suffix) = UNITS.firstOrNull { whole >= it.first } ?: return whole.toString()

    // Integer tenths, so 1,300 is exactly 13 and never 12.999…
    val tenths = whole / (from / 10)
    val fraction = tenths % 10
    return "${tenths / 10}${if (fraction != 0L) ".$fraction" else ""}$suffix"
}
