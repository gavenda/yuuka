package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.Trade
import java.math.BigDecimal
import kotlin.math.max
import kotlin.math.roundToLong

/*
 * PSE investments, worked out on the device. Money is in centavos as everywhere else; a price is in
 * ten-thousandths of a peso (`…E4`), since the PSE's smallest tick is ₱0.0001, and shares are whole.
 * The arithmetic is the server's (`server/investments.ts`) — a trade made offline must show the figures
 * the server will agree with once it lands.
 */

object TradeKinds {
    const val BUY = "buy"
    const val SELL = "sell"
    const val DIVIDEND = "dividend"
}

/** The value in centavos of [shares] at [priceE4], rounded half up. */
fun valueOf(shares: Long, priceE4: Long): Long = Math.floorDiv(shares * priceE4 + 50, 100)

/** The cash a trade moves, signed: a buy pays the gross and the fees, a sell and a dividend bring in the gross less them. */
fun tradeCash(kind: String, shares: Long, priceE4: Long, fees: Long): Long {
    val gross = valueOf(shares, priceE4)
    return if (kind == TradeKinds.BUY) -(gross + fees) else gross - fees
}

/** A price as PSE quotes it: at least two decimals, up to four when the tick needs them. */
fun formatPrice(priceE4: Long): String = currencySymbol("PHP") + priceDecimal(priceE4)

/** [formatPrice] without the peso sign or grouping, as a field holds it. */
fun priceInput(priceE4: Long): String = priceDecimal(priceE4).replace(",", "")

private fun priceDecimal(priceE4: Long): String {
    val decimal = BigDecimal.valueOf(priceE4, 4).stripTrailingZeros()
    val scaled = if (decimal.scale() < 2) decimal.setScale(2) else decimal
    return "%,.${scaled.scale()}f".format(scaled)
}

/** [part] as a signed percentage of [whole], one decimal. */
fun percentChange(part: Long, whole: Long): String =
    if (whole == 0L) "—" else "%+.1f%%".format(part * 100.0 / whole)

/** A typed price in ten-thousandths of a peso, split on the decimal point like money: null unless above zero with at most four decimals. */
fun parsePrice(input: String): Long? {
    val text = input.trim().replace(",", "")
    val match = Regex("""(\d*)(?:\.(\d{0,4}))?""").matchEntire(text) ?: return null
    val (whole, fraction) = match.destructured
    if (whole.isEmpty() && fraction.isEmpty()) return null
    val value = (whole.ifEmpty { "0" }.toLongOrNull() ?: return null) * 10_000 + fraction.padEnd(4, '0').toLong()
    return value.takeIf { it > 0 }
}

/** A typed share count: a whole number above zero, or null. */
fun parseShares(input: String): Long? = input.trim().replace(",", "").toLongOrNull()?.takeIf { it > 0 }

/** What a broker charges on a PSE trade of [gross] centavos. */
data class PseFees(val commission: Long, val vat: Long, val pseFee: Long, val sccp: Long, val salesTax: Long) {
    val total: Long get() = commission + vat + pseFee + sccp + salesTax
}

private fun share(amount: Long, numerator: Long, denominator: Long) = (amount * numerator + denominator / 2) / denominator

/** Commission 0.25% (₱20 at least), 12% VAT on it, the PSE's 0.005%, SCCP's 0.01%, and on a sell the 0.1% stock transaction tax. */
fun pseFees(gross: Long, sell: Boolean): PseFees {
    if (gross <= 0) return PseFees(0, 0, 0, 0, 0)
    val commission = max(share(gross, 25, 10_000), 2_000)
    return PseFees(
        commission = commission,
        vat = share(commission, 12, 100),
        pseFee = share(gross, 5, 100_000),
        sccp = share(gross, 1, 10_000),
        salesTax = if (sell) share(gross, 1, 1_000) else 0,
    )
}

/** The 10% final tax withheld from a cash dividend. */
fun dividendTax(gross: Long): Long = share(gross, 10, 100)

