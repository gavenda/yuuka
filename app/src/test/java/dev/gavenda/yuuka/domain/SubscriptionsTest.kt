package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Subscription
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

    private fun subscription(amount: Long, enabled: Boolean = true) =
        Subscription(id = "s", accountId = "a", amount = amount, payee = "p", startOn = "2026-09-19", dayOfMonth = 19, nextRunOn = "2026-09-19", enabled = enabled)

    @Test
    fun `monthlyTotal adds up what the subscriptions post in a month, outflows negative`() {
        assertEquals(1_800_001L, monthlyTotal(listOf(subscription(-150_000), subscription(-49_999), subscription(2_000_000))))
    }

    @Test
    fun `monthlyTotal leaves out paused subscriptions, which post nothing`() {
        assertEquals(-150_000L, monthlyTotal(listOf(subscription(-150_000), subscription(-999_900, enabled = false))))
    }

    @Test
    fun `monthlyTotal is zero when there is nothing active`() {
        assertEquals(0L, monthlyTotal(emptyList()))
        assertEquals(0L, monthlyTotal(listOf(subscription(-100, enabled = false))))
    }
}
