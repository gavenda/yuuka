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
     * `categoryNone` selects the "uncategorised" filter; when it is false,
     * `categoryId` (possibly null, for "all categories") is used instead. See
     * [dev.gavenda.yuuka.repository.TransactionRepository] for how the two are set.
     *
     * A search also matches the names of a transaction's tags, as the API's does.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:month IS NULL OR occurredOn LIKE (:month || '%'))
          AND (:accountId IS NULL OR accountId = :accountId)
          AND (:categoryNone = 0 OR categoryId IS NULL)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
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
        accountId: String?,
        categoryId: String?,
        categoryNone: Boolean,
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
