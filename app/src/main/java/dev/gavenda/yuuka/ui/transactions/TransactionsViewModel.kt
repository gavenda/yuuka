package dev.gavenda.yuuka.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionFilters
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.TransactionRow
import dev.gavenda.yuuka.domain.currentMonth
import dev.gavenda.yuuka.domain.mergeTransferRows
import dev.gavenda.yuuka.repository.BudgetRepository
import dev.gavenda.yuuka.repository.LedgerRepository
import dev.gavenda.yuuka.repository.PayeeRepository
import dev.gavenda.yuuka.repository.TransactionRepository
import dev.gavenda.yuuka.ui.common.ScreenStatus
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

data class TransactionsUiState(
    val month: String = currentMonth(),
    val currency: String = DEFAULT_CURRENCY,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val defaultAccountId: String? = null,
    val accountFilter: String? = null,
    val categoryFilter: String? = null,
    val searchText: String = "",
    val rows: List<TransactionRow> = emptyList(),
    val total: Int = 0,
    val loadedCount: Int = PAGE_SIZE,
    val status: ScreenStatus = ScreenStatus.Idle,
    val deletingId: String? = null,
) {
    val hasMore: Boolean get() = loadedCount < total
}

sealed interface TransactionSubmission {
    data class Plain(val accountId: String, val categoryId: String?, val amount: Long, val occurredOn: String, val payee: String, val notes: String) :
        TransactionSubmission

    data class Transfer(
        val fromAccountId: String,
        val toAccountId: String,
        val categoryId: String?,
        val amount: Long,
        val occurredOn: String,
        val payee: String,
        val notes: String,
    ) : TransactionSubmission
}

data class TransactionFormState(
    val open: Boolean = false,
    val editing: Transaction? = null,
    val transferToAccountId: String? = null,
    val submitting: Boolean = false,
    val error: String? = null,
)

