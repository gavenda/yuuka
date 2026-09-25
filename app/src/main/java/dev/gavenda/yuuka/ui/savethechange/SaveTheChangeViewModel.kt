package dev.gavenda.yuuka.ui.savethechange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.domain.CategoryGroup
import dev.gavenda.yuuka.domain.groupForPicker
import dev.gavenda.yuuka.domain.transferCategories
import dev.gavenda.yuuka.repository.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SaveTheChangeUiState(
    val enabled: Boolean = false,
    val roundTo: Long = 1000,
    val destinationAccountId: String? = null,
    /** Must be a transfer-scope category — a round-up posts as an ordinary transfer. Null stays uncategorized. */
    val categoryId: String? = null,
    val accounts: List<Account> = emptyList(),
    /** Cashflow categories, grouped for picking — the same tree a transfer offers. */
    val categoryGroups: List<CategoryGroup> = emptyList(),
)

/** Mirrors `SaveTheChangeView.vue`. Which accounts round up is set on each account's own form, not here. */
class SaveTheChangeViewModel(private val ledgerRepository: LedgerRepository) : ViewModel() {
    val uiState: StateFlow<SaveTheChangeUiState> =
        combine(ledgerRepository.roundUpRule, ledgerRepository.accounts, ledgerRepository.categories) { rule, accounts, categories ->
            SaveTheChangeUiState(
                enabled = rule?.enabled ?: false,
                roundTo = rule?.roundTo ?: 1000,
                destinationAccountId = rule?.destinationAccountId,
                categoryId = rule?.categoryId,
                accounts = accounts.filter { !it.archived },
                categoryGroups = groupForPicker(transferCategories(categories)),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaveTheChangeUiState())

    init {
        viewModelScope.launch {
            runCatching { ledgerRepository.refreshRoundUpRule() }
            runCatching { ledgerRepository.refreshAccounts() }
            runCatching { ledgerRepository.refreshCategories() }
        }
    }

    suspend fun save(enabled: Boolean, roundTo: Long, destinationAccountId: String?, clearDestination: Boolean, categoryId: String?, clearCategory: Boolean) {
        ledgerRepository.updateRoundUpRule(enabled, roundTo, destinationAccountId, clearDestination, categoryId, clearCategory)
    }

    /** Which accounts take part, set here rather than on each account's own form. */
    suspend fun setRoundUpSource(accountId: String, roundUpSource: Boolean) {
        ledgerRepository.setAccountRoundUpSource(accountId, roundUpSource)
    }
}
