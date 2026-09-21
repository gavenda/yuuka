package dev.gavenda.yuuka.data.local.dao

import androidx.room.*
import dev.gavenda.yuuka.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    /** The soonest first, with paused ones after — the same order the API answers in. */
    @Query("SELECT * FROM subscriptions ORDER BY enabled DESC, nextRunOn ASC, payee COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subscriptions: List<SubscriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity)

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun byId(id: String): SubscriptionEntity?

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM subscriptions")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(subscriptions: List<SubscriptionEntity>) {
        clear()
        insertAll(subscriptions)
    }
}
