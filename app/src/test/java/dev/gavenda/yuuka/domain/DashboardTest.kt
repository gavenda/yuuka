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

    @Test
    fun safeToSpendSharesWhatIsLeftOverTheDaysLeftTodayIncluded() {
        // Day 20 of 31 leaves 12 days, today among them; 10,000 of 22,000 spent leaves 12,000.
        assertEquals(SafeToSpend(1000, 12_000, 12), safeToSpend(BudgetPace(10_000, 22_000, 45, 20, 31)))
        // Rounds down, so keeping to it never runs past the plan.
        assertEquals(SafeToSpend(333, 1000, 3), safeToSpend(BudgetPace(0, 1000, 0, 28, 30)))
    }

    @Test
    fun safeToSpendHasNothingToSayPastThePlanOrOutsideTheMonth() {
        assertNull(safeToSpend(BudgetPace(1000, 1000, 100, 10, 30)))
        assertNull(safeToSpend(BudgetPace(500, 1000, 50, null, 30)))
        assertNull(safeToSpend(null))
    }

    @Test
    fun aheadOfPaceListsPlansSpentFasterThanTheMonthIsPassingFurthestFirst() {
        // Day 10 of 31 is about a third of the month gone.
        val list = aheadOfPace(
            listOf(
                entry("food", planned = 1000, actual = 500),
                entry("fun", planned = 1000, actual = 900),
                entry("rent", planned = 1000, actual = 200),
                entry("over", planned = 1000, actual = 1200),
                entry("unplanned", planned = 0, actual = 500),
                entry("salary", planned = 1000, actual = 900, kind = CategoryKind.income),
            ),
            "2026-10",
            "2026-10-10",
        )
        assertEquals(listOf("fun", "food"), list.map { it.categoryId })
        assertEquals(emptyList<CategoryBreakdown>(), aheadOfPace(listOf(entry("food", 1000, 900)), "2026-09", "2026-10-10"))
    }

    @Test
    fun investmentSummaryAddsTheHeldPositionsAndNamesTheLargestStocks() {
        val summary = investmentSummary(
            listOf(
                holding("BDO", "acc-a", shares = 100, cost = 1_400_000, priceE4 = 1_500_000, asOf = "2026-10-30"),
                holding("BDO", "acc-b", shares = 100, cost = 1_450_000, priceE4 = 1_500_000, asOf = "2026-10-30"),
                holding("ALI", "acc-a", shares = 1000, cost = 3_000_000, priceE4 = 280_000, asOf = "2026-10-31"),
                holding("JFC", "acc-a", shares = 10, cost = 250_000, priceE4 = 2_000_000, asOf = null),
                holding("SM", "acc-a", shares = 0, cost = 0, priceE4 = 9_000_000, asOf = "2026-10-31"),
            ),
            top = 2,
        )!!
        // ₱150 × 200 BDO, ₱28 × 1,000 ALI and ₱200 × 10 JFC.
        assertEquals(3_000_000 + 2_800_000 + 200_000L, summary.marketValue)
        assertEquals(6_100_000L, summary.costBasis)
        assertEquals(-100_000L, summary.unrealised)
        assertEquals("2026-10-31", summary.asOf)
        assertEquals(listOf("BDO" to 3_000_000L, "ALI" to 2_800_000L), summary.largest.map { it.symbol to it.marketValue })
    }

    @Test
    fun investmentSummaryHasNothingToSayWithNothingHeld() {
        assertNull(investmentSummary(emptyList()))
        assertNull(investmentSummary(listOf(holding("SM", "acc", shares = 0, cost = 0, priceE4 = 9_000_000, asOf = null))))
    }

    private fun holding(symbol: String, account: String, shares: Long, cost: Long, priceE4: Long, asOf: String?) =
        Holding(Position(account, symbol, shares, cost, 0, 0), name = symbol, accountName = account, priceE4 = priceE4, changeBp = null, asOf = asOf)

    private fun subscription(id: String, nextRunOn: String, enabled: Boolean = true) =
        Subscription(id = id, accountId = "acc", amount = -500, payee = id, startOn = nextRunOn, dayOfMonth = 1, nextRunOn = nextRunOn, enabled = enabled)

    private fun account(id: String, type: String = "Checking", balance: Long = 0, currency: String = "PHP", archived: Boolean = false) =
        Account(id = id, name = id, typeId = type, typeName = type, currency = currency, startingBalance = 0, balance = balance, archived = archived)

    private fun entry(id: String, planned: Long, actual: Long, kind: CategoryKind = CategoryKind.expense) =
        CategoryBreakdown(categoryId = id, name = id, kind = kind, color = "#2a78d6", planned = planned, actual = actual, remaining = planned - actual)
}