/** The PSE's board lot for a stock trading at [priceE4]: the fewest shares the main board takes. */
fun boardLot(priceE4: Long): Long = when {
    priceE4 < 100 -> 1_000_000
    priceE4 < 500 -> 100_000
    priceE4 < 5_000 -> 10_000
    priceE4 < 50_000 -> 1_000
    priceE4 < 500_000 -> 100
    priceE4 < 10_000_000 -> 10
    else -> 5
}

/** What one stock in one broker account comes to. */
data class Position(
    val accountId: String,
    val symbol: String,
    val shares: Long,
    /** What the shares held cost, buy fees included. */
    val costBasis: Long,
    val realised: Long,
    val dividends: Long,
)

/**
 * Works the trades through in date order, average cost, as the server does: a buy adds its shares and its cost
 * with fees, a sell takes its shares out at the average cost so far and realises the rest of its proceeds, a
 * dividend adds its net cash. Null when a sell takes more than is held at that point.
 */
fun positions(trades: List<Trade>): List<Position>? {
    data class Running(var shares: Long = 0, var cost: Long = 0, var realised: Long = 0, var dividends: Long = 0)
    val running = linkedMapOf<Pair<String, String>, Running>()
    trades.sortedWith(compareBy<Trade> { it.occurredOn }.thenBy { it.createdAt }).forEach { trade ->
        val position = running.getOrPut(trade.accountId to trade.symbol) { Running() }
        val gross = valueOf(trade.shares, trade.price)
        when (trade.kind) {
            TradeKinds.BUY -> {
                position.shares += trade.shares
                position.cost += gross + trade.fees
            }
            TradeKinds.SELL -> {
                if (trade.shares > position.shares) return null
                val cost = (position.cost * trade.shares.toDouble() / position.shares).roundToLong()
                position.realised += gross - trade.fees - cost
                position.cost -= cost
                position.shares -= trade.shares
            }
            else -> position.dividends += gross - trade.fees
        }
    }
    return running.map { (key, value) -> Position(key.first, key.second, value.shares, value.cost, value.realised, value.dividends) }
}

/** A position with what it is worth: one row of the Investments screen. */
data class Holding(
    val position: Position,
    val name: String,
    val accountName: String,
    val bank: String?,
    /** The latest close, or the average cost when the stock has no price yet. */
    val priceE4: Long,
    val changeBp: Long?,
    val asOf: String?,
) {
    val symbol: String get() = position.symbol
    val shares: Long get() = position.shares
    val costBasis: Long get() = position.costBasis
    /** What a share cost on average, buy fees included. */
    val avgCostE4: Long get() = if (shares == 0L) 0 else (costBasis * 100.0 / shares).roundToLong()
    val marketValue: Long get() = valueOf(shares, priceE4)
    val unrealised: Long get() = marketValue - costBasis

    /** The close before the latest, worked back from the day's move. */
    val previousCloseE4: Long get() = changeBp?.let { (priceE4 * 10_000.0 / (10_000 + it)).roundToLong() } ?: priceE4
    val dayChange: Long get() = marketValue - valueOf(shares, previousCloseE4)
}

/**
 * Every position still holding shares, valued at its stock's latest close, plus the ones sold out that made or
 * lost something (they still say what was realised).
 */
fun holdings(
    trades: List<Trade>,
    securities: Map<String, Security>,
    accountName: (String) -> String?,
    bank: (String) -> String?,
): List<Holding> = (positions(trades) ?: emptyList()).map { position ->
    val security = securities[position.symbol]
    Holding(
        position = position,
        name = security?.name ?: trades.firstOrNull { it.symbol == position.symbol }?.name ?: position.symbol,
        accountName = accountName(position.accountId) ?: trades.firstOrNull { it.accountId == position.accountId }?.accountName.orEmpty(),
        bank = bank(position.accountId),
        priceE4 = security?.price ?: if (position.shares > 0) (position.costBasis * 100.0 / position.shares).roundToLong() else 0,
        changeBp = security?.changeBp,
        asOf = security?.asOf,
    )
}
