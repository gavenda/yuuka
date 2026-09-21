package dev.gavenda.yuuka.sync

import dev.gavenda.yuuka.data.local.dao.OutboxDao
import dev.gavenda.yuuka.data.local.entity.OutboxEntity
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.data.remote.dto.SyncBatchRequest
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import dev.gavenda.yuuka.data.remote.apiJson
import dev.gavenda.yuuka.data.remote.dto.SyncOperationDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import java.time.Instant

/**
 * Local-first writes, and the batch that drains them.
 *
 * Every mutation in the app goes through [enqueue], and [enqueue] never waits
 * for the network. It writes the change to the outbox and returns; the caller
 * has already written the row it drew into Room, so the screen — which observes
 * Room, not the network — updates at once, with or without a connection.
 *
 * The queue drains separately: right away when [enqueue] is called, when the
 * network comes back ([NetworkMonitor]), and from [SyncWorker] under a
 * connectivity constraint so a queue built offline is still sent if the app is
 * never reopened. It drains as one `POST /api/sync/batch`, in the order the
 * user made the changes, which is what makes "spend from the account I just
 * made" work after an hour with no signal.
 *
 * Three things follow from writing first and sending later:
 *
 * - **The row written locally is a guess, and the server's is the truth.** Once
 *   a batch lands, [synced] names the slices it touched and the repositories
 *   refetch them, so any figure worked out approximately here — a balance, a
 *   running total — is corrected by the only party that can be sure.
 * - **A rejection arrives late.** A name the server considers a duplicate is
 *   only known to be one when the batch lands, which may be a day later. That
 *   is surfaced through [rejections] and the optimistic row is dropped by the
 *   refresh, rather than pretending the save is still in question.
 * - **Sending twice is better than losing one.** A row leaves the queue only
 *   once the server has accounted for it, so a flush that never got an answer
 *   sends again. The API is built for that.
 */
class Outbox(
    private val api: YuukaApi,
    private val dao: OutboxDao,
) {
    /** Given up on after this many failed sends, so one impossible change cannot block the queue forever. */
    private val maxAttempts = 8

    /** A batch this size already covers a long time offline; the rest goes in the next one. */
    private val batchSize = 200

    private val flushLock = Mutex()

    /** Outlives any screen, so a save made just before leaving one is still sent. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _syncing = MutableStateFlow(false)
    private val _synced = MutableSharedFlow<List<String>>(extraBufferCapacity = 8)
    private val _rejections = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** How many changes have not reached the API. Zero is "everything is saved". */
    val unsentCount: Flow<Int> = dao.observeCount()

    /** Whether a batch is in flight, for a screen to say "syncing". */
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Emits the slices a landed batch touched, so the repositories holding them refetch. */
    val synced: SharedFlow<List<String>> = _synced.asSharedFlow()

    /** Emits what the server refused, for a screen to surface — the user is told late, but told. */
    val rejections: SharedFlow<String> = _rejections.asSharedFlow()

    /**
     * Queues a mutation. Returns as soon as it is written down.
     *
     * `at` is stamped here, when the user made the change, rather than when it
     * is sent: that is what the server judges a stale edit against, and a change
     * made on a plane should lose to one made on the ground an hour later, not
     * win by arriving second.
     *
     * [entity] and [rowId] are what the server checks that against, so an edit
     * or a delete should name the row it touches. A create names nothing —
     * there is no row for it to be stale against.
     */
    suspend fun enqueue(
        method: String,
        path: String,
        body: JsonElement? = null,
        entity: String? = null,
        rowId: String? = null,
    ) {
        dao.add(
            OutboxEntity(
                opId = java.util.UUID.randomUUID().toString(),
                method = method,
                path = path,
                at = Instant.now().toString(),
                entity = entity,
                rowId = rowId,
                body = body?.let { apiJson.encodeToString(JsonElement.serializer(), it) },
                slices = Slices.forPath(path).joinToString(","),
            ),
        )

        // Sent in the background: the caller is told the change is saved the moment it is written down.
        scope.launch { runCatching { flush() } }
    }

    /**
     * Sends everything waiting, as one batch.
     *
     * Only one runs at a time — a second call while one is in flight waits for
     * it rather than sending the same rows again. Nothing here decides on
     * whether the device thinks it is online: a flush is always attempted, and
     * an unreachable API simply leaves the queue as it was.
     *
     * Returns false when there is still work that did not get through, which is
     * what [SyncWorker] retries on.
     */
    suspend fun flush(): Boolean = flushLock.withLock {
        val entries = dao.oldest(batchSize)
        if (entries.isEmpty()) return@withLock true

        _syncing.value = true
        try {
            val response = try {
                apiCall {
                    api.syncBatch(
                        SyncBatchRequest(
                            entries.map { entry ->
                                SyncOperationDto(
                                    opId = entry.opId,
                                    method = entry.method,
                                    path = entry.path,
                                    at = entry.at,
                                    entity = entry.entity,
                                    id = entry.rowId,
                                    body = entry.body?.let { apiJson.parseToJsonElement(it) },
                                )
                            },
                        ),
                    )
                }
            } catch (error: ApiError) {
                // Still offline, or the session expired. The queue is untouched
                // and will be sent again; the only thing recorded is that the
                // attempt happened, so a row the server will never accept is
                // eventually given up on.
                dao.recordFailure(entries.map { it.seq }, error.message)
                return@withLock false
            }

            val bySeq = entries.associateBy { it.opId }
            val settled = mutableListOf<Long>()
            val retry = mutableListOf<Long>()
            val touched = linkedSetOf<String>()
            val refused = mutableListOf<String>()

            for (result in response.results) {
                val entry = bySeq[result.opId] ?: continue

                when {
                    result.status == "applied" || result.status == "stale" -> {
                        settled += entry.seq
                        touched += entry.slices.split(',').filter { it.isNotBlank() }
                    }
                    // The server answered, and said no. Retrying a 4xx would only
                    // be told the same thing, so it is given up on at once — the
                    // attempt count is for a batch that never got through.
                    result.code in 400..499 || entry.attempts + 1 >= maxAttempts -> {
                        settled += entry.seq
                        touched += entry.slices.split(',').filter { it.isNotBlank() }
                        result.error?.let { refused += it }
                    }
                    else -> retry += entry.seq
                }
            }

            if (settled.isNotEmpty()) dao.remove(settled)
            if (retry.isNotEmpty()) dao.recordFailure(retry, "Retrying.")

            // The provisional rows are replaced by what the server actually has,
            // which is also what takes a rejected change back off the screen.
            if (touched.isNotEmpty()) _synced.tryEmit(touched.toList())
            for (message in refused) _rejections.tryEmit(message)

            retry.isEmpty() && dao.count() == 0
        } finally {
            _syncing.value = false
        }
    }

    /** Forgets the unsent work. Signing out — it was written against a session that is over. */
    suspend fun discard() = dao.clear()
}
