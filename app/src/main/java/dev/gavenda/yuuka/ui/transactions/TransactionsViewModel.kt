package dev.gavenda.yuuka.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionFilters
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.repository.*
import dev.gavenda.yuuka.ui.common.ScreenStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val PAGE_SIZE = 50

data class TransactionsUiState(
    val month: String = currentMonth(),
    val currency: String = DEFAULT_CURRENCY,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val defaultAccountId: String? = null,
    /** What the filters are narrowed to, in the order it was picked; empty means unfiltered. */
    val accountFilter: Set<String> = emptySet(),
    val categoryFilter: Set<String> = emptySet(),
    val tagFilter: Set<String> = emptySet(),
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
    data class Plain(
        val accountId: String,
        val categoryId: String?,
        val amount: Long,
        val occurredOn: String,
        val payee: String,
        val notes: String,
        val tagIds: List<String>,
    ) : TransactionSubmission

    data class Transfer(
        val fromAccountId: String,
        val toAccountId: String,
        val categoryId: String?,
        val amount: Long,
        val occurredOn: String,
        val payee: String,
        val notes: String,
        val tagIds: List<String>,
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
    val payeeRepository: PayeeRepository,
    private val syncRepository: SyncRepository,
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
        accountIds = _uiState.value.accountFilter,
        categoryIds = _uiState.value.categoryFilter,
        tagIds = _uiState.value.tagFilter,
        search = _uiState.value.searchText.trim().ifBlank { null },
    )

    init {
        viewModelScope.launch { ledgerRepository.accounts.collect { list -> _uiState.update { it.copy(accounts = list) } } }
        viewModelScope.launch {
            ledgerRepository.categories.collect { list ->
                _uiState.update { it.copy(categories = list) }
            }
        }
        viewModelScope.launch { ledgerRepository.tags.collect { list -> _uiState.update { it.copy(tags = list) } } }
        viewModelScope.launch {
            ledgerRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        currency = settings?.displayCurrency ?: DEFAULT_CURRENCY,
                        defaultAccountId = settings?.defaultAccountId
                    )
                }
            }
        }

        viewModelScope.launch {
            searchInput.debounce(250.milliseconds).distinctUntilChanged().collect { reload() }
        }

        viewModelScope.launch { syncRepository.synced.collect { resync() } }

        viewModelScope.launch {
            filterKey.flatMapLatest { f ->
                limit.flatMapLatest { l ->
                    transactionRepository.observePage(f, l).map(::mergeTransferRows)
                }
            }
                .collect { rows -> _uiState.update { it.copy(rows = rows) } }
        }

        viewModelScope.launch {
            syncRepository.discardStaleTransactions()
            reload()
        }
    }

    fun setMonth(next: String) {
        _uiState.update { it.copy(month = next) }
        reload()
    }

    fun setAccountFilter(accountIds: Set<String>) {
        _uiState.update { it.copy(accountFilter = accountIds) }
        reload()
    }

    fun setCategoryFilter(categoryIds: Set<String>) {
        _uiState.update { it.copy(categoryFilter = categoryIds) }
        reload()
    }

    fun setTagFilter(tagIds: Set<String>) {
        _uiState.update { it.copy(tagFilter = tagIds) }
        reload()
    }

    fun setSearchText(text: String) {
        _uiState.update { it.copy(searchText = text) }
        searchInput.value = text
    }

    private fun reload() {
        val f = currentFilters()
        filterKey.value = f
        limit.value = f.rowLimit(PAGE_SIZE)
        _uiState.update { it.copy(loadedCount = PAGE_SIZE) }
        refreshFirstPage(f)
    }

    private var firstPageJob: Job? = null

    /**
     * What a sync asks for. Unlike [reload] it keeps the filters, how much is loaded and the status where they
     * are, and the list stays up while the rows are fetched: they replace the cache in one step, so what is on
     * screen changes once, in place. A failure leaves the last-seen rows rather than an error over them.
     */
    private fun resync() {
        val f = currentFilters()
        firstPageJob?.cancel()
        firstPageJob = viewModelScope.launch {
            try {
                val (total, count) = transactionRepository.resync(f, _uiState.value.loadedCount)
                syncRepository.markTransactionsFresh()
                limit.value = f.rowLimit(count)
                _uiState.update { it.copy(total = total, loadedCount = count) }
            } catch (_: ApiError) {
            }
        }
    }

    /** Cancels the one still running, so quick changes to the filters cannot leave an older answer's total on screen. */
    private fun refreshFirstPage(f: TransactionFilters) {
        firstPageJob?.cancel()
        firstPageJob = viewModelScope.launch {
            _uiState.update { it.copy(status = ScreenStatus.Loading) }
            try {
                val page = transactionRepository.refreshPage(f, limit = PAGE_SIZE, offset = 0)
                _uiState.update { it.copy(total = page.total) }
                if (f.narrowsCache) loadRest(f, page.total)
                _uiState.update { it.copy(status = ScreenStatus.Idle) }
            } catch (e: ApiError) {
                _uiState.update { it.copy(status = ScreenStatus.Error(e.message ?: "Could not load transactions.")) }
            }
        }
    }

    /** A filter that narrows the cache only sees what is cached, so it needs the rest of the month's pages, not just the first. */
    private suspend fun loadRest(f: TransactionFilters, total: Int) {
        var loaded = _uiState.value.loadedCount
        while (loaded < total) {
            val page = transactionRepository.refreshPage(f, limit = PAGE_SIZE, offset = loaded)
            if (page.transactions.isEmpty()) break
            loaded += page.transactions.size
            _uiState.update { it.copy(total = page.total, loadedCount = loaded) }
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
                limit.value = f.rowLimit(nextLoaded)
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
        _formState.value =
            TransactionFormState(open = true, editing = transaction, transferToAccountId = transferToAccountId)
    }

    fun closeForm() {
        _formState.value = TransactionFormState()
    }

    fun submit(submission: TransactionSubmission) {
        val editing = _formState.value.editing

        viewModelScope.launch {
            _formState.update { it.copy(submitting = true, error = null) }
            try {
                // Only a plain, newly created expense can trigger "Save the
                // Change" — captured here so its feedback can ride along with
                // the ordinary success message below.
                var roundUp: Transaction? = null

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
                            submission.tagIds,
                        )
                    } else {
                        roundUp = transactionRepository.createTransaction(
                            submission.accountId,
                            submission.categoryId,
                            submission.amount,
                            submission.occurredOn,
                            submission.payee,
                            submission.notes,
                            submission.tagIds,
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
                                submission.tagIds,
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
                                submission.tagIds,
                            )
                        }
                    }
                }

                val isEditing = editing != null
                closeForm()

                val baseMessage = when (submission) {
                    is TransactionSubmission.Transfer -> if (isEditing) "Transfer updated" else "Transfer added"
                    is TransactionSubmission.Plain -> if (isEditing) "Transaction updated" else "Transaction added"
                }

                // Combined into one message rather than a second `_events.tryEmit()`:
                // with `extraBufferCapacity = 1` and no suspension between the two
                // calls, a second emit here could land while the buffer from the
                // first is still unread and get silently dropped.
                _events.tryEmit(
                    if (roundUp != null) {
                        val currency = _uiState.value.accounts.firstOrNull { it.id == roundUp.accountId }?.currency
                            ?: _uiState.value.currency
                        "$baseMessage — +${formatMoney(roundUp.amount, currency)} saved to ${roundUp.accountName}"
                    } else {
                        baseMessage
                    },
                )
            } catch (e: ApiError) {
                _formState.update {
                    it.copy(
                        submitting = false,
                        error = e.message ?: "Could not save the transaction."
                    )
                }
            }
        }
    }

    fun delete(transaction: Transaction) {
        val isTransfer = transaction.transferId != null
        viewModelScope.launch {
            _uiState.update { it.copy(deletingId = transaction.id) }
            try {
                transactionRepository.deleteTransactionOrTransfer(transaction)
                _events.tryEmit(if (isTransfer) "Transfer deleted" else "Transaction deleted")
            } catch (e: ApiError) {
                _uiState.update { it.copy(status = ScreenStatus.Error(e.message ?: "Could not delete.")) }
            } finally {
                _uiState.update { it.copy(deletingId = null) }
            }
        }
    }
}
