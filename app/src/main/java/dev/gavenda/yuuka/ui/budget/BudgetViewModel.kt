package dev.gavenda.yuuka.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.CategoryScope
import dev.gavenda.yuuka.data.model.IncomePlanMode
import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.currentMonth
import dev.gavenda.yuuka.repository.BudgetRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.ui.common.ScreenStatus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BudgetUiState(
    val month: String = currentMonth(),
    val currency: String = DEFAULT_CURRENCY,
    val summary: Summary? = null,
    val expenseBreakdown: List<CategoryBreakdown> = emptyList(),
    val incomeBreakdown: List<CategoryBreakdown> = emptyList(),
    val cashflowBreakdown: List<CategoryBreakdown> = emptyList(),
    val plannedIncome: Long = 0,
    val plannedIncomeMode: IncomePlanMode = IncomePlanMode.fixed,
    val plannedIncomeGrossAmount: Long? = null,
    val unspent: Long = 0,
    val overspent: Long = 0,
    val status: ScreenStatus = ScreenStatus.Idle,
) {
    val totalPlanned: Long get() = expenseBreakdown.sumOf { it.planned }
    val totalActual: Long get() = expenseBreakdown.sumOf { it.actual }
    val totalAllocated: Long get() = (expenseBreakdown + cashflowBreakdown).sumOf { it.planned }
    val unallocatedIncome: Long get() = plannedIncome - totalAllocated
    val hasAnyCategories: Boolean get() = summary != null && (expenseBreakdown.isNotEmpty() || incomeBreakdown.isNotEmpty() || cashflowBreakdown.isNotEmpty())
}

/** Mirrors the web app's `budget` Pinia store plus `BudgetView.vue`'s own derived totals. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BudgetViewModel(
    private val ledgerRepository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {
    private val month = MutableStateFlow(currentMonth())
    private val status = MutableStateFlow<ScreenStatus>(ScreenStatus.Idle)

    private val _mutationErrors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val mutationErrors: SharedFlow<String> = _mutationErrors

    /** Categories (or "income") with a budget save in flight, so only that row's control disables/spins. */
    private val _savingKeys = MutableStateFlow<Set<String>>(emptySet())
    val savingKeys: StateFlow<Set<String>> = _savingKeys.asStateFlow()

    val uiState: StateFlow<BudgetUiState> = combine(
        month,
        ledgerRepository.settings,
        month.flatMapLatest { budgetRepository.observeSummary(it) },
        status,
    ) { currentMonthValue, settings, summary, currentStatus ->
        val standard = summary?.categories.orEmpty().filter { it.appliesTo == CategoryScope.standard }
        val expense = standard.filter { it.kind == CategoryKind.expense }.sortedByDescending { it.actual }
        val income = standard.filter { it.kind == CategoryKind.income }.sortedByDescending { it.actual }
        val cashflow = summary?.categories.orEmpty().filter { it.appliesTo == CategoryScope.transfer }.sortedByDescending { it.actual }

        BudgetUiState(
            month = currentMonthValue,
            currency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
            summary = summary,
            expenseBreakdown = expense,
            incomeBreakdown = income,
            cashflowBreakdown = cashflow,
            plannedIncome = summary?.plannedIncome ?: 0,
            plannedIncomeMode = summary?.plannedIncomeMode ?: IncomePlanMode.fixed,
            plannedIncomeGrossAmount = summary?.plannedIncomeGrossAmount,
            unspent = expense.sumOf { maxOf(0, it.remaining) },
            overspent = expense.sumOf { minOf(0, it.remaining) },
            status = currentStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUiState())

    init {
        viewModelScope.launch { runCatching { ledgerRepository.refreshSettings() } }
        refresh()
    }

    fun setMonth(next: String) {
        month.value = next
        refresh()
    }

    fun refresh() {
        val target = month.value
        viewModelScope.launch {
            status.value = ScreenStatus.Loading
            try {
                budgetRepository.refreshSummary(target)
                status.value = ScreenStatus.Idle
            } catch (e: ApiError) {
                status.value = ScreenStatus.Error(e.message ?: "Could not load the summary.")
            }
        }
    }

    fun setBudgetAmount(categoryId: String, amount: Long) {
        viewModelScope.launch {
            _savingKeys.update { it + categoryId }
            try {
                budgetRepository.setBudgetAmount(categoryId, month.value, amount)
            } catch (e: ApiError) {
                _mutationErrors.tryEmit(e.message ?: "Could not save the budget.")
            } finally {
                _savingKeys.update { it - categoryId }
            }
        }
    }

    fun setBudgetPercent(categoryId: String, percent: Double) {
        viewModelScope.launch {
            _savingKeys.update { it + categoryId }
            try {
                budgetRepository.setBudgetPercent(categoryId, month.value, percent)
            } catch (e: ApiError) {
                _mutationErrors.tryEmit(e.message ?: "Could not save the budget.")
            } finally {
                _savingKeys.update { it - categoryId }
            }
        }
    }

    fun setIncomePlan(amount: Long, mode: IncomePlanMode, grossAmount: Long?) {
        viewModelScope.launch {
            _savingKeys.update { it + INCOME_KEY }
            try {
                budgetRepository.setIncomePlan(month.value, amount, mode.name, grossAmount)
            } catch (e: ApiError) {
                _mutationErrors.tryEmit(e.message ?: "Could not save planned income.")
            } finally {
                _savingKeys.update { it - INCOME_KEY }
            }
        }
    }

    companion object {
        const val INCOME_KEY = "income"
    }
}
