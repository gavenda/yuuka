package dev.gavenda.yuuka.ui.accounttypes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.repository.LedgerRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountTypesUiState(val types: List<AccountType> = emptyList())

/** Mirrors `AccountTypesView.vue`. */
class AccountTypesViewModel(private val ledgerRepository: LedgerRepository) : ViewModel() {
    val uiState: StateFlow<AccountTypesUiState> = ledgerRepository.accountTypes
        .map(::AccountTypesUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountTypesUiState())

    /** A type's count moves whenever an account is added or filed elsewhere, so the screen asks again each time it is shown. */
    fun refresh() {
        viewModelScope.launch { runCatching { ledgerRepository.refreshAccounts() } }
    }

    /** New types go after the existing ones rather than at the top. */
    suspend fun create(name: String) {
        val sortOrder = uiState.value.types.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        ledgerRepository.createAccountType(name, sortOrder)
    }

    suspend fun rename(id: String, name: String) = ledgerRepository.renameAccountType(id, name)

    suspend fun setArchived(id: String, archived: Boolean) = ledgerRepository.setAccountTypeArchived(id, archived)

    suspend fun delete(id: String) = ledgerRepository.deleteAccountType(id)
}
