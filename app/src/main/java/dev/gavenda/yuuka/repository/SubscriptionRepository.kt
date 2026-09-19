package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.local.dao.SubscriptionDao
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Subscription
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Monthly charges the Worker posts on the user's behalf — mirrors the web app's `subscriptions`
 * Pinia store. This only manages the schedule: the transactions themselves are posted server-side
 * by a cron at 00:00 UTC and reach the app through the ordinary transaction loads.
 */
class SubscriptionRepository(
    private val api: YuukaApi,
    private val dao: SubscriptionDao,
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
        apiCall { api.createSubscription(body) }
        refresh()
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
        apiCall { api.updateSubscription(id, body) }
        refresh()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        apiCall { api.updateSubscription(id, buildJsonObject { put("enabled", enabled) }) }
        refresh()
    }

    suspend fun delete(id: String) {
        apiCall { api.deleteSubscription(id) }
        refresh()
    }
}
