package dev.gavenda.yuuka.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardTest {
    @Test
    fun savingsRateIsTheShareOfIncomeTheMonthEndedWith() {
        assertEquals(25, savingsRate(2_500_000, 10_000_000))
        assertEquals(-10, savingsRate(-1_000_000, 10_000_000))
    }

    @Test
    fun savingsRateHasNothingToSayWithoutIncome() {
        assertNull(savingsRate(0, 0))
        assertNull(savingsRate(-500, 0))
    }
}
