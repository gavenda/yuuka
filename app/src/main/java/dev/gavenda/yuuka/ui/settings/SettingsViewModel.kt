package dev.gavenda.yuuka.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.BudgetMode
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.repository.BudgetRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.domain.currentMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val displayCurrency: String = DEFAULT_CURRENCY,
    val budgetMode: BudgetMode = BudgetMode.fixed,
    val defaultAccountId: String? = null,
    val accounts: List<Account> = emptyList(),
)

/** Mirrors `SettingsDialog.vue`. */
class SettingsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(ledgerRepository.settings, ledgerRepository.accounts) { settings, accounts ->
        SettingsUiState(
            displayCurrency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
            budgetMode = settings?.budgetMode ?: BudgetMode.fixed,
            defaultAccountId = settings?.defaultAccountId,
            accounts = accounts.filter { !it.archived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            runCatching { ledgerRepository.refreshSettings() }
            runCatching { ledgerRepository.refreshAccounts() }
        }
    }

    /** Only changed fields are sent, mirroring the dialog's own `currencyChanged`/`budgetModeChanged`/`defaultAccountChanged` guards. */
    suspend fun save(displayCurrency: String?, budgetMode: BudgetMode?, defaultAccountId: String?, clearDefaultAccount: Boolean) {
        ledgerRepository.updateSettings(
            displayCurrency = displayCurrency,
            budgetMode = budgetMode?.name,
            defaultAccountId = defaultAccountId,
            clearDefaultAccount = clearDefaultAccount,
        )
        // Neither setting is shown pre-formatted in the cached summary, but both change what it means, so keep it in step.
        runCatching { budgetRepository.refreshSummary(currentMonth()) }
    }
}
