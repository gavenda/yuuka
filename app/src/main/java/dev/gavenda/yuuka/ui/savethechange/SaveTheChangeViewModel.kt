package dev.gavenda.yuuka.ui.savethechange

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SaveTheChangeUiState(
    val enabled: Boolean = false,
    val roundTo: Long = 1000,
    val destinationAccountId: String? = null,
    val accounts: List<Account> = emptyList(),
)

/** Mirrors `SettingsScreen`/`SettingsViewModel`, plus a per-account opt-in list the web app keeps on the account form instead. */
class SaveTheChangeViewModel(private val ledgerRepository: LedgerRepository) : ViewModel() {
    val uiState: StateFlow<SaveTheChangeUiState> = combine(ledgerRepository.roundUpRule, ledgerRepository.accounts) { rule, accounts ->
        SaveTheChangeUiState(
            enabled = rule?.enabled ?: false,
            roundTo = rule?.roundTo ?: 1000,
            destinationAccountId = rule?.destinationAccountId,
            accounts = accounts.filter { !it.archived },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SaveTheChangeUiState())

    init {
        viewModelScope.launch {
            runCatching { ledgerRepository.refreshRoundUpRule() }
            runCatching { ledgerRepository.refreshAccounts() }
        }
    }

    suspend fun save(enabled: Boolean, roundTo: Long, destinationAccountId: String?, clearDestination: Boolean) {
        ledgerRepository.updateRoundUpRule(enabled, roundTo, destinationAccountId, clearDestination)
    }

    /** Toggling participation on an account is a plain account update — the same field an account's own edit form also carries. */
    suspend fun setAccountParticipation(account: Account, participates: Boolean) {
        ledgerRepository.updateAccount(
            id = account.id,
            name = account.name,
            typeId = account.typeId,
            currency = account.currency,
            startingBalance = account.startingBalance,
            logoUrl = account.logoUrl ?: "",
            logoInvertDark = account.logoInvertDark,
            roundUpSource = participates,
        )
    }
}
