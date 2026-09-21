package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.Ids
import dev.gavenda.yuuka.data.local.Provisional
import dev.gavenda.yuuka.data.local.dao.AccountDao
import dev.gavenda.yuuka.data.local.dao.CategoryDao
import dev.gavenda.yuuka.data.local.dao.RoundUpRuleDao
import dev.gavenda.yuuka.data.local.dao.TagDao
import dev.gavenda.yuuka.data.local.dao.TransactionDao
import dev.gavenda.yuuka.data.local.entity.TransactionEntity
import dev.gavenda.yuuka.data.local.entity.TransactionTagEntity
import dev.gavenda.yuuka.data.local.toDomain
import dev.gavenda.yuuka.data.local.tagLinks
import dev.gavenda.yuuka.data.local.toEntity
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionFilters
import dev.gavenda.yuuka.data.model.UNCATEGORIZED_FILTER_ID
import dev.gavenda.yuuka.data.model.TransactionPage
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import dev.gavenda.yuuka.sync.Outbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The API's largest page, so a resync makes as few round trips as it can. */
private const val RESYNC_PAGE = 200

/**
 * Mirrors the web app's `transactions` Pinia store (`src/stores/transactions.ts`), backed by Room.
 *
 * Writes are local-first: the rows the user just made are written into Room and
 * the call is handed to the [Outbox], so the list updates at once with or
 * without a connection. Two things that are the server's work online have to be
 * done here as well for that to look right:
 *
 * - **A round-up.** A purchase on an opted-in account moves money into another
 *   account, and an offline purchase that did not show it would leave both
 *   balances wrong. It is worked out with the same arithmetic the API uses
 *   ([Provisional.roundUpFor]), and the three rows it writes are named here so
 *   the API adopts those names rather than posting a second copy.
 * - **A balance adjustment's amount.** The API is told the balance the account
 *   should read and computes the difference against the balance at the instant
 *   it writes. Here the only balance available is the cached one — so if a
 *   transaction lands in between, the server's figure is the one that survives
 *   the refresh.
 */
