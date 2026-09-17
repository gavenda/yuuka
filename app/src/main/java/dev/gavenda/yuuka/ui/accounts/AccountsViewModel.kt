package dev.gavenda.yuuka.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.currentMonth
import dev.gavenda.yuuka.repository.BudgetRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountGroup(val id: String, val name: String, val accounts: List<Account>, val total: Long, val currency: String)

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val accountTypes: List<AccountType> = emptyList(),
    val displayCurrency: String = DEFAULT_CURRENCY,
    val netWorth: Long = 0,
    val showArchived: Boolean = false,
) {
    val archivedCount: Int get() = accounts.count { it.archived }

    /** Accounts listed under their type, in the order the types are sorted in — mirrors `AccountsView.vue`'s `groups`. */
    val groups: List<AccountGroup>
        get() {
            val visible = accounts.filter { showArchived || !it.archived }
            val byType = visible.groupBy { it.typeId }.toMutableMap()
            val result = mutableListOf<AccountGroup>()

            for (type in accountTypes.sortedBy { it.sortOrder }) {
                val list = byType.remove(type.id) ?: continue
                result += toGroup(type.id, type.name, list)
            }
            for ((typeId, list) in byType) {
                result += toGroup(typeId.ifEmpty { "untyped" }, list.firstOrNull()?.typeName ?: "Uncategorized", list)
            }
            return result
        }

    private fun toGroup(id: String, name: String, list: List<Account>): AccountGroup {
        val currencies = list.map { it.currency }.toSet()
        return AccountGroup(id, name, list, list.sumOf { it.balance }, if (currencies.size == 1) currencies.first() else displayCurrency)
    }
}

/** Mirrors `AccountsView.vue` and the account-type CRUD in `AccountTypeManager.vue`. */
class AccountsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val budgetRepository: BudgetRepository,
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)

    val uiState: StateFlow<AccountsUiState> = combine(
        ledgerRepository.accounts,
        ledgerRepository.accountTypes,
        ledgerRepository.settings,
        showArchived,
    ) { accounts, types, settings, archived ->
        AccountsUiState(
            accounts = accounts,
            accountTypes = types,
            displayCurrency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
            netWorth = accounts.filter { !it.archived }.sumOf { it.balance },
            showArchived = archived,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    init {
        viewModelScope.launch { runCatching { ledgerRepository.refreshAccounts() } }
    }

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    private suspend fun resyncBudget() = runCatching { budgetRepository.refreshSummary(currentMonth()) }

    suspend fun createAccount(name: String, typeId: String, currency: String, startingBalance: Long, logoUrl: String, logoInvertDark: Boolean) {
        ledgerRepository.createAccount(name, typeId, currency, startingBalance, logoUrl, logoInvertDark)
        resyncBudget()
    }

    suspend fun updateAccount(id: String, name: String, typeId: String, currency: String, startingBalance: Long, logoUrl: String, logoInvertDark: Boolean) {
        ledgerRepository.updateAccount(id, name, typeId, currency, startingBalance, logoUrl, logoInvertDark)
        resyncBudget()
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        ledgerRepository.setAccountArchived(id, archived)
        resyncBudget()
    }

    /** May throw [dev.gavenda.yuuka.data.remote.ApiError] with status 409 when the account still has transactions. */
    suspend fun deleteAccount(id: String, includeTransactions: Boolean = false) {
        ledgerRepository.deleteAccount(id, includeTransactions)
        resyncBudget()
    }

    suspend fun createAccountType(name: String) {
        val sortOrder = uiState.value.accountTypes.maxOfOrNull { it.sortOrder }?.plus(1) ?: 0
        ledgerRepository.createAccountType(name, sortOrder)
    }

    suspend fun renameAccountType(id: String, name: String) = ledgerRepository.renameAccountType(id, name)

    suspend fun setAccountTypeArchived(id: String, archived: Boolean) = ledgerRepository.setAccountTypeArchived(id, archived)

    suspend fun deleteAccountType(id: String) = ledgerRepository.deleteAccountType(id)
}
