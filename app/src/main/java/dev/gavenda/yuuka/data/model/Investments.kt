package dev.gavenda.yuuka.data.model

import kotlinx.serialization.Serializable

/**
 * A PSE stock at its latest close, as `GET /api/securities` answers it. Market data, shared by everyone.
 * [price] is in ten-thousandths of a peso: the PSE's smallest tick is ₱0.0001.
 */
@Serializable
data class Security(
    val symbol: String,
    val name: String,
    val price: Long,
    /** The day's move in hundredths of a percent, when PHISIX gave one. */
    val changeBp: Long? = null,
    /** The trading day [price] is the close of. */
    val asOf: String,
)

/** A buy, a sell or a cash dividend, and the cash it moved in its broker account (`GET /api/trades`). */
@Serializable
data class Trade(
    val id: String,
    val accountId: String,
    val accountName: String? = null,
    val symbol: String,
    val name: String? = null,
    /** `buy`, `sell` or `dividend`, as the API speaks it. */
    val kind: String,
    val occurredOn: String,
    val shares: Long,
    /** Ten-thousandths of a peso a share; a dividend's is what it paid a share. */
    val price: Long,
    /** Centavos: the broker's fees and taxes, or a dividend's withheld final tax. */
    val fees: Long,
    /** Centavos, signed: the cash leg's amount. */
    val amount: Long,
    val transactionId: String,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** One trading day's close, in ten-thousandths of a peso. */
@Serializable
data class PricePoint(val day: String, val close: Long)
