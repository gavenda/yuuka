package dev.gavenda.yuuka.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.currentMonth
import dev.gavenda.yuuka.repository.BudgetRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.repository.SyncRepository
import dev.gavenda.yuuka.ui.common.ScreenStatus
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val month: String = currentMonth(),
    val currency: String = DEFAULT_CURRENCY,
    val summary: Summary? = null,
    val status: ScreenStatus = ScreenStatus.Idle,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val ledgerRepository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
    syncRepository: SyncRepository,
) : ViewModel() {
    private val month = MutableStateFlow(currentMonth())
    private val status = MutableStateFlow<ScreenStatus>(ScreenStatus.Idle)

    val uiState: StateFlow<DashboardUiState> = combine(
        month,
        ledgerRepository.settings,
        month.flatMapLatest { budgetRepository.observeSummary(it) },
        status,
    ) { currentMonthValue, settings, summary, currentStatus ->
        DashboardUiState(
            month = currentMonthValue,
            currency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
            summary = summary,
            status = currentStatus,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        viewModelScope.launch { runCatching { ledgerRepository.refreshAll() } }
        viewModelScope.launch { syncRepository.synced.collect { refresh() } }
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
}
