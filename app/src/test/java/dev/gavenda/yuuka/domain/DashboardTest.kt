package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.Subscription
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

    @Test
    fun budgetPaceTotalsExpensesAndNamesTheDayOnlyWhileTheMonthRuns() {
        val plan = listOf(entry("food", 6000, 3000), entry("rent", 4000, 4000), entry("salary", 9999, 9999, CategoryKind.income))
        assertEquals(BudgetPace(7000, 10000, 70, 16, 31), budgetPace(plan, "2026-10", "2026-10-16"))
        assertNull(budgetPace(plan, "2026-09", "2026-10-16")?.day)
        assertNull(budgetPace(listOf(entry("food", 0, 3000)), "2026-10", "2026-10-16"))
    }

    @Test
    fun budgetPaceHealthWarnsAheadOfTheMonthAndIsCriticalPastThePlan() {
        assertEquals(BudgetHealth.WARNING, BudgetPace(7000, 10000, 70, 16, 31).health)
        assertEquals(BudgetHealth.GOOD, BudgetPace(4000, 10000, 40, 16, 31).health)
        assertEquals(BudgetHealth.GOOD, BudgetPace(7000, 10000, 70, null, 30).health)
        assertEquals(BudgetHealth.CRITICAL, BudgetPace(10001, 10000, 100, null, 30).health)
    }

    @Test
    fun incomeProgressIsUnclampedAndAbsentWithoutAPlan() {
        assertEquals(120, incomeProgress(12000, 10000))
        assertNull(incomeProgress(5000, 0))
    }

    @Test
    fun accountGroupsGroupTheAccountsInUseUnderTheirTypesLargestFirst() {
        val groups = accountGroups(
            listOf(
                account("a", "Savings", 100),
                account("b", "checking", 50),
                account("c", "Savings", 900),
                account("d", "Savings", 5000, archived = true),
            ),
        )
        assertEquals(listOf("checking" to listOf("b"), "Savings" to listOf("c", "a")), groups.map { (type, rows) -> type to rows.map { it.id } })
    }

    @Test
    fun mixedCurrenciesNoticesASecondCurrencyAmongAccountsInUse() {
        assertEquals(false, mixedCurrencies(listOf(account("a", currency = "PHP"), account("b", currency = "php"))))
        assertEquals(true, mixedCurrencies(listOf(account("a", currency = "PHP"), account("b", currency = "USD"))))
        assertEquals(false, mixedCurrencies(listOf(account("a", currency = "PHP"), account("b", currency = "USD", archived = true))))
    }

    @Test
    fun categoryChangesAreHowFarEachCategoryMovedFromTheOtherMonth() {
        val changes = categoryChanges(
            listOf(entry("food", 0, 1200), entry("fun", 0, 300), entry("new", 0, 50)),
            listOf(entry("food", 0, 1000), entry("fun", 0, 500), entry("gone", 0, 999)),
        )
        assertEquals(mapOf("food" to 200L, "fun" to -200L, "new" to 50L), changes)
    }

    @Test
    fun stillToPostListsActiveSubscriptionsNextRunningInTheMonthSoonestFirst() {
        val list = stillToPost(
            listOf(
                subscription("late", "2026-10-28"),
                subscription("soon", "2026-10-20"),
                subscription("paused", "2026-10-21", enabled = false),
                subscription("next", "2026-11-02"),
            ),
            "2026-10",
        )
        assertEquals(listOf("soon", "late"), list.map { it.id })
    }

    private fun subscription(id: String, nextRunOn: String, enabled: Boolean = true) =
        Subscription(id = id, accountId = "acc", amount = -500, payee = id, startOn = nextRunOn, dayOfMonth = 1, nextRunOn = nextRunOn, enabled = enabled)

    private fun account(id: String, type: String = "Checking", balance: Long = 0, currency: String = "PHP", archived: Boolean = false) =
        Account(id = id, name = id, typeId = type, typeName = type, currency = currency, startingBalance = 0, balance = balance, archived = archived)

    private fun entry(id: String, planned: Long, actual: Long, kind: CategoryKind = CategoryKind.expense) =
        CategoryBreakdown(categoryId = id, name = id, kind = kind, color = "#2a78d6", planned = planned, actual = actual, remaining = planned - actual)
}