/** Mirrors the web app's `transactions` Pinia store plus `TransactionsView.vue`'s own dialog and filter state. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class TransactionsViewModel(
    private val ledgerRepository: LedgerRepository,
    private val transactionRepository: TransactionRepository,
    private val budgetRepository: BudgetRepository,
    val payeeRepository: PayeeRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TransactionsUiState())
    val uiState: StateFlow<TransactionsUiState> = _uiState.asStateFlow()

    private val _formState = MutableStateFlow(TransactionFormState())
    val formState: StateFlow<TransactionFormState> = _formState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events

    private val filterKey = MutableStateFlow(currentFilters())
    private val limit = MutableStateFlow(PAGE_SIZE)
    private val searchInput = MutableStateFlow("")

    private fun currentFilters() = TransactionFilters(
        month = _uiState.value.month,
        accountId = _uiState.value.accountFilter,
        categoryId = _uiState.value.categoryFilter,
        search = _uiState.value.searchText.trim().ifBlank { null },
    )

    init {
        viewModelScope.launch { ledgerRepository.accounts.collect { list -> _uiState.update { it.copy(accounts = list) } } }
        viewModelScope.launch { ledgerRepository.categories.collect { list -> _uiState.update { it.copy(categories = list) } } }
        viewModelScope.launch {
            ledgerRepository.settings.collect { settings ->
                _uiState.update { it.copy(currency = settings?.displayCurrency ?: DEFAULT_CURRENCY, defaultAccountId = settings?.defaultAccountId) }
            }
        }

        viewModelScope.launch {
            searchInput.debounce(250).distinctUntilChanged().collect { reload() }
        }

        viewModelScope.launch {
            filterKey.flatMapLatest { f -> limit.flatMapLatest { l -> transactionRepository.observePage(f, l).map(::mergeTransferRows) } }
                .collect { rows -> _uiState.update { it.copy(rows = rows) } }
        }

        reload()
    }

    fun setMonth(next: String) {
        _uiState.update { it.copy(month = next) }
        reload()
    }

    fun setAccountFilter(accountId: String?) {
        _uiState.update { it.copy(accountFilter = accountId) }
        reload()
    }

    fun setCategoryFilter(categoryId: String?) {
        _uiState.update { it.copy(categoryFilter = categoryId) }
        reload()
    }

    fun setSearchText(text: String) {
        _uiState.update { it.copy(searchText = text) }
        searchInput.value = text
    }

    private fun reload() {
        val f = currentFilters()
        filterKey.value = f
        limit.value = PAGE_SIZE
        _uiState.update { it.copy(loadedCount = PAGE_SIZE) }
        refreshFirstPage(f)
    }

    private fun refreshFirstPage(f: TransactionFilters) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = ScreenStatus.Loading) }
            try {
                val page = transactionRepository.refreshPage(f, limit = PAGE_SIZE, offset = 0)
                _uiState.update { it.copy(total = page.total, status = ScreenStatus.Idle) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(status = ScreenStatus.Error(e.message ?: "Could not load transactions.")) }
            }
        }
    }

    fun loadMore() {
        if (_uiState.value.status == ScreenStatus.Loading) return
        val f = filterKey.value
        val offset = _uiState.value.loadedCount

        viewModelScope.launch {
            _uiState.update { it.copy(status = ScreenStatus.Loading) }
            try {
                val page = transactionRepository.refreshPage(f, limit = PAGE_SIZE, offset = offset)
                val nextLoaded = offset + page.transactions.size
                limit.value = nextLoaded
                _uiState.update { it.copy(total = page.total, loadedCount = nextLoaded, status = ScreenStatus.Idle) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(status = ScreenStatus.Error(e.message ?: "Could not load more.")) }
            }
        }
    }

    fun openCreate() {
        viewModelScope.launch { runCatching { payeeRepository.refresh() } }
        _formState.value = TransactionFormState(open = true)
    }

    fun openEdit(transaction: Transaction, transferToAccountId: String? = null) {
        viewModelScope.launch { runCatching { payeeRepository.refresh() } }
        _formState.value = TransactionFormState(open = true, editing = transaction, transferToAccountId = transferToAccountId)
    }

    fun closeForm() {
        _formState.value = TransactionFormState()
    }

    fun submit(submission: TransactionSubmission) {
        val editing = _formState.value.editing

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, error = null) }
            try {
                when (submission) {
                    is TransactionSubmission.Plain -> if (editing != null) {
                        transactionRepository.updateTransaction(
                            editing.id,
                            submission.accountId,
                            submission.categoryId,
                            submission.amount,
                            submission.occurredOn,
                            submission.payee,
                            submission.notes,
                        )
                    } else {
                        transactionRepository.createTransaction(
                            submission.accountId,
                            submission.categoryId,
                            submission.amount,
                            submission.occurredOn,
                            submission.payee,
                            submission.notes,
                        )
                    }

                    is TransactionSubmission.Transfer -> {
                        val transferId = editing?.transferId
                        if (transferId != null) {
                            transactionRepository.updateTransfer(
                                transferId,
                                submission.fromAccountId,
                                submission.toAccountId,
                                submission.categoryId,
                                submission.amount,
                                submission.occurredOn,
                                submission.payee,
                                submission.notes,
                            )
                        } else {
                            transactionRepository.createTransfer(
                                submission.fromAccountId,
                                submission.toAccountId,
                                submission.categoryId,
                                submission.amount,
                                submission.occurredOn,
                                submission.payee,
                                submission.notes,
                            )
                        }
                    }
                }

                val isEditing = editing != null
                closeForm()
                refreshAfterMutation()
                _events.tryEmit(
                    when (submission) {
                        is TransactionSubmission.Transfer -> if (isEditing) "Transfer updated" else "Transfer added"
                        is TransactionSubmission.Plain -> if (isEditing) "Transaction updated" else "Transaction added"
                    },
                )
            } catch (e: ApiError) {
                _formState.update { it.copy(submitting = false, error = e.message ?: "Could not save the transaction.") }
            }
        }
    }

    fun delete(transaction: Transaction) {
        val isTransfer = transaction.transferId != null
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = transaction.id) }
            try {
                transactionRepository.deleteTransactionOrTransfer(transaction)
                refreshAfterMutation()
                _events.tryEmit(if (isTransfer) "Transfer deleted" else "Transaction deleted")
            } catch (e: ApiError) {
                _uiState.update { it.copy(status = ScreenStatus.Error(e.message ?: "Could not delete.")) }
            } finally {
                _uiState.update { it.copy(deletingId = null) }
            }
        }
    }

    private suspend fun refreshAfterMutation() = coroutineScope {
        val f = filterKey.value
        val loaded = _uiState.value.loadedCount
        launch { runCatching { ledgerRepository.refreshAccounts() } }
        launch { runCatching { budgetRepository.refreshSummary(f.month ?: currentMonth()) } }
        launch { runCatching { transactionRepository.refreshPage(f, limit = loaded, offset = 0) } }
    }
}
