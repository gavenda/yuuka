package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.local.dao.TransactionDao
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.tagLinks
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionFilters
import dev.gavenda.yuuka.data.model.TransactionPage
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Mirrors the web app's `transactions` Pinia store (`src/stores/transactions.ts`), backed by Room instead of in-memory state. */
class TransactionRepository(
    private val api: YuukaApi,
    private val dao: TransactionDao,
) {
    /** Rows and the tags they wear go in together, so a chip never outlives its transaction or the reverse. */
    private suspend fun store(transactions: List<Transaction>) = dao.upsert(transactions.map { it.toEntity() }, transactions.flatMap { it.tagLinks() })

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
        store(page.transactions)
        return page
    }

    /** Returns the "Save the Change" round-up this create triggered, if any — its destination-account leg, ready to surface as feedback. */
    suspend fun createTransaction(
        accountId: String,
        categoryId: String?,
        amount: Long,
        occurredOn: String,
        payee: String,
        notes: String,
        tagIds: List<String>,
    ): Transaction? {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
            putTagIds(tagIds)
        }
        val response = apiCall { api.createTransaction(body) }
        store(listOfNotNull(response.transaction, response.roundUp))
        return response.roundUp
    }

    suspend fun updateTransaction(
        id: String,
        accountId: String,
        categoryId: String?,
        amount: Long,
        occurredOn: String,
        payee: String,
        notes: String,
        tagIds: List<String>,
    ) {
        val body = buildJsonObject {
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
            putTagIds(tagIds)
        }
        val response = apiCall { api.updateTransaction(id, body) }
        store(listOf(response.transaction))
    }

    /**
     * Posts the difference between an account's current balance and [balance] as its own
     * transaction, computed by the API from the balance it holds at write time rather than
     * whatever this call happened to observe. Throws [dev.gavenda.yuuka.data.remote.ApiError]
     * with status 400 when the account is already at that balance.
     */
    suspend fun adjustAccountBalance(accountId: String, balance: Long, occurredOn: String, payee: String, notes: String) {
        val body = buildJsonObject {
            put("balance", balance)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
        }
        val response = apiCall { api.adjustAccountBalance(accountId, body) }
        store(listOf(response.transaction))
    }

    suspend fun deleteTransaction(id: String) {
        apiCall { api.deleteTransaction(id) }
        dao.deleteById(id)
    }

    suspend fun createTransfer(
        fromAccountId: String,
        toAccountId: String,
        categoryId: String?,
        amount: Long,
        occurredOn: String,
        payee: String,
        notes: String,
        tagIds: List<String>,
    ) {
        val body = buildJsonObject {
            put("fromAccountId", fromAccountId)
            put("toAccountId", toAccountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
            putTagIds(tagIds)
        }
        val response = apiCall { api.createTransfer(body) }
        store(response.transactions)
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
        tagIds: List<String>,
    ) {
        val body = buildJsonObject {
            put("fromAccountId", fromAccountId)
            put("toAccountId", toAccountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
            putTagIds(tagIds)
        }
        val response = apiCall { api.updateTransfer(transferId, body) }
        store(response.transactions)
    }

    /** Deletes both legs of a transfer, or a single plain transaction — the caller knows which by whether `transferId` was set. */
    suspend fun deleteTransactionOrTransfer(transaction: Transaction) {
        apiCall { api.deleteTransaction(transaction.id) }
        val transferId = transaction.transferId
        if (transferId != null) dao.deleteByTransferId(transferId) else dao.deleteById(transaction.id)
    }
}

/** Sent whole on every save: the API replaces a transaction's tags with exactly this set. */
private fun JsonObjectBuilder.putTagIds(tagIds: List<String>) {
    put("tagIds", buildJsonArray { tagIds.forEach { add(it) } })
}
