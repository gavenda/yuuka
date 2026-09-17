package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.local.dao.PayeeDao
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Remembered payees for the "what did I file this under last time" autosuggest — mirrors `PayeeInput.vue`. */
class PayeeRepository(
    private val api: YuukaApi,
    private val dao: PayeeDao,
) {
    val payees: Flow<List<Payee>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** The whole history is fetched at once and ranked client-side (see [dev.gavenda.yuuka.domain.rankPayees]) — a personal ledger has tens or hundreds of payees, not thousands. */
    suspend fun refresh(limit: Int = 50) {
        val response = apiCall { api.listPayees(limit = limit) }
        dao.replaceAll(response.payees.map { it.toEntity() })
    }

    suspend fun forget(id: String) {
        apiCall { api.forgetPayee(id) }
        dao.deleteById(id)
    }
}
