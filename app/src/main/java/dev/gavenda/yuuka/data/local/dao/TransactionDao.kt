package dev.gavenda.yuuka.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT tagId FROM transaction_tags WHERE transactionId = :id")
    suspend fun tagIdsOf(id: String): List<String>

    @Query("DELETE FROM transactions")
    suspend fun clearRows()

    @Query("DELETE FROM transaction_tags")
    suspend fun clearTagLinks()

    @Transaction
    suspend fun clear() {
        clearTagLinks()
        clearRows()
    }
}
