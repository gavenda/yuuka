package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Security
import dev.gavenda.yuuka.data.model.Trade
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.math.max
import kotlin.math.roundToLong

/*
 * PSE stocks and UITFs, worked out on the device. Money is in centavos as everywhere else; a price is in
 * ten-thousandths of a peso (`…E4`), since the PSE's smallest tick is ₱0.0001 and a NAVPU is quoted to four
 * decimals; a quantity is in ten-thousandths of a share or a unit (`units`), since a fund's units are
 * fractional — a stock's are always whole shares. The arithmetic is the server's (`server/investments.ts`) —
 * a trade made offline must show the figures the server will agree with once it lands.
 */

/** Ten-thousandths of a unit in one share or unit. */
const val UNIT = 10_000L

/** A fund's symbol is `UITF-` and its id on uitf.com.ph; no PSE symbol has a hyphen. */
const val FUND_PREFIX = "UITF-"

/** Whether [symbol] names a UITF rather than a PSE stock. */
fun isFund(symbol: String): Boolean = symbol.startsWith(FUND_PREFIX)

object TradeKinds {
    const val BUY = "buy"
    const val SELL = "sell"
    const val DIVIDEND = "dividend"
}

private val MILLION = BigInteger.valueOf(1_000_000)

/** The value in centavos of [units] at [priceE4], rounded half up. Worked out wide: the product can pass a Long. */
fun valueOf(units: Long, priceE4: Long): Long =
    BigInteger.valueOf(units).multiply(BigInteger.valueOf(priceE4)).add(BigInteger.valueOf(500_000)).divide(MILLION).toLong()

/** The units [gross] centavos buy at [priceE4], to the ten-thousandth, rounded half up: what a trust bank gives for an amount. */
fun unitsFor(gross: Long, priceE4: Long): Long {
    if (priceE4 <= 0) return 0
    val price = BigInteger.valueOf(priceE4)
    return BigInteger.valueOf(gross).multiply(MILLION).add(price.shiftRight(1)).divide(price).toLong()
}

/** A trade's cash before fees: what the trust bank said ([gross]), or the units at the price. */
fun tradeGross(units: Long, priceE4: Long, gross: Long? = null): Long = gross ?: valueOf(units, priceE4)

/** The cash a trade moves, signed: a buy pays the gross and the fees, a sell and a dividend bring in the gross less them. */
fun tradeCash(kind: String, units: Long, priceE4: Long, fees: Long, gross: Long? = null): Long {
    val value = tradeGross(units, priceE4, gross)
    return if (kind == TradeKinds.BUY) -(value + fees) else value - fees
}

/** What a fund's trades are called on its cash leg, as the server writes it. */
private val FUND_VERBS = mapOf(TradeKinds.BUY to "Subscribe", TradeKinds.SELL to "Redeem", TradeKinds.DIVIDEND to "Distribution on")

/**
 * A trade's cash leg's payee and notes, as the server writes them: a stock's ticker and `Buy 1,000 BDO`, a fund's
 * name and `Subscribe 1,234.5678 units`.
 */
fun legLabels(kind: String, symbol: String, name: String?, units: Long): Pair<String, String> {
    val count = formatUnits(units)
    if (isFund(symbol)) return (name ?: symbol).take(120) to "${FUND_VERBS[kind]} $count units"
    return symbol to "${kind.replaceFirstChar { it.uppercase() }} $count $symbol"
}

/** What a security is called where one word is wanted: a stock's ticker, a fund's name (its symbol means nothing to anyone). */
fun securityLabel(symbol: String, name: String?): String = if (isFund(symbol)) name ?: symbol else symbol

private fun unitsDecimal(units: Long, grouped: Boolean): String {
    val whole = units / UNIT
    val fraction = (units % UNIT).toString().padStart(4, '0').trimEnd('0')
    val head = if (grouped) "%,d".format(whole) else whole.toString()
    return if (fraction.isEmpty()) head else "$head.$fraction"
}

/** Shares or units as they are read: grouped, with as many of the four decimals as they need. */
fun formatUnits(units: Long): String = unitsDecimal(units, grouped = true)

/** [formatUnits] without grouping, as a field holds it. */
fun unitsInput(units: Long): String = unitsDecimal(units, grouped = false)

private val WHOLE_UNITS = Regex("""(\d+)()""")
private val FRACTIONAL_UNITS = Regex("""(\d*)(?:\.(\d{0,4}))?""")

