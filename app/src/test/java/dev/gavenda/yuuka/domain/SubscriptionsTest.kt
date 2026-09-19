package dev.gavenda.yuuka.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SubscriptionsTest {
    @Test
    fun `ordinal suffixes each day of the month`() {
        val expected = listOf("1st", "2nd", "3rd", "4th", "10th", "11th", "12th", "13th", "14th", "21st", "22nd", "23rd", "30th", "31st")
        assertEquals(expected, listOf(1, 2, 3, 4, 10, 11, 12, 13, 14, 21, 22, 23, 30, 31).map(::ordinal))
    }

    @Test
    fun `utcToday is the UTC calendar day, not the local one`() {
        assertEquals(LocalDate.of(2026, 9, 19), utcToday(Instant.parse("2026-09-19T23:59:00Z")))
        assertEquals(LocalDate.of(2026, 9, 20), utcToday(Instant.parse("2026-09-20T00:00:00Z")))
    }
}
