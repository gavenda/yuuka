package dev.gavenda.yuuka.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Month/date helpers mirroring the API's `YYYY-MM` and `YYYY-MM-DD` formats. */

fun currentMonth(now: LocalDate = LocalDate.now()): String =
    "%04d-%02d".format(now.year, now.monthValue)

fun today(now: LocalDate = LocalDate.now()): String =
    "${currentMonth(now)}-%02d".format(now.dayOfMonth)

fun addMonths(month: String, delta: Long): String {
    val ym = YearMonth.parse(month).plusMonths(delta)
    return "%04d-%02d".format(ym.year, ym.monthValue)
}

/** `2026-09` -> `September 2026`. */
fun formatMonth(month: String): String {
    val ym = YearMonth.parse(month)
    val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    return "$monthName ${ym.year}"
}

/** `2026-09-15[T14:30]` -> `15 Sep`, rendered from the date part to dodge timezone shifts. */
fun formatDate(date: String): String {
    val local = LocalDate.parse(date.take(10))
    return local.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
}

fun formatLongDate(date: String): String {
    val local = LocalDate.parse(date.take(10))
    return local.format(DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM).withLocale(Locale.getDefault()))
}

private val TIME_PATTERN = Regex("^\\d{2}:\\d{2}$")

/** `2026-09-15T14:30` -> `2:30 PM`; null when the transaction carries no time of day. */
fun formatTime(date: String): String? {
    if (date.length < 16) return null
    val time = date.substring(11, 16)
    if (!TIME_PATTERN.matches(time)) return null
    val local = LocalTime.parse(time)
    return local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
}
