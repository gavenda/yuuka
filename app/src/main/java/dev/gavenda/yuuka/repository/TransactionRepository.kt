package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.local.dao.TransactionDao
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionFilters
import dev.gavenda.yuuka.data.model.TransactionPage
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Mirrors the web app's `transactions` Pinia store (`src/stores/transactions.ts`), backed by Room instead of in-memory state. */
class TransactionRepository(
    private val api: YuukaApi,
    private val dao: TransactionDao,
) {
    /** The cached page for these filters — populated by [refreshPage], observed live thereafter. */
    fun observePage(filters: TransactionFilters, limit: Int, offset: Int = 0): Flow<List<Transaction>> {
        val categoryNone = filters.categoryId == "none"
        val categoryId = if (categoryNone) null else filters.categoryId

        return dao.observePage(
            month = filters.month,
            accountId = filters.accountId,
            categoryId = categoryId,
            categoryNone = categoryNone,
            search = filters.search?.takeIf { it.isNotBlank() },
            limit = limit,
            offset = offset,
        ).map { list -> list.map { it.toDomain() } }
    }

    /** Fetches a page from the network and folds it into the local cache; returns the API's own total for pagination. */
    suspend fun refreshPage(filters: TransactionFilters, limit: Int, offset: Int): TransactionPage {
        val page = apiCall {
            api.listTransactions(
                month = filters.month,
                accountId = filters.accountId,
                categoryId = filters.categoryId,
                search = filters.search?.takeIf { it.isNotBlank() },
                limit = limit,
                offset = offset,
            )
        }
        dao.insertAll(page.transactions.map { it.toEntity() })
        return page
    }

    suspend fun createTransaction(accountId: String, categoryId: String?, amount: Long, occurredOn: String, payee: String, notes: String) {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
        }
        val response = apiCall { api.createTransaction(body) }
        dao.insertAll(listOf(response.transaction.toEntity()))
    }

    suspend fun updateTransaction(id: String, accountId: String, categoryId: String?, amount: Long, occurredOn: String, payee: String, notes: String) {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
        }
        val response = apiCall { api.updateTransaction(id, body) }
        dao.insertAll(listOf(response.transaction.toEntity()))
    }

    suspend fun deleteTransaction(id: String) {
        apiCall { api.deleteTransaction(id) }
        dao.deleteById(id)
    }

    suspend fun createTransfer(fromAccountId: String, toAccountId: String, categoryId: String?, amount: Long, occurredOn: String, payee: String, notes: String) {
        val body = buildJsonObject {
            put("fromAccountId", fromAccountId)
            put("toAccountId", toAccountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
        }
        val response = apiCall { api.createTransfer(body) }
        dao.insertAll(response.transactions.map { it.toEntity() })
    }

    suspend fun updateTransfer(
        transferId: String,
        fromAccountId: String,
        toAccountId: String,
        categoryId: String?,
        amount: Long,
        occurredOn: String,
        payee: String,
        notes: String,
    ) {
        val body = buildJsonObject {
            put("fromAccountId", fromAccountId)
            put("toAccountId", toAccountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
        }
        val response = apiCall { api.updateTransfer(transferId, body) }
        dao.insertAll(response.transactions.map { it.toEntity() })
    }

    /** Deletes both legs of a transfer, or a single plain transaction — the caller knows which by whether `transferId` was set. */
    suspend fun deleteTransactionOrTransfer(transaction: Transaction) {
        apiCall { api.deleteTransaction(transaction.id) }
        val transferId = transaction.transferId
        if (transferId != null) dao.deleteByTransferId(transferId) else dao.deleteById(transaction.id)
    }
}