class TransactionRepository(
    private val api: YuukaApi,
    private val outbox: Outbox,
    private val dao: TransactionDao,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val tagDao: TagDao,
    private val roundUpRuleDao: RoundUpRuleDao,
) {
    /** Rows and the tags they wear go in together, so a chip never outlives its transaction or the reverse. */
    private suspend fun store(transactions: List<Transaction>) = dao.upsert(transactions.map { it.toEntity() }, transactions.flatMap { it.tagLinks() })

    /** The same, for rows built here rather than returned by the API. */
    private suspend fun storeLocal(rows: List<TransactionEntity>, tagIds: List<String>) =
        dao.upsert(rows, rows.flatMap { row -> tagIds.map { TransactionTagEntity(row.id, it) } })

    /** The cached page for these filters — populated by [refreshPage], observed live thereafter. */
    fun observePage(filters: TransactionFilters, limit: Int, offset: Int = 0): Flow<List<Transaction>> {
        return dao.observePage(
            month = filters.month,
            filterAccounts = filters.accountIds.isNotEmpty(),
            accountIds = filters.accountIds.toList(),
            filterCategories = filters.categoryIds.isNotEmpty(),
            categoryIds = (filters.categoryIds - UNCATEGORIZED_FILTER_ID).toList(),
            categoryNone = UNCATEGORIZED_FILTER_ID in filters.categoryIds,
            filterTags = filters.tagIds.isNotEmpty(),
            tagIds = filters.tagIds.toList(),
            search = filters.search?.takeIf { it.isNotBlank() },
            limit = limit,
            offset = offset,
        ).map { list -> list.map { it.toDomain() } }
    }

    /**
     * Fetches a page from the network and folds it into the local cache; returns the API's own total for pagination.
     * Only the month and search are asked of the API: the account, category and tag filters are applied to the cache by [observePage].
     */
    suspend fun refreshPage(filters: TransactionFilters, limit: Int, offset: Int): TransactionPage {
        val page = apiCall {
            api.listTransactions(
                month = filters.month,
                search = filters.search?.takeIf { it.isNotBlank() },
                limit = limit,
                offset = offset,
            )
        }
        store(page.transactions)
        return page
    }

    /**
     * The refetch after a sync: pulls back as much as was on screen (everything, when a filter narrows the cache),
     * and only then replaces the cache with it in one step. Clearing first and refilling as the pages arrive is
     * what made the list empty out and flicker; here the last-seen rows stay up until the new ones are ready, and
     * a failed fetch leaves them alone. Returns the API's total and how many rows it now holds.
     */
    suspend fun resync(filters: TransactionFilters, loaded: Int): Pair<Int, Int> {
        val rows = mutableListOf<Transaction>()
        var total: Int
        do {
            val page = apiCall {
                api.listTransactions(
                    month = filters.month,
                    search = filters.search?.takeIf { it.isNotBlank() },
                    limit = RESYNC_PAGE,
                    offset = rows.size,
                )
            }
            rows += page.transactions
            total = page.total
        } while (page.transactions.isNotEmpty() && rows.size < total && (filters.narrowsCache || rows.size < loaded))
        dao.replaceAll(rows.map { it.toEntity() }, rows.flatMap { it.tagLinks() })
        return total to rows.size
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
        val id = Ids.new(Ids.TRANSACTION)
        val account = accountDao.byId(accountId)
        val category = categoryId?.let { categoryDao.byId(it) }

        val row = Provisional.transaction(id, accountId, account, category, amount, occurredOn, payee, notes)

        // The ids of the round-up this may trigger are decided here whether or
        // not it does, so the body is the same shape either way and the API
        // adopts these names if it agrees there is one.
        val roundUpIds = Triple(Ids.new(Ids.TRANSFER), Ids.new(Ids.TRANSACTION), Ids.new(Ids.TRANSACTION))
        val roundUp = buildRoundUp(row, roundUpIds)

        storeLocal(listOfNotNull(row, roundUp?.first, roundUp?.second), tagIds)
        // A round-up wears no tags; only the purchase does.
        if (roundUp != null) dao.deleteTagLinks(listOf(roundUp.first.id, roundUp.second.id))

        val body = buildJsonObject {
            put("id", id)
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
            putTagIds(tagIds)
            put(
                "roundUpIds",
                buildJsonObject {
                    put("transferId", roundUpIds.first)
                    put("fromId", roundUpIds.second)
                    put("toId", roundUpIds.third)
                },
            )
        }
        outbox.enqueue("POST", "/api/transactions", body)

        // The destination leg is what the interface surfaces as feedback. A
        // round-up wears no tags, so there are none to look up.
        return roundUp?.second?.toDomain(emptyList())
    }

    /**
     * The two legs "Save the Change" would post, or null when this purchase does
     * not trigger one. The same four conditions the API checks, in the same
     * order: the rule is on, it has a destination, the account opted in, and the
     * purchase was not made from the destination itself.
     */
    private suspend fun buildRoundUp(
        purchase: TransactionEntity,
        ids: Triple<String, String, String>,
    ): Pair<TransactionEntity, TransactionEntity>? {
        val rule = roundUpRuleDao.current() ?: return null
        val destinationId = rule.destinationAccountId ?: return null
        if (!rule.enabled) return null

        val source = accountDao.byId(purchase.accountId) ?: return null
        if (!source.roundUpSource || purchase.accountId == destinationId) return null

        val amount = Provisional.roundUpFor(purchase.amount, rule.roundTo)
        if (amount <= 0) return null

        // Both legs carry the rule's category — a round-up is a transfer, so it
        // takes one from the same Cashflow tree an ordinary transfer uses.
        val category = rule.categoryId?.let { categoryDao.byId(it) }
        val (transferId, fromId, toId) = ids

        return Provisional.transaction(
            id = fromId,
            accountId = purchase.accountId,
            account = source,
            category = category,
            amount = -amount,
            occurredOn = purchase.occurredOn,
            payee = Provisional.ROUND_UP_PAYEE,
            notes = "",
            transferId = transferId,
        ) to Provisional.transaction(
            id = toId,
            accountId = destinationId,
            account = accountDao.byId(destinationId),
            category = category,
            amount = amount,
            occurredOn = purchase.occurredOn,
            payee = Provisional.ROUND_UP_PAYEE,
            notes = "",
            transferId = transferId,
        )
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
        val existing = dao.byId(id)
        storeLocal(
            listOf(
                Provisional.transaction(
                    id = id,
                    accountId = accountId,
                    account = accountDao.byId(accountId),
                    category = categoryId?.let { categoryDao.byId(it) },
                    amount = amount,
                    occurredOn = occurredOn,
                    payee = payee,
                    notes = notes,
                    transferId = existing?.transferId,
                ).copy(automated = existing?.automated ?: false, createdAt = existing?.createdAt ?: Provisional.touchedAt()),
            ),
            tagIds,
        )

        val body = buildJsonObject {
            put("accountId", accountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
            putTagIds(tagIds)
        }
        outbox.enqueue("PATCH", "/api/transactions/$id", body, entity = "transaction", rowId = id)
    }

    /**
     * Posts the difference between an account's current balance and [balance] as
     * its own transaction. Online the API computes that difference against the
     * balance it holds at write time; queued, it is computed against the cached
     * one and corrected when the batch lands.
     */
    suspend fun adjustAccountBalance(accountId: String, balance: Long, occurredOn: String, payee: String, notes: String) {
        val account = accountDao.byId(accountId)
        val difference = balance - (account?.balance ?: 0L)
        val id = Ids.new(Ids.TRANSACTION)

        // Nothing distinguishes the row from one entered by hand, because that
        // is what it is: an ordinary, uncategorised transaction.
        storeLocal(
            listOf(
                Provisional.transaction(
                    id = id,
                    accountId = accountId,
                    account = account,
                    category = null,
                    amount = difference,
                    occurredOn = occurredOn,
                    payee = payee.ifBlank { "Balance adjustment" },
                    notes = notes,
                ),
            ),
            emptyList(),
        )

        val body = buildJsonObject {
            put("id", id)
            put("balance", balance)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
        }
        outbox.enqueue("POST", "/api/accounts/$accountId/adjust", body)
    }

    suspend fun deleteTransaction(id: String) {
        dao.deleteById(id)
        outbox.enqueue("DELETE", "/api/transactions/$id", entity = "transaction", rowId = id)
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
        val transferId = Ids.new(Ids.TRANSFER)
        val fromId = Ids.new(Ids.TRANSACTION)
        val toId = Ids.new(Ids.TRANSACTION)

        storeLocal(legsFor(transferId, fromId, toId, fromAccountId, toAccountId, categoryId, amount, occurredOn, payee, notes), tagIds)

        val body = buildJsonObject {
            put(
                "ids",
                buildJsonObject {
                    put("transferId", transferId)
                    put("fromId", fromId)
                    put("toId", toId)
                },
            )
            put("fromAccountId", fromAccountId)
            put("toAccountId", toAccountId)
            put("categoryId", categoryId)
            put("amount", amount)
            put("occurredOn", occurredOn)
            put("payee", payee)
            put("notes", notes)
            putTagIds(tagIds)
        }
        outbox.enqueue("POST", "/api/transactions/transfer", body)
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
        // An edit keeps the legs it already has; only what they say changes.
        val existing = dao.byTransferId(transferId)
        val fromId = existing.firstOrNull()?.id ?: Ids.new(Ids.TRANSACTION)
        val toId = existing.getOrNull(1)?.id ?: Ids.new(Ids.TRANSACTION)

        storeLocal(legsFor(transferId, fromId, toId, fromAccountId, toAccountId, categoryId, amount, occurredOn, payee, notes), tagIds)

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
        outbox.enqueue("PATCH", "/api/transactions/transfer/$transferId", body, entity = "transfer", rowId = transferId)
    }

    /** One movement recorded twice: a negative leg and a positive one, sharing a transfer id and their tags. */
    private suspend fun legsFor(
        transferId: String,
        fromId: String,
        toId: String,
        fromAccountId: String,
        toAccountId: String,
        categoryId: String?,
        amount: Long,
        occurredOn: String,
        payee: String,
        notes: String,
    ): List<TransactionEntity> {
        val from = accountDao.byId(fromAccountId)
        val to = accountDao.byId(toAccountId)
        val category = categoryId?.let { categoryDao.byId(it) }
        // Blank names itself, exactly as the API composes it.
        val name = payee.ifBlank { Provisional.transferName(from, to) }

        return listOf(
            Provisional.transaction(fromId, fromAccountId, from, category, -amount, occurredOn, name, notes, transferId),
            Provisional.transaction(toId, toAccountId, to, category, amount, occurredOn, name, notes, transferId),
        )
    }

    /** Deletes both legs of a transfer, or a single plain transaction — the caller knows which by whether `transferId` was set. */
    suspend fun deleteTransactionOrTransfer(transaction: Transaction) {
        val transferId = transaction.transferId
        if (transferId != null) dao.deleteByTransferId(transferId) else dao.deleteById(transaction.id)

        outbox.enqueue("DELETE", "/api/transactions/${transaction.id}", entity = "transaction", rowId = transaction.id)
    }
}

/** Sent whole on every save: the API replaces a transaction's tags with exactly this set. */
private fun JsonObjectBuilder.putTagIds(tagIds: List<String>) {
    put("tagIds", buildJsonArray { tagIds.forEach { add(it) } })
}
