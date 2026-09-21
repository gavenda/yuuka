package dev.gavenda.yuuka.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.gavenda.yuuka.data.local.entity.OutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * The unsent queue. Always read in [OutboxEntity.seq] order — that is the order
 * the user made the changes in, and replaying them out of order would have a
 * transaction arrive before the account it names.
 *
 * Note the absence of a `clear()` that anything but signing out calls: a row
 * leaves here only once the server has accounted for it.
 */
@Dao
interface OutboxDao {
    @Insert
    suspend fun add(entry: OutboxEntity): Long

    @Query("SELECT * FROM outbox ORDER BY seq ASC LIMIT :limit")
    suspend fun oldest(limit: Int): List<OutboxEntity>

    /** How many changes have not reached the API. Observed so a screen can say "3 unsynced". */
    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    @Query("DELETE FROM outbox WHERE seq IN (:sequences)")
    suspend fun remove(sequences: List<Long>)

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE seq IN (:sequences)")
    suspend fun recordFailure(sequences: List<Long>, error: String?)

    /** Signing out, or someone else signing in — the unsent work is not theirs. */
    @Query("DELETE FROM outbox")
    suspend fun clear()
}
