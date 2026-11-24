package dev.gavenda.yuuka.data.model

import kotlinx.serialization.Serializable

/**
 * A PSE stock at its latest close, a UITF at its latest NAVPU, or an ETF someone has looked up at its latest close,
 * as `GET /api/securities` answers it. Market data, shared by everyone. [price] is in ten-thousandths of
 * [currency]: the PSE's smallest tick is ₱0.0001, and a NAVPU is quoted to four decimals.
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
    /** The logo PSE Edge shows for the company, loaded from there; null with none found yet. */
    val logoUrl: String? = null,
    /** `stock`, `fund` or `etf`, as the API speaks it. */
    val kind: String = SecurityKinds.STOCK,
    /** The trust entity that runs a fund; null for a stock. */
    val provider: String? = null,
    /** What a fund invests in (Money Market Fund, Equity Fund, …); null for a stock. */
    val classification: String? = null,
    /** What [price] is in: PHP for a stock or a fund, an ETF's own currency otherwise. */
    val currency: String = "PHP",
    /** Where an ETF trades (`US` or `LSE`), which decides its broker's fees and the tax on its dividends; null for the PSE. */
    val exchange: String? = null,
)

object SecurityKinds {
    const val STOCK = "stock"
    const val FUND = "fund"
    const val ETF = "etf"
}

/** An ETF a search on Yahoo Finance found (`GET /api/securities/search`), not yet kept: looking it up adds it. */
@Serializable
data class EtfMatch(val symbol: String, val name: String, val exchange: String)

/**
 * A buy, a sell or a cash dividend — for a fund, a subscription, a redemption or a distribution — and the cash it
 * moved in its account (`GET /api/trades`).
 */
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
    /** Ten-thousandths of a share or a unit; a stock's are whole shares. A dividend's are the ones it was paid on. */
    val units: Long,
    /** Ten-thousandths of a peso a share or unit; a dividend's is what it paid a share. */
    val price: Long,
    /** A fund's cash before fees in centavos, as the trust bank said it; null works it out from the units and price. */
    val gross: Long? = null,
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