/**
 * A typed quantity in ten-thousandths, split on the decimal point like money: a whole number of shares for a stock
 * ([whole]), up to four decimals of a unit for a fund. Null unless above zero.
 */
fun parseUnits(input: String, whole: Boolean): Long? {
    val text = input.trim().replace(",", "")
    val match = (if (whole) WHOLE_UNITS else FRACTIONAL_UNITS).matchEntire(text) ?: return null
    val (head, fraction) = match.destructured
    if (head.isEmpty() && fraction.isEmpty()) return null
    val count = head.ifEmpty { "0" }.toLongOrNull()?.takeIf { it <= Long.MAX_VALUE / UNIT - 1 } ?: return null
    return (count * UNIT + fraction.padEnd(4, '0').toLong()).takeIf { it > 0 }
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

private val PRICE = Regex("""(\d*)(?:\.(\d{0,8}))?""")

/**
 * A typed price in ten-thousandths of a peso, split on the decimal point like money: null unless above zero. A
 * NAVPU a trust bank quotes to six decimals is taken and rounded half up to four, as the server reads it.
 */
fun parsePrice(input: String): Long? {
    val text = input.trim().replace(",", "")
    val match = PRICE.matchEntire(text) ?: return null
    val (whole, fraction) = match.destructured
    if (whole.isEmpty() && fraction.isEmpty()) return null
    val count = whole.ifEmpty { "0" }.toLongOrNull()?.takeIf { it <= Long.MAX_VALUE / 10_000 - 1 } ?: return null
    val digits = fraction.padEnd(5, '0')
    val value = count * 10_000 + digits.take(4).toLong() + (if (digits[4] >= '5') 1 else 0)
    return value.takeIf { it > 0 }
}

/** What a broker charges on a PSE trade of [gross] centavos. */
data class PseFees(val commission: Long, val vat: Long, val pseFee: Long, val sccp: Long, val salesTax: Long) {
    val total: Long get() = commission + vat + pseFee + sccp + salesTax
}

private fun share(amount: Long, numerator: Long, denominator: Long) = (amount * numerator + denominator / 2) / denominator

/** The least commission most PSE brokers charge on a trade, in centavos: ₱20. */
const val MIN_COMMISSION = 2_000L

/**
 * Commission 0.25% (at least [minCommission], ₱20 unless the broker waives it), 12% VAT on it, the PSE's 0.005%,
 * SCCP's 0.01%, and on a sell the 0.1% stock transaction tax.
 *
 * Each fee is rounded to the nearest centavo, unless the broker [roundsUp] as DragonFi does: it charges the
 * commission with its VAT, and the PSE and SCCP fees together, each rounded up to the next centavo. The lines
 * still add up to what is charged — the commission and the PSE fee are shown to the nearest centavo, and the VAT
 * and the SCCP fee take what is left of their group.
 */
fun pseFees(gross: Long, sell: Boolean, minCommission: Long = MIN_COMMISSION, roundsUp: Boolean = false): PseFees {
    if (gross <= 0) return PseFees(0, 0, 0, 0, 0)
    if (roundsUp) return pseFeesRoundedUp(gross, sell, minCommission)
    val commission = max(share(gross, 25, 10_000), minCommission)
    return PseFees(
        commission = commission,
        vat = share(commission, 12, 100),
        pseFee = share(gross, 5, 100_000),
        sccp = share(gross, 1, 10_000),
        salesTax = if (sell) share(gross, 1, 1_000) else 0,
    )
}

/** A hundred-thousandth of a centavo: fine enough that every fee on a whole-centavo gross is exact in it. */
private const val FINE = 100_000L

private fun pseFeesRoundedUp(gross: Long, sell: Boolean, minCommission: Long): PseFees {
    val commission = max(gross * 250, minCommission * FINE)
    val vat = commission * 12 / 100
    val pseFee = gross * 5
    val sccp = gross * 10
    val nearest = { fine: Long -> (fine + FINE / 2) / FINE }
    val up = { fine: Long -> (fine + FINE - 1) / FINE }
    return PseFees(
        commission = nearest(commission),
        vat = up(commission + vat) - nearest(commission),
        pseFee = nearest(pseFee),
        sccp = up(pseFee + sccp) - nearest(pseFee),
        salesTax = if (sell) up(gross * 100) else 0,
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

/** What one stock or fund in one account comes to. */
data class Position(
    val accountId: String,
    val symbol: String,
    /** Ten-thousandths of a share or a unit held. */
    val units: Long,
    /** What the units held cost, buy fees included. */
    val costBasis: Long,
    val realised: Long,
    val dividends: Long,
)

/**
 * Works the trades through in date order, average cost, as the server does: a buy adds its units and its cost
 * with fees, a sell takes its units out at the average cost so far and realises the rest of its proceeds, a
 * dividend adds its net cash. Null when a sell takes more than is held at that point.
 */
fun positions(trades: List<Trade>): List<Position>? {
    data class Running(var units: Long = 0, var cost: Long = 0, var realised: Long = 0, var dividends: Long = 0)
    val running = linkedMapOf<Pair<String, String>, Running>()
    trades.sortedWith(compareBy<Trade> { it.occurredOn }.thenBy { it.createdAt }).forEach { trade ->
        val position = running.getOrPut(trade.accountId to trade.symbol) { Running() }
        val gross = tradeGross(trade.units, trade.price, trade.gross)
        when (trade.kind) {
            TradeKinds.BUY -> {
                position.units += trade.units
                position.cost += gross + trade.fees
            }
            TradeKinds.SELL -> {
                if (trade.units > position.units) return null
                val cost = (position.cost * trade.units.toDouble() / position.units).roundToLong()
                position.realised += gross - trade.fees - cost
                position.cost -= cost
                position.units -= trade.units
            }
            else -> position.dividends += gross - trade.fees
        }
    }
    return running.map { (key, value) -> Position(key.first, key.second, value.units, value.cost, value.realised, value.dividends) }
}

/**
 * The units of [symbol] in [accountId] held at the start of [day] (`YYYY-MM-DD`), its own trades left out: what
 * a dividend or a distribution paid that day is most likely paid on. Zero for a history that does not stand.
 */
fun unitsHeldBefore(trades: List<Trade>, accountId: String, symbol: String, day: String): Long =
    positions(trades.filter { it.accountId == accountId && it.symbol == symbol && it.occurredOn.take(10) < day })?.firstOrNull()?.units ?: 0

/** What a unit cost on average, in ten-thousandths of a peso: [costBasis] centavos over [units]. */
fun averagePriceE4(costBasis: Long, units: Long): Long = if (units == 0L) 0 else (costBasis * 1_000_000.0 / units).roundToLong()

/** A position with what it is worth: one row of the Investments screen. */
data class Holding(
    val position: Position,
    val name: String,
    val accountName: String,
    /** The latest close, or the average cost when the stock has no price yet. */
    val priceE4: Long,
    val changeBp: Long?,
    val asOf: String?,
) {
    val symbol: String get() = position.symbol
    /** Whether it is a UITF rather than a PSE stock. */
    val fund: Boolean get() = isFund(position.symbol)
    val units: Long get() = position.units
    val costBasis: Long get() = position.costBasis
    /** What a share or unit cost on average, buy fees included. */
    val avgCostE4: Long get() = averagePriceE4(costBasis, units)
    val marketValue: Long get() = valueOf(units, priceE4)
    val unrealised: Long get() = marketValue - costBasis

    /** The close before the latest, worked back from the day's move. */
    val previousCloseE4: Long get() = changeBp?.let { (priceE4 * 10_000.0 / (10_000 + it)).roundToLong() } ?: priceE4
    val dayChange: Long get() = marketValue - valueOf(units, previousCloseE4)
}

/**
 * Every position still holding shares or units, valued at its security's latest close or NAVPU, plus the ones sold out that made or
 * lost something (they still say what was realised).
 */
fun holdings(
    trades: List<Trade>,
    securities: Map<String, Security>,
    accountName: (String) -> String?,
): List<Holding> = (positions(trades) ?: emptyList()).map { position ->
    val security = securities[position.symbol]
    Holding(
        position = position,
        name = security?.name ?: trades.firstOrNull { it.symbol == position.symbol }?.name ?: position.symbol,
        accountName = accountName(position.accountId) ?: trades.firstOrNull { it.accountId == position.accountId }?.accountName.orEmpty(),
        priceE4 = security?.price ?: averagePriceE4(position.costBasis, position.units),
        changeBp = security?.changeBp,
        asOf = security?.asOf,
    )
}
