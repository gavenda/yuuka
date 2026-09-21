package dev.gavenda.yuuka.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A change that has not reached the API yet.
 *
 * This is the one table in the database that is **not** a cache. Everything
 * else here is a copy of what the server holds and can be dropped and refilled
 * — which is exactly what `fallbackToDestructiveMigration` does on a schema
 * change. A row here is a change the user made that exists nowhere else yet, so
 * losing it loses their work.
 *
 * It holds the call as it would have been made online: a method, an `/api/…`
 * path and a JSON body, which `POST /api/sync/batch` replays through the same
 * routes an online client would have reached. Nothing about the queue knows
 * what a transaction is.
 *
 * [seq] is the order the user made the changes in, and the order they are sent
 * in: creating an account and then spending from it are two rows, and the
 * second only works after the first.
 */
@Entity(tableName = "outbox")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    /** This client's own name for the operation, echoed back in the batch's results. */
    val opId: String,
    val method: String,
    val path: String,
    /**
     * When the user made the change, not when it is sent. The server judges a
     * stale edit against this, so a change made on a plane loses to one made on
     * the ground an hour later rather than winning by arriving second.
     */
    val at: String,
    /** What it touches, for the server's staleness check. Null means "apply unconditionally". */
    val entity: String?,
    val rowId: String?,
    /** The request body, already serialised. Null for a DELETE. */
    val body: String?,
    /** Which slices to refresh once this lands or fails, comma-separated. */
    val slices: String,
    /** How many times sending this has been attempted, so a permanently broken row can be given up on. */
    val attempts: Int = 0,
    /** Why the last attempt failed, kept so a screen can say what went wrong. */
    val lastError: String? = null,
)
