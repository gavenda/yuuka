package dev.gavenda.yuuka.repository

import dev.gavenda.yuuka.data.Ids
import dev.gavenda.yuuka.data.local.Provisional
import dev.gavenda.yuuka.data.local.dao.AccountDao
import dev.gavenda.yuuka.data.local.dao.InvestmentDao
import dev.gavenda.yuuka.data.local.dao.TransactionDao
import dev.gavenda.yuuka.data.local.entity.TradeEntity
import dev.gavenda.yuuka.data.local.entity.toDomain
import dev.gavenda.yuuka.data.local.entity.toEntity
import dev.gavenda.yuuka.data.model.PricePoint
import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.data.remote.YuukaApi
import dev.gavenda.yuuka.data.remote.apiCall
import dev.gavenda.yuuka.domain.tradeCash
import dev.gavenda.yuuka.sync.Outbox
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

/**
 * PSE investments. Prices come from the server, which keeps them from PHISIX; trades are written here first
 * and sent through the [Outbox], with their cash leg saved as an ordinary transaction so the broker account's
 * balance moves at once, as `POST /api/trades` will move it. Both are named here, so the server adopts them.
 */
class InvestmentRepository(
    private val api: YuukaApi,
    private val outbox: Outbox,
    private val dao: InvestmentDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao,
) {
    val securities: Flow<List<Security>> = dao.observeSecurities().map { list -> list.map { it.toDomain() } }
    val trades: Flow<List<Trade>> = dao.observeTrades().map { list -> list.map { it.toDomain() } }

    fun prices(symbol: String): Flow<List<PricePoint>> = dao.observePrices(symbol).map { list -> list.map { it.toDomain() } }

    suspend fun refresh() {
        val securities = apiCall { api.listSecurities() }.securities
        dao.replaceSecurities(securities.map { it.toEntity() })
        refreshTrades()
    }

    suspend fun refreshTrades() {
        val trades = apiCall { api.listTrades() }.trades
        dao.replaceTrades(trades.map { it.toEntity() })
    }

    /** A year of closes, folded into what is already held for the stock. */
    suspend fun refreshPrices(symbol: String) {
        val from = LocalDate.now().minusYears(1).toString()
        val prices = apiCall { api.prices(symbol, from) }.prices
        dao.insertPrices(prices.map { it.toEntity(symbol) })
    }

    suspend fun createTrade(accountId: String, symbol: String, kind: String, occurredOn: String, shares: Long, price: Long, fees: Long) {
        val id = Ids.new(Ids.TRADE)
        val transactionId = Ids.new(Ids.TRANSACTION)
        val amount = tradeCash(kind, shares, price, fees)
        val account = accountDao.byId(accountId)
        val now = Provisional.touchedAt()

        // The cash leg reads as the server writes it: the stock as the payee, what was done in the notes.
        val leg = Provisional.transaction(
            id = transactionId,
            accountId = accountId,
            account = account,
            category = null,
            amount = amount,
            occurredOn = occurredOn,
            payee = symbol,
            notes = "${kind.replaceFirstChar { it.uppercase() }} ${"%,d".format(shares)} $symbol",
        )
        transactionDao.saveLocal(listOf(leg), emptyList())
        dao.upsertTrade(
            TradeEntity(
                id = id,
                accountId = accountId,
                accountName = account?.name,
                symbol = symbol,
                name = dao.security(symbol)?.name,
                kind = kind,
                occurredOn = occurredOn,
                shares = shares,
                price = price,
                fees = fees,
                amount = amount,
                transactionId = transactionId,
                createdAt = now,
                updatedAt = now,
            ),
        )

        val body = buildJsonObject {
            put("id", id)
            put("transactionId", transactionId)
            put("accountId", accountId)
            put("symbol", symbol)
            put("kind", kind)
            put("occurredOn", occurredOn)
            put("shares", shares)
            put("price", price)
            put("fees", fees)
        }
        outbox.enqueue("POST", "/api/trades", body)
    }

    /** The trade goes with its cash leg, here as on the server. */
    suspend fun deleteTrade(id: String) {
        val trade = dao.trade(id) ?: return
        dao.deleteTrade(id)
        transactionDao.deleteLocal(trade.transactionId, null)
        outbox.enqueue("DELETE", "/api/trades/$id")
    }
}
