package dev.gavenda.yuuka.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Summary
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.addMonths
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
    /** The month before's summary, which "Where the money went" is compared with; null until it is known. */
    val previous: Summary? = null,
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
        month.flatMapLatest { budgetRepository.observeSummary(addMonths(it, -1)) },
        status,
    ) { currentMonthValue, settings, summary, previous, currentStatus ->
        DashboardUiState(
            month = currentMonthValue,
            currency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
            summary = summary,
            previous = previous,
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
        // Only a comparison: without it the bars stand alone, so a failure here is not the screen's error.
        viewModelScope.launch { runCatching { budgetRepository.refreshSummary(addMonths(target, -1)) } }
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
