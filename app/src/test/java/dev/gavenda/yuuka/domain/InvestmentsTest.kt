package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Trade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The same figures `test/investments.spec.ts` pins on the server: a trade made offline must agree with it. */
class InvestmentsTest {
    /** [shares] are whole. */
    private fun trade(kind: String, on: String, shares: Long, price: Long, fees: Long = 0) = Trade(
        id = "$kind$on", accountId = "a", symbol = "X", kind = kind, occurredOn = on,
        units = shares * UNIT, price = price, fees = fees, amount = tradeCash(kind, shares * UNIT, price, fees), transactionId = "t$on",
    )

    @Test
    fun `values units to the centavo, rounding half up`() {
        assertEquals(1_180_000L, valueOf(1_000_000, 1_180_000))
        assertEquals(0L, valueOf(30_000, 5))
        assertEquals(1L, valueOf(100_000, 5))
        // 1,234.5678 units at ₱1,942.3725.
        assertEquals(239_799_054L, valueOf(12_345_678, 19_423_725))
        // A product past what a Long holds: 100,000,000 units at ₱100,000.0000.
        assertEquals(1_000_000_000_000_000L, valueOf(1_000_000_000_000, 1_000_000_000))
    }

    @Test
    fun `signs a trade's cash by what it does, a fund's from what the trust bank said`() {
        assertEquals(-102_000L, tradeCash(TradeKinds.BUY, 100_000, 1_000_000, 2_000))
        assertEquals(98_000L, tradeCash(TradeKinds.SELL, 100_000, 1_000_000, 2_000))
        assertEquals(900L, tradeCash(TradeKinds.DIVIDEND, 100_000, 10_000, 100))
        // ₱10,000.00 for 5.1483 units, which at ₱1,942.3725 come to ₱9,999.92.
        assertEquals(-1_000_000L, tradeCash(TradeKinds.BUY, 51_483, 19_423_725, 0, gross = 1_000_000))
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
        assertEquals(listOf(Position("a", "X", 1_500_000, 225_000, 75_000, 0)), result)
    }

    @Test
    fun `refuses a history that sells more than it holds`() {
        assertNull(positions(listOf(trade(TradeKinds.SELL, "2026-01-01", 1, 100_000), trade(TradeKinds.BUY, "2026-02-01", 100, 100_000))))
    }

    @Test
    fun `works a day's trades through in time order, an untimed one first`() {
        assertNull(positions(listOf(trade(TradeKinds.BUY, "2026-01-01T10:00", 100, 100_000), trade(TradeKinds.SELL, "2026-01-01T09:30", 10, 100_000))))
        assertEquals(
            900_000L,
            positions(listOf(trade(TradeKinds.SELL, "2026-01-01T09:30", 10, 100_000), trade(TradeKinds.BUY, "2026-01-01", 100, 100_000)))?.single()?.units,
        )
    }

    @Test
    fun `charges the PSE schedule, with the ₱20 minimum commission`() {
        // ₱11,800.00 bought: 0.25% is ₱29.50, VAT ₱3.54, PSE ₱0.59, SCCP ₱1.18.
        assertEquals(PseFees(2_950, 354, 59, 118, 0), pseFees(1_180_000, sell = false))
        // ₱1,000.00 sold: the commission floors at ₱20.00, and the 0.1% sales tax applies.
        assertEquals(PseFees(2_000, 240, 5, 10, 100), pseFees(100_000, sell = true))
        // A broker that waives the minimum (DragonFi) charges the 0.25% alone: ₱2.50 on ₱1,000.00.
        assertEquals(PseFees(250, 30, 5, 10, 100), pseFees(100_000, sell = true, minCommission = 0))
    }

    @Test
    fun `rounds fees up by group for a broker that does, as a DragonFi statement reads`() {
        // 400 RCR at ₱6.03: commission with VAT ₱6.7536 is charged ₱6.76, PSE and SCCP ₱0.3618 is charged ₱0.37.
        val fees = pseFees(241_200, sell = false, minCommission = 0, roundsUp = true)
        assertEquals(PseFees(603, 73, 12, 25, 0), fees)
        assertEquals(676L, fees.commission + fees.vat)
        assertEquals(37L, fees.pseFee + fees.sccp)
    }

    @Test
    fun `parses a price to four decimals, split on the point`() {
        assertEquals(1_180_000L, parsePrice("118"))
        assertEquals(5_700L, parsePrice("0.57"))
        assertEquals(1L, parsePrice("0.0001"))
        assertNull(parsePrice("0.00001"))
        assertEquals(8_409L, parsePrice("0.840905"))
        assertEquals(8_410L, parsePrice("0.840950"))
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

    @Test
    fun aDividendIsPaidOnWhatWasHeldBeforeItsDay() {
        val trades = listOf(
            trade(TradeKinds.BUY, "2026-01-05", 1000, 100_000),
            trade(TradeKinds.SELL, "2026-02-10T10:00", 400, 110_000),
            trade(TradeKinds.BUY, "2026-03-01", 200, 105_000),
        )
        assertEquals(0L, unitsHeldBefore(trades, "a", "X", "2026-01-05"))
        assertEquals(10_000_000L, unitsHeldBefore(trades, "a", "X", "2026-02-10"))
        assertEquals(6_000_000L, unitsHeldBefore(trades, "a", "X", "2026-02-11"))
        assertEquals(8_000_000L, unitsHeldBefore(trades, "a", "X", "2026-03-02"))
        assertEquals(0L, unitsHeldBefore(trades, "b", "X", "2026-03-02"))
    }

    @Test
    fun `gives the units an amount buys at a NAVPU, to the ten-thousandth`() {
        assertEquals(51_483L, unitsFor(1_000_000, 19_423_725))
        assertEquals(500_000L, unitsFor(500_000, 1_000_000))
    }

    @Test
    fun `reads and writes units with up to four decimals, a stock in whole shares`() {
        assertEquals(51_483L, parseUnits("5.1483", whole = false))
        assertEquals(10_000_000L, parseUnits("1,000", whole = false))
        assertNull(parseUnits("1.23456", whole = false))
        assertNull(parseUnits("1.5", whole = true))
        assertEquals(1_000_000L, parseUnits("100", whole = true))
        assertEquals("5.1483", formatUnits(51_483))
        assertEquals("1,234.5", formatUnits(12_345_000))
        assertEquals("100", formatUnits(1_000_000))
    }

    @Test
    fun `names a fund's cash leg as the server does`() {
        assertEquals("BDO PESO MONEY MARKET FUND" to "Subscribe 5.1483 units", legLabels(TradeKinds.BUY, "UITF-128", "BDO PESO MONEY MARKET FUND", 51_483))
        assertEquals("BDO" to "Sell 100 BDO", legLabels(TradeKinds.SELL, "BDO", "BDO Unibank, Inc.", 1_000_000))
    }
}
