package dev.gavenda.yuuka.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Transaction(
    val id: String,
    val accountId: String,
    val accountName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val amount: Long,
    val occurredOn: String,
    val payee: String,
    val notes: String = "",
    val transferId: String? = null,
    /** This account's own balance immediately after the transaction posted. */
    val runningBalance: Long,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class TransactionPage(
    val transactions: List<Transaction>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

@Serializable
data class Budget(
    val id: String,
    val categoryId: String,
    /** Null when this budget is fixed — it applies to every month rather than one in particular. */
    val month: String? = null,
    val amount: Long,
    /** Whole or fractional percent of the month's planned income (e.g. 12.5), or null for a fixed amount. */
    val percent: Double? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
enum class IncomePlanMode {
    gross,
    fixed,
}

/** The total income planned for a month, set independently of what actually came in. */
@Serializable
data class IncomePlan(
    /** Null when this plan is fixed — it applies to every month rather than one in particular. */
    val month: String? = null,
    val amount: Long,
    /** Whether [amount] was typed directly or is the take-home net of [grossAmount]. */
    val mode: IncomePlanMode,
    val grossAmount: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class SubcategoryBreakdown(
    val categoryId: String,
    val name: String,
    val color: String,
    /** Already included in the parent's [actual]. */
    val actual: Long,
)

@Serializable
data class CategoryBreakdown(
    val categoryId: String,
    val name: String,
    val kind: CategoryKind,
    val color: String,
    val appliesTo: CategoryScope,
    val planned: Long,
    /** Set when [planned] is a share of the month's planned income rather than a fixed amount. */
    val plannedPercent: Double? = null,
    /** Includes everything filed under this category's children. */
    val actual: Long,
    val remaining: Long,
    val children: List<SubcategoryBreakdown> = emptyList(),
)

@Serializable
data class DailySpend(val date: String, val amount: Long)

@Serializable
data class Summary(
    val month: String,
    val income: Long,
    val expenses: Long,
    val net: Long,
    /** The planned total for the month, set separately from actual income — what a percent-based budget is a share of. */
    val plannedIncome: Long,
    /** How [plannedIncome] was set, so the Budget screen can reopen its editor in the same mode. */
    val plannedIncomeMode: IncomePlanMode,
    val plannedIncomeGrossAmount: Long? = null,
    val netWorth: Long,
    /** Money moved into transfer-categorised destinations, e.g. investments. */
    val cashflow: Long,
    val totalBudgeted: Long,
    val accounts: List<Account> = emptyList(),
    val categories: List<CategoryBreakdown> = emptyList(),
    val dailySpend: List<DailySpend> = emptyList(),
    val generatedAt: String = "",
    val cached: Boolean = false,
)

@Serializable
enum class PayeeKind {
    expense,
    income,
    transfer,
}

/** A remembered payee and what it was last filed under. */
@Serializable
data class Payee(
    val id: String,
    val payee: String,
    val kind: PayeeKind,
    val accountId: String? = null,
    val accountName: String? = null,
    val toAccountId: String? = null,
    val toAccountName: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val categoryColor: String? = null,
    val notes: String = "",
    val usedCount: Int = 0,
    val lastUsedAt: String = "",
)
