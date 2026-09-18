package dev.gavenda.yuuka.data.local.dao

import androidx.room.*
import dev.gavenda.yuuka.data.local.entity.BudgetEntity
import dev.gavenda.yuuka.data.local.entity.IncomePlanEntity
import dev.gavenda.yuuka.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE queriedMonth = :month")
    fun observeForMonth(month: String): Flow<List<BudgetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets WHERE queriedMonth = :month")
    suspend fun clearMonth(month: String)

    @Query("DELETE FROM budgets")
    suspend fun clear()

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)

    @Transaction
    suspend fun replaceMonth(month: String, budgets: List<BudgetEntity>) {
        clearMonth(month)
        insertAll(budgets)
    }
}

@Dao
interface IncomePlanDao {
    @Query("SELECT * FROM income_plans WHERE queriedMonth = :month")
    fun observe(month: String): Flow<IncomePlanEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: IncomePlanEntity)

    @Query("DELETE FROM income_plans")
    suspend fun clear()
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries WHERE month = :month")
    fun observe(month: String): Flow<SummaryEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(summary: SummaryEntity)

    @Query("DELETE FROM summaries")
    suspend fun clear()
}
