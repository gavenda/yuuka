package dev.gavenda.yuuka.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gavenda.yuuka.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    /**
     * `categoryNone` selects the "uncategorised" filter; when it is false,
     * `categoryId` (possibly null, for "all categories") is used instead. See
     * [dev.gavenda.yuuka.repository.TransactionRepository] for how the two are set.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:month IS NULL OR occurredOn LIKE (:month || '%'))
          AND (:accountId IS NULL OR accountId = :accountId)
          AND (:categoryNone = 0 OR categoryId IS NULL)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:search IS NULL OR payee LIKE ('%' || :search || '%') OR notes LIKE ('%' || :search || '%'))
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
    ): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transactions WHERE transferId = :transferId")
    suspend fun deleteByTransferId(transferId: String)

    @Query("DELETE FROM transactions")
    suspend fun clear()
}
