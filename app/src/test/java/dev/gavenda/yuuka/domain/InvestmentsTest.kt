package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same figures `test/investments.spec.ts` pins on the server: a trade made offline must agree with it. */
class InvestmentsTest {
    private fun trade(kind: String, on: String, shares: Long, price: Long, fees: Long = 0) = Trade(
        id = "$kind$on", accountId = "a", symbol = "X", kind = kind, occurredOn = on,
        shares = shares, price = price, fees = fees, amount = tradeCash(kind, shares, price, fees), transactionId = "t$on",
    )

    @Test
    fun `values shares to the centavo, rounding half up`() {
        assertEquals(1_180_000L, valueOf(100, 1_180_000))
        assertEquals(0L, valueOf(3, 5))
        assertEquals(1L, valueOf(10, 5))
    }

    @Test
    fun `signs a trade's cash by what it does`() {
        assertEquals(-102_000L, tradeCash(TradeKinds.BUY, 10, 1_000_000, 2_000))
        assertEquals(98_000L, tradeCash(TradeKinds.SELL, 10, 1_000_000, 2_000))
        assertEquals(900L, tradeCash(TradeKinds.DIVIDEND, 10, 10_000, 100))
    }

    @Test
    fun `takes shares out at the average cost and realises the rest`() {
        val result = positions(
            listOf(
                trade(TradeKinds.BUY, "2026-01-01", 100, 100_000),
                trade(TradeKinds.BUY, "2026-02-01", 100, 200_000),
                trade(TradeKinds.SELL, "2026-03-01", 50, 300_000),
            ),
        )
        assertEquals(listOf(Position("a", "X", 150, 225_000, 75_000, 0)), result)
    }

    @Test
    fun `refuses a history that sells more than it holds`() {
        assertNull(positions(listOf(trade(TradeKinds.SELL, "2026-01-01", 1, 100_000), trade(TradeKinds.BUY, "2026-02-01", 100, 100_000))))
    }

    @Test
    fun `charges the PSE schedule, with the ₱20 minimum commission`() {
        // ₱11,800.00 bought: 0.25% is ₱29.50, VAT ₱3.54, PSE ₱0.59, SCCP ₱1.18.
        assertEquals(PseFees(2_950, 354, 59, 118, 0), pseFees(1_180_000, sell = false))
        // ₱1,000.00 sold: the commission floors at ₱20.00, and the 0.1% sales tax applies.
        assertEquals(PseFees(2_000, 240, 5, 10, 100), pseFees(100_000, sell = true))
    }

    @Test
    fun `parses a price to four decimals, split on the point`() {
        assertEquals(1_180_000L, parsePrice("118"))
        assertEquals(5_700L, parsePrice("0.57"))
        assertEquals(1L, parsePrice("0.0001"))
        assertNull(parsePrice("0.00001"))
        assertNull(parsePrice("0"))
    }

    @Test
    fun `quotes a price as the PSE does`() {
        assertEquals("118.00", priceInput(1_180_000))
        assertEquals("0.0125", priceInput(125))
        assertEquals("1234.50", priceInput(12_345_000))
    }

    @Test
    fun `knows the board lot by price band`() {
        assertEquals(10L, boardLot(1_180_000))
        assertEquals(1_000L, boardLot(5_700))
        assertEquals(1_000_000L, boardLot(50))
    }
}
