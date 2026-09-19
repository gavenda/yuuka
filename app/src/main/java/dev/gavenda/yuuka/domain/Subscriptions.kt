package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Subscription
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

/**
 * What the active subscriptions come to in a month, signed like the amounts themselves: a net
 * outflow is negative. Paused ones post nothing, so they are left out. Like every aggregate figure
 * it is added up as it stands and shown in the display currency; amounts are not converted between
 * currencies. Mirrors `monthlyTotal` in the web app's `lib/subscriptions.ts`.
 */
fun monthlyTotal(subscriptions: List<Subscription>): Long = subscriptions.filter { it.enabled }.sumOf { it.amount }
