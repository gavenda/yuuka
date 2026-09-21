package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.Ids
import dev.gavenda.yuuka.data.local.Provisional
import dev.gavenda.yuuka.data.local.dao.AccountDao
import dev.gavenda.yuuka.data.local.dao.CategoryDao
import dev.gavenda.yuuka.data.local.dao.SubscriptionDao
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Subscription
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import dev.gavenda.yuuka.sync.Outbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Monthly charges the Worker posts on the user's behalf — mirrors the web app's `subscriptions`
 * Pinia store. This only manages the schedule: the transactions themselves are posted server-side
 * by a cron at 00:00 UTC and reach the app through the ordinary transaction loads.
 */
class SubscriptionRepository(
    private val api: YuukaApi,
    private val outbox: Outbox,
    private val dao: SubscriptionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
) {
    val subscriptions: Flow<List<Subscription>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun refresh() {
        val response = apiCall { api.listSubscriptions() }
        dao.replaceAll(response.subscriptions.map { it.toEntity() })
    }

    suspend fun create(accountId: String, categoryId: String?, amount: Long, payee: String, notes: String, startOn: String) {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("payee", payee)
            put("notes", notes)
            put("startOn", startOn)
        }
        val id = Ids.new(Ids.SUBSCRIPTION)
        dao.upsert(
            Provisional.subscription(
                id = id,
                accountId = accountId,
                account = accountDao.byId(accountId),
                category = categoryId?.let { categoryDao.byId(it) },
                amount = amount,
                payee = payee,
                notes = notes,
                startOn = startOn,
            ),
        )
        outbox.enqueue("POST", "/api/subscriptions", JsonObject(body + ("id" to JsonPrimitive(id))))
    }

    /**
     * [startOn] restarts the schedule from that date, so it is only sent when the user changed it —
     * leaving it null keeps the schedule exactly as it is.
     */
    suspend fun update(id: String, accountId: String, categoryId: String?, amount: Long, payee: String, notes: String, startOn: String?) {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("payee", payee)
            put("notes", notes)
            startOn?.let { put("startOn", it) }
        }
        dao.byId(id)?.let { current ->
            dao.upsert(
                current.copy(
                    accountId = accountId,
                    accountName = accountDao.byId(accountId)?.name,
                    categoryId = categoryId,
                    categoryName = categoryId?.let { categoryDao.byId(it)?.name },
                    categoryColor = categoryId?.let { categoryDao.byId(it)?.color },
                    amount = amount,
                    payee = payee,
                    notes = notes,
                    // Restarting the schedule moves the anchor and the next run
                    // with it, the way the API does.
                    startOn = startOn ?: current.startOn,
                    dayOfMonth = startOn?.substring(8, 10)?.toInt() ?: current.dayOfMonth,
                    nextRunOn = startOn ?: current.nextRunOn,
                    updatedAt = Provisional.touchedAt(),
                ),
            )
        }
        outbox.enqueue("PATCH", "/api/subscriptions/$id", body, entity = "subscription", rowId = id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.byId(id)?.let { dao.upsert(it.copy(enabled = enabled, updatedAt = Provisional.touchedAt())) }
        outbox.enqueue("PATCH", "/api/subscriptions/$id", buildJsonObject { put("enabled", enabled) }, entity = "subscription", rowId = id)
    }

    /** Everything it already posted stays: those are ordinary transactions, and this is only its schedule. */
    suspend fun delete(id: String) {
        dao.deleteById(id)
        outbox.enqueue("DELETE", "/api/subscriptions/$id", entity = "subscription", rowId = id)
    }
}
