package dev.gavenda.yuuka.data.local.dao

import androidx.room.*
import dev.gavenda.yuuka.data.local.entity.PayeeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PayeeDao {
    @Query("SELECT * FROM payees ORDER BY usedCount DESC, lastUsedAt DESC")
    fun observeAll(): Flow<List<PayeeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(payees: List<PayeeEntity>)

    @Query("DELETE FROM payees")
    suspend fun clear()

    @Query("DELETE FROM payees WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun replaceAll(payees: List<PayeeEntity>) {
        clear()
        insertAll(payees)
    }
}
