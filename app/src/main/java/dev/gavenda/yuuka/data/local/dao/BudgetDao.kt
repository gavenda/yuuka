package dev.gavenda.yuuka.data.local.dao

import androidx.room.*
import dev.gavenda.yuuka.data.local.entity.BudgetEntity
import dev.gavenda.yuuka.data.local.entity.IncomePlanEntity
import dev.gavenda.yuuka.data.local.entity.SummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    /** A category carries one plan per queried month, so setting it again replaces whatever was there. */
    @Query("SELECT * FROM budgets WHERE queriedMonth = :month AND categoryId = :categoryId")
    suspend fun forCategory(month: String, categoryId: String): BudgetEntity?

    @Query("DELETE FROM budgets")
    suspend fun clear()

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface IncomePlanDao {
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
