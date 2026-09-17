package dev.gavenda.yuuka.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.model.TransactionFilters
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.TransactionRow
import dev.gavenda.yuuka.domain.currentMonth
import dev.gavenda.yuuka.domain.mergeTransferRows
import dev.gavenda.yuuka.repository.BudgetRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.repository.TransactionRepository
import dev.gavenda.yuuka.ui.common.ScreenStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Fetched deeper than the 8 shown so a transfer pair split across the page boundary still merges into one row. */
private const val FETCH_LIMIT = 16
private const val SHOWN = 8

data class DashboardUiState(
    val month: String = currentMonth(),
    val currency: String = DEFAULT_CURRENCY,
    val summary: Summary? = null,
    val recentRows: List<TransactionRow> = emptyList(),
    val status: ScreenStatus = ScreenStatus.Idle,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val ledgerRepository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    private val month = MutableStateFlow(currentMonth())
    private val status = MutableStateFlow<ScreenStatus>(ScreenStatus.Idle)

    val uiState: StateFlow<DashboardUiState> = combine(
        month,
        ledgerRepository.settings,
        month.flatMapLatest { budgetRepository.observeSummary(it) },
        month.flatMapLatest { transactionRepository.observePage(TransactionFilters(month = it), limit = FETCH_LIMIT) },
        status,
    ) { currentMonthValue, settings, summary, transactions, currentStatus ->
        DashboardUiState(
            month = currentMonthValue,
            currency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
            summary = summary,
            recentRows = mergeTransferRows(transactions).take(SHOWN),
            status = currentStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        viewModelScope.launch { runCatching { ledgerRepository.refreshAll() } }
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
                coroutineScope {
                    launch { budgetRepository.refreshSummary(target) }
                    launch { transactionRepository.refreshPage(TransactionFilters(month = target), limit = FETCH_LIMIT, offset = 0) }
                }
                status.value = ScreenStatus.Idle
            } catch (e: ApiError) {
                status.value = ScreenStatus.Error(e.message ?: "Could not load the summary.")
            }
        }
    }
}
