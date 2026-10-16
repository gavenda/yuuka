package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.CategoryKind
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

    @Test
    fun overBudgetListsPlannedExpensesPastTheirPlanFurthestFirst() {
        val list = overBudget(
            listOf(
                entry("food", planned = 1000, actual = 1200),
                entry("fun", planned = 1000, actual = 1500),
                entry("rent", planned = 1000, actual = 900),
                entry("unplanned", planned = 0, actual = 500),
                entry("salary", planned = 1000, actual = 1200, kind = CategoryKind.income),
            ),
        )
        assertEquals(listOf("fun", "food"), list.map { it.categoryId })
    }

    private fun entry(id: String, planned: Long, actual: Long, kind: CategoryKind = CategoryKind.expense) =
        CategoryBreakdown(categoryId = id, name = id, kind = kind, color = "#2a78d6", planned = planned, actual = actual, remaining = planned - actual)
}
