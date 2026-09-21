package dev.gavenda.yuuka.repository

import androidx.room.withTransaction
import dev.gavenda.yuuka.data.local.YuukaDatabase
import dev.gavenda.yuuka.data.local.dao.BudgetDao
import dev.gavenda.yuuka.data.local.dao.IncomePlanDao
import dev.gavenda.yuuka.data.local.dao.SummaryDao
import dev.gavenda.yuuka.data.local.dao.TransactionDao
import dev.gavenda.yuuka.sync.Slices
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The pull-to-refresh sync. The API is the source of truth and Room only a
 * cache of it, and the per-screen `refresh*` calls only ever fold rows in — a
 * transaction, budget or summary deleted elsewhere (the web app, another
 * device) would stay on this one forever. A full sync is what lets them go:
 * it has the screens on show load the paged and per-month data again, and
 * discards what is cached for the budgets and summaries.
 */
class SyncRepository(
    private val database: YuukaDatabase,
    private val ledgerRepository: LedgerRepository,
    private val payeeRepository: PayeeRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val incomePlanDao: IncomePlanDao,
    private val summaryDao: SummaryDao,
) {
    private val _synced = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Set when a sync has left the cached transactions possibly out of date. They are not cleared then — an
     * emptied table is what made the list flicker — but swapped for a fresh fetch by whoever shows them
     * ([TransactionRepository.resync]), or discarded by the next one to open ([discardStaleTransactions]).
     */
    private val transactionsStale = AtomicBoolean(false)

    /** For a screen that has just brought the cache up to date itself. */
    fun markTransactionsFresh() = transactionsStale.set(false)

    /** For a screen opening on a cache a sync has left behind: nothing is on show yet, so clearing costs no flicker. */
    suspend fun discardStaleTransactions() {
        if (transactionsStale.getAndSet(false)) transactionDao.clear()
    }

    /** Emits after a full sync has marked the cache stale; a screen holding per-month or paged data reloads its own view on it. */
    val synced: SharedFlow<Unit> = _synced.asSharedFlow()

    /**
     * Refetches only the named slices.
     *
     * This is what a push resolves to, and what the outbox asks for once a batch
     * has landed. A full sync would answer the same question — everything would
     * certainly be up to date afterwards — but it is the wrong size of answer:
     * a transaction added in the web app should not cost this device its
     * subscription list and every cached month. The slices name what moved, and
     * only that is fetched.
     *
     * Each part is independent and a failure is swallowed: this runs in the
     * background, often from a push with no screen open, so the right response
     * to being unable to reach the API is to leave the last-seen copy alone and
     * catch up on the next sync.
     */
    suspend fun refreshSlices(slices: List<String>) = coroutineScope {
        val wanted = slices.toSet()

        // The ledger's lists all come from one place, so several slices resolve
        // to the same call; each is asked for at most once.
        if (Slices.ACCOUNTS in wanted || Slices.ACCOUNT_TYPES in wanted) launch { runCatching { ledgerRepository.refreshAccounts() } }
        if (Slices.CATEGORIES in wanted) launch { runCatching { ledgerRepository.refreshCategories() } }
        if (Slices.TAGS in wanted) launch { runCatching { ledgerRepository.refreshTags() } }
        if (Slices.SETTINGS in wanted) launch { runCatching { ledgerRepository.refreshSettings() } }
        if (Slices.ROUND_UP_RULE in wanted) launch { runCatching { ledgerRepository.refreshRoundUpRule() } }
        if (Slices.PAYEES in wanted) launch { runCatching { payeeRepository.refresh() } }
        if (Slices.SUBSCRIPTIONS in wanted) launch { runCatching { subscriptionRepository.refresh() } }

        // Transactions, budgets and summaries are held per page and per month,
        // and only the screens on show know which ones. Dropping what is cached
        // and letting them load again is how those catch up — the same
        // mechanism a full sync uses, without touching the whole-list data.
        if (wanted.any { it in setOf(Slices.TRANSACTIONS, Slices.BUDGETS, Slices.INCOME_PLAN, Slices.SUMMARY) }) {
            if (Slices.TRANSACTIONS in wanted) transactionsStale.set(true)
            database.withTransaction {
                if (Slices.BUDGETS in wanted) budgetDao.clear()
                if (Slices.INCOME_PLAN in wanted) incomePlanDao.clear()
                if (Slices.SUMMARY in wanted) summaryDao.clear()
            }
            _synced.tryEmit(Unit)
        }
    }

    /**
     * The whole-list data is replaced from the API first, and only once that
     * has succeeded is the rest cleared — so a failed sync (offline, expired
     * session) throws with the last-seen ledger still on screen rather than
     * leaving it empty.
     */
    suspend fun fullSync() {
        ledgerRepository.refreshAll()
        payeeRepository.refresh()
        subscriptionRepository.refresh()

        transactionsStale.set(true)
        database.withTransaction {
            budgetDao.clear()
            incomePlanDao.clear()
            summaryDao.clear()
        }
        _synced.tryEmit(Unit)
    }
}
