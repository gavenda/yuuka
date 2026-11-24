package dev.gavenda.yuuka.data.local.entity

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import dev.gavenda.yuuka.data.model.PricePoint
import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.Trade

/** Every PSE stock at its latest close, every UITF at its latest NAVPU and every ETF looked up; replaced whole from the API. */
@Entity(tableName = "securities")
data class SecurityEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val price: Long,
    val changeBp: Long?,
    val asOf: String,
    val logoUrl: String?,
    val kind: String,
    val provider: String?,
    val classification: String?,
    val currency: String,
    val exchange: String?,
)

@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val accountName: String?,
    val symbol: String,
    val name: String?,
    val kind: String,
    val occurredOn: String,
    val units: Long,
    val price: Long,
    val gross: Long?,
    val fees: Long,
    val amount: Long,
    val transactionId: String,
    val createdAt: String,
    val updatedAt: String,
)

/** A stock's daily closes, folded in as a holding's screen asks for them. */
@Entity(tableName = "stock_prices", primaryKeys = ["symbol", "day"])
data class StockPriceEntity(
    val symbol: String,
    val day: String,
    val close: Long,
)

fun Security.toEntity() = SecurityEntity(symbol, name, price, changeBp, asOf, logoUrl, kind, provider, classification, currency, exchange)
fun SecurityEntity.toDomain() = Security(symbol, name, price, changeBp, asOf, logoUrl, kind, provider, classification, currency, exchange)

fun Trade.toEntity() = TradeEntity(
    id, accountId, accountName, symbol, name, kind, occurredOn, units, price, gross, fees, amount, transactionId, createdAt, updatedAt,
)
fun TradeEntity.toDomain() = Trade(
    id, accountId, accountName, symbol, name, kind, occurredOn, units, price, gross, fees, amount, transactionId, createdAt, updatedAt,
)

fun PricePoint.toEntity(symbol: String) = StockPriceEntity(symbol, day, close)
fun StockPriceEntity.toDomain() = PricePoint(day, close)
