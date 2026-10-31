package dev.gavenda.yuuka.data.local.dao

import androidx.room3.*
import dev.gavenda.yuuka.data.local.entity.SecurityEntity
import dev.gavenda.yuuka.data.local.entity.StockPriceEntity
import dev.gavenda.yuuka.data.local.entity.TradeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InvestmentDao {
    @Query("SELECT * FROM securities ORDER BY symbol")
    fun observeSecurities(): Flow<List<SecurityEntity>>

    @Query("SELECT * FROM securities WHERE symbol = :symbol")
    suspend fun security(symbol: String): SecurityEntity?

    @Query("DELETE FROM securities")
    suspend fun clearSecurities()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSecurities(securities: List<SecurityEntity>)

    @Transaction
    suspend fun replaceSecurities(securities: List<SecurityEntity>) {
        clearSecurities()
        insertSecurities(securities)
    }

    /** Newest first, the order the API answers in. */
    @Query("SELECT * FROM trades ORDER BY occurredOn DESC, createdAt DESC")
    fun observeTrades(): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades")
    suspend fun trades(): List<TradeEntity>

    @Query("SELECT * FROM trades WHERE id = :id")
    suspend fun trade(id: String): TradeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrade(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE id = :id")
    suspend fun deleteTrade(id: String)

    @Query("DELETE FROM trades")
    suspend fun clearTrades()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrades(trades: List<TradeEntity>)

    @Transaction
    suspend fun replaceTrades(trades: List<TradeEntity>) {
        clearTrades()
        insertTrades(trades)
    }

    @Query("SELECT * FROM stock_prices WHERE symbol = :symbol ORDER BY day ASC")
    fun observePrices(symbol: String): Flow<List<StockPriceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<StockPriceEntity>)
}
