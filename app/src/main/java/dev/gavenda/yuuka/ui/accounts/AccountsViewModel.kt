package dev.gavenda.yuuka.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.today
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.repository.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AccountGroup(val id: String, val name: String, val accounts: List<Account>, val total: Long, val currency: String)

data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val accountTypes: List<AccountType> = emptyList(),
    val displayCurrency: String = DEFAULT_CURRENCY,
    val netWorth: Long = 0,
    val showArchived: Boolean = false,
    val searchText: String = "",
) {
    val archivedCount: Int get() = accounts.count { it.archived }

    /**
     * What the search view lists: every account until something is typed, then those whose name, type or currency
     * holds the text. Archived ones follow the same toggle as the grouped list.
     */
    val matches: List<Account>
        get() {
            val query = searchText.trim()
            return accounts.filter {
                (showArchived || !it.archived) &&
                    (
                        query.isEmpty() ||
                            it.name.contains(query, ignoreCase = true) ||
                            it.typeName?.contains(query, ignoreCase = true) == true ||
                            it.currency.contains(query, ignoreCase = true)
                        )
            }
        }

    /** Accounts listed under their type, in the order the types are sorted in — mirrors `AccountsView.vue`'s `groups`. */
    val groups: List<AccountGroup>
        get() {
            val visible = matches
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

/** What the account form hands back. Whether its purchases round up is not here: Save the Change owns that. */
data class AccountSubmission(
    val name: String,
    val typeId: String,
    val currency: String,
    val startingBalance: Long,
    val logoUrl: String,
    val logoInvertDark: Boolean,
    /** A preset bank's key, or null for Custom. */
    val bank: String?,
)

/** Mirrors `AccountsView.vue`. */
class AccountsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val transactionRepository: TransactionRepository,
) : ViewModel() {
    private val showArchived = MutableStateFlow(false)
    private val searchText = MutableStateFlow("")

    val uiState: StateFlow<AccountsUiState> = combine(
        ledgerRepository.accounts,
        ledgerRepository.accountTypes,
        ledgerRepository.settings,
        showArchived,
        searchText,
    ) { accounts, types, settings, archived, search ->
        AccountsUiState(
            accounts = accounts,
            accountTypes = types,
            displayCurrency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
            netWorth = accounts.filter { !it.archived }.sumOf { it.balance },
            showArchived = archived,
            searchText = search,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    init {
        viewModelScope.launch { runCatching { ledgerRepository.refreshAccounts() } }
    }

    fun setSearchText(text: String) {
        searchText.value = text
    }

    fun toggleShowArchived() {
        showArchived.value = !showArchived.value
    }

    /** A new account when [id] is null. */
    suspend fun save(id: String?, submission: AccountSubmission) {
        with(submission) {
            if (id == null) {
                ledgerRepository.createAccount(name, typeId, currency, startingBalance, logoUrl, logoInvertDark, bank)
            } else {
                // The round-up opt-in is deliberately left out: the Save the Change screen owns it, and an edit
                // here must not quietly take it off an account.
                ledgerRepository.updateAccount(id, name, typeId, currency, startingBalance, logoUrl, logoInvertDark, bank)
            }
        }
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        ledgerRepository.setAccountArchived(id, archived)
    }

    /** May throw [dev.gavenda.yuuka.data.remote.ApiError] with status 409 when the account still has transactions. */
    suspend fun deleteAccount(id: String, includeTransactions: Boolean = false) {
        ledgerRepository.deleteAccount(id, includeTransactions)
    }

    /**
     * Logs the difference between [id]'s current balance and [balance] as its own transaction —
     * an expense if it dropped, income if it rose. May throw [dev.gavenda.yuuka.data.remote.ApiError]
     * with status 400 when the account is already at that balance.
     */
    suspend fun adjustBalance(id: String, balance: Long, payee: String) {
        transactionRepository.adjustAccountBalance(id, balance, today(), payee, notes = "")
    }
}
