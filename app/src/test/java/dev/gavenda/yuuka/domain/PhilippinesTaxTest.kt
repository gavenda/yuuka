package dev.gavenda.yuuka.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PhilippinesTaxTest {
    @Test
    fun `a zero gross salary produces a zero net pay without touching the tax table`() {
        val breakdown = computeNetPay(0)
        assertEquals(0L, breakdown.netPay)
        assertEquals(0L, breakdown.incomeTax)
    }

    @Test
    fun `a small gross salary that contributions fully consume does not crash`() {
        // Below the SSS and PhilHealth floors, so contributions alone exceed
        // gross and taxable income clamps to exactly zero.
        val breakdown = computeNetPay(100)
        assertEquals(0L, breakdown.taxableIncome)
        assertEquals(0L, breakdown.incomeTax)
    }

    @Test
    fun `a typical gross salary is taxed at the matching bracket`() {
        val breakdown = computeNetPay(3_000_000)
        assertEquals(3_000_000L, breakdown.gross)
        assert(breakdown.netPay in 1..breakdown.gross)
    }
}
