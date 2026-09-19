package dev.gavenda.yuuka.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** `1` -> `1st`, `22` -> `22nd`, `13` -> `13th`. Mirrors the web app's `ordinal` in `lib/subscriptions.ts`. */
fun ordinal(day: Int): String {
    val teens = day % 100
    if (teens in 11..13) return "${day}th"

    return when (day % 10) {
        1 -> "${day}st"
        2 -> "${day}nd"
        3 -> "${day}rd"
        else -> "${day}th"
    }
}

/**
 * Today's date in UTC. Subscriptions run at 00:00 UTC, so the API measures "not in the past"
 * against this rather than the device's own calendar day.
 */
fun utcToday(now: Instant = Instant.now()): LocalDate = now.atZone(ZoneOffset.UTC).toLocalDate()
