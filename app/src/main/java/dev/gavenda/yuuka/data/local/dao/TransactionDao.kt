package dev.gavenda.yuuka.data.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import dev.gavenda.yuuka.data.local.entity.TransactionEntity
import dev.gavenda.yuuka.data.local.entity.TransactionTagEntity
import dev.gavenda.yuuka.data.local.entity.TransactionWithTags
import kotlinx.coroutines.flow.Flow

/** SQLite caps the variables in one statement (999 on older releases), and a page can be reloaded up to everything loaded so far. */
private const val ID_CHUNK = 500

@Dao
interface TransactionDao {
    /**
     * Each `filter…` flag says whether that filter applies at all, so an empty id list
     * means "everything" rather than "nothing". A row matches any id of a filter, and must
     * satisfy every filter that applies. `categoryNone` adds the uncategorised to the
     * categories; see [dev.gavenda.yuuka.repository.TransactionRepository] for how these are set.
     *
     * A search also matches the names of a transaction's tags, as the API's does.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:month IS NULL OR occurredOn LIKE (:month || '%'))
          AND (:filterAccounts = 0 OR accountId IN (:accountIds))
          AND (:filterCategories = 0 OR categoryId IN (:categoryIds) OR (:categoryNone = 1 AND categoryId IS NULL))
          AND (
            :filterTags = 0
            OR EXISTS (SELECT 1 FROM transaction_tags ft WHERE ft.transactionId = transactions.id AND ft.tagId IN (:tagIds))
          )
          AND (
            :search IS NULL
            OR payee LIKE ('%' || :search || '%')
            OR notes LIKE ('%' || :search || '%')
            OR EXISTS (
              SELECT 1 FROM transaction_tags tt JOIN tags g ON g.id = tt.tagId
              WHERE tt.transactionId = transactions.id AND g.name LIKE ('%' || :search || '%')
            )
          )
        ORDER BY occurredOn DESC, createdAt DESC
        LIMIT :limit OFFSET :offset
        """,
    )
    fun observePage(
        month: String?,
        filterAccounts: Boolean,
        accountIds: List<String>,
        filterCategories: Boolean,
        categoryIds: List<String>,
        categoryNone: Boolean,
        filterTags: Boolean,
        tagIds: List<String>,
        search: String?,
        limit: Int,
        offset: Int,
    ): Flow<List<TransactionWithTags>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRows(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTagLinks(links: List<TransactionTagEntity>)

    @Query("DELETE FROM transaction_tags WHERE transactionId IN (:ids)")
    suspend fun deleteTagLinks(ids: List<String>)

    /** Writes the rows and exactly the tags each now wears: what they wore before is dropped, so a removed tag does not linger. */
    @Transaction
    suspend fun upsert(transactions: List<TransactionEntity>, links: List<TransactionTagEntity>) {
        transactions.map { it.id }.chunked(ID_CHUNK).forEach { deleteTagLinks(it) }
        insertRows(transactions)
        insertTagLinks(links)
    }

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteRow(id: String)

    @Query("DELETE FROM transaction_tags WHERE transactionId = :id")
    suspend fun deleteTagLinksOf(id: String)

    @Transaction
    suspend fun deleteById(id: String) {
        deleteTagLinksOf(id)
        deleteRow(id)
    }

    @Query("DELETE FROM transaction_tags WHERE transactionId IN (SELECT id FROM transactions WHERE transferId = :transferId)")
    suspend fun deleteTagLinksOfTransfer(transferId: String)

    @Query("DELETE FROM transactions WHERE transferId = :transferId")
    suspend fun deleteRowsByTransferId(transferId: String)

    @Transaction
    suspend fun deleteByTransferId(transferId: String) {
        deleteTagLinksOfTransfer(transferId)
        deleteRowsByTransferId(transferId)
    }

    @Query("DELETE FROM transaction_tags WHERE transactionId IN (SELECT id FROM transactions WHERE accountId = :accountId)")
    suspend fun deleteTagLinksOfAccount(accountId: String)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countByAccountId(accountId: String): Int

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteRowsByAccountId(accountId: String)

    /** Deleting an account takes its transactions with it, which is what the API's `includeTransactions` confirms. */
    @Transaction
    suspend fun deleteByAccountId(accountId: String) {
        deleteTagLinksOfAccount(accountId)
        deleteRowsByAccountId(accountId)
    }

    /** Reading one back, for an edit that has to show before it is sent. */
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun byId(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transferId = :transferId ORDER BY amount ASC")
    suspend fun byTransferId(transferId: String): List<TransactionEntity>

    @Query("DELETE FROM transactions")
    suspend fun clearRows()

    @Query("DELETE FROM transaction_tags")
    suspend fun clearTagLinks()

    @Transaction
    suspend fun clear() {
        clearTagLinks()
        clearRows()
    }

    /** Swaps the cache for these rows in one transaction, so an observer sees the old list and then the new one, never an empty one between. */
    @Transaction
    suspend fun replaceAll(transactions: List<TransactionEntity>, links: List<TransactionTagEntity>) {
        clear()
        insertRows(transactions)
        insertTagLinks(links)
    }

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<TransactionEntity>

    @Query("UPDATE accounts SET balance = balance + :delta WHERE id = :accountId")
    suspend fun shiftBalance(accountId: String, delta: Long)

    /**
     * A save made on this device: the rows as [upsert] writes them, and every account they touch moved by
     * what changed on it, so a balance and net worth read right before the server has answered. The
     * server's balances replace these once the change has landed. Never for rows the API answered with:
     * those balances are already the server's.
     */
    @Transaction
    suspend fun saveLocal(transactions: List<TransactionEntity>, links: List<TransactionTagEntity>) {
        val before = transactions.map { it.id }.chunked(ID_CHUNK).flatMap { byIds(it) }
        upsert(transactions, links)
        balanceShifts(before, transactions).forEach { (accountId, delta) -> shiftBalance(accountId, delta) }
    }

    /** Deletes a transaction made or shown here — both legs, when it is one side of a transfer — and moves the balances back. */
    @Transaction
    suspend fun deleteLocal(id: String, transferId: String?) {
        val rows = if (transferId != null) byTransferId(transferId) else listOfNotNull(byId(id))
        if (transferId != null) deleteByTransferId(transferId) else deleteById(id)
        balanceShifts(rows, emptyList()).forEach { (accountId, delta) -> shiftBalance(accountId, delta) }
    }
}

/** How far each account moves when [before] is replaced by [after]: an account's balance is its starting balance plus its rows. */
internal fun balanceShifts(before: List<TransactionEntity>, after: List<TransactionEntity>): Map<String, Long> {
    val shifts = mutableMapOf<String, Long>()
    before.forEach { shifts[it.accountId] = (shifts[it.accountId] ?: 0L) - it.amount }
    after.forEach { shifts[it.accountId] = (shifts[it.accountId] ?: 0L) + it.amount }
    return shifts.filterValues { it != 0L }
}
