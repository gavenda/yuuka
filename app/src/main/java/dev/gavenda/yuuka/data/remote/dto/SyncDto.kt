package dev.gavenda.yuuka.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * `POST /api/sync/batch`: where the outbox drains.
 *
 * An operation is the call the app would have made online — a method, an
 * `/api/…` path and a body — and the server replays it through the same routes
 * an online client would have reached. Nothing in this shape knows what a
 * transaction is, which is what keeps the two paths from drifting.
 */
@Serializable
data class SyncOperationDto(
    val opId: String,
    val method: String,
    val path: String,
    /** When the user made the change, not when it is sent; the server judges a stale edit against it. */
    val at: String,
    /** What it touches, for the staleness check. Null applies the operation unconditionally. */
    val entity: String? = null,
    val id: String? = null,
    val body: JsonElement? = null,
)

@Serializable
data class SyncBatchRequest(val operations: List<SyncOperationDto>)

/**
 * What became of one operation.
 *
 * `applied` landed; `stale` was dropped because a newer change to the same row
 * reached the server first; `failed` was refused. All three are final as far as
 * the queue is concerned — only a batch that never got an answer at all is sent
 * again.
 */
@Serializable
data class SyncResultDto(
    val opId: String,
    val status: String,
    val code: Int,
    val error: String? = null,
)

@Serializable
data class SyncBatchResponse(val results: List<SyncResultDto>)

@Serializable
data class DeviceRegistrationRequest(
    val token: String,
    val deviceId: String,
    val platform: String = "android",
)
