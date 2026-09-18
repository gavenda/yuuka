package dev.gavenda.yuuka.repository

import androidx.room.withTransaction
import dev.gavenda.yuuka.data.local.YuukaDatabase
import dev.gavenda.yuuka.data.local.dao.BudgetDao
import dev.gavenda.yuuka.data.local.dao.IncomePlanDao
import dev.gavenda.yuuka.data.local.dao.SummaryDao
import dev.gavenda.yuuka.data.local.dao.TransactionDao
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The pull-to-refresh sync. The API is the source of truth and Room only a
 * cache of it, and the per-screen `refresh*` calls only ever fold rows in — a
 * transaction, budget or summary deleted elsewhere (the web app, another
 * device) would stay on this one forever. A full sync is what lets them go:
 * it discards everything cached for the paged and per-month data and has the
 * screens on show load it again.
 */
class SyncRepository(
    private val database: YuukaDatabase,
    private val ledgerRepository: LedgerRepository,
    private val payeeRepository: PayeeRepository,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val incomePlanDao: IncomePlanDao,
    private val summaryDao: SummaryDao,
) {
    private val _synced = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after a full sync has emptied the cache; a screen holding per-month or paged data reloads its own view on it. */
    val synced: SharedFlow<Unit> = _synced.asSharedFlow()

    /**
     * The whole-list data is replaced from the API first, and only once that
     * has succeeded is the rest cleared — so a failed sync (offline, expired
     * session) throws with the last-seen ledger still on screen rather than
     * leaving it empty.
     */
    suspend fun fullSync() {
        ledgerRepository.refreshAll()
        payeeRepository.refresh()

        database.withTransaction {
            transactionDao.clear()
            budgetDao.clear()
            incomePlanDao.clear()
            summaryDao.clear()
        }
        _synced.tryEmit(Unit)
    }
}
