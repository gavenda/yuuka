package dev.gavenda.yuuka.domain

import java.text.NumberFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Money is handled as integer minor units (cents) everywhere, so arithmetic is
 * exact. Conversion to and from a decimal string happens only at the edges.
 */

/** The currency used when none is known. */
const val DEFAULT_CURRENCY = "PHP"

/**
 * Formats minor units as a localised currency string, e.g. `-4599` -> `-₱45.99`.
 *
 * A currency code [Currency.getInstance] does not recognise (unlike `Intl`,
 * which renders an unknown but well-formed code as the code itself) falls back
 * to the amount plus the code, rather than throwing and blanking the figure.
 */
fun formatMoney(minor: Long, currency: String = DEFAULT_CURRENCY): String {
    return try {
        val instance = Currency.getInstance(currency)
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = instance
        format.minimumFractionDigits = 2
        format.maximumFractionDigits = 2
        format.format(minor / 100.0)
    } catch (_: IllegalArgumentException) {
        "${formatAmount(minor)} $currency".trim()
    }
}

/** The currency's symbol alone, e.g. `"PHP"` -> `"₱"`, for an input field's prefix affix. */
fun currencySymbol(currency: String = DEFAULT_CURRENCY): String {
    return try {
        Currency.getInstance(currency).getSymbol(Locale.getDefault())
    } catch (_: IllegalArgumentException) {
        currency
    }
}

/** Formats without the currency symbol, for tables that label the currency once. */
fun formatAmount(minor: Long): String {
    val format = NumberFormat.getNumberInstance(Locale.getDefault())
    format.minimumFractionDigits = 2
    format.maximumFractionDigits = 2
    return format.format(minor / 100.0)
}

/** Renders minor units as an editable decimal string, e.g. `4599` -> `45.99`. */
fun toDecimalString(minor: Long): String {
    val negative = minor < 0
    val absolute = abs(minor)
    val whole = absolute / 100
    val cents = absolute % 100
    return "${if (negative) "-" else ""}$whole.${cents.toString().padStart(2, '0')}"
}

private val MONEY_PATTERN = Regex("^(-)?(\\d*)(?:\\.(\\d{1,2}))?$")

/**
 * Parses a decimal string into minor units, or returns null if it is not a
 * number. Splitting on the decimal point rather than multiplying by 100 avoids
 * the float rounding that turns 45.99 into 4598.999…
 */
fun parseMoney(input: String): Long? {
    val normalised = input.trim().replace(Regex("[\\s,_]"), "")
    if (normalised.isEmpty()) return null

    val match = MONEY_PATTERN.matchEntire(normalised) ?: return null
    val (sign, whole, fraction) = match.destructured
    if (whole.isEmpty() && fraction.isEmpty()) return null

    // A digit string long enough to overflow Long (e.g. pasted or typed in
    // bulk) must not throw — it is simply not a representable amount.
    val wholeMinor = (whole.ifEmpty { "0" }).toLongOrNull() ?: return null
    val fractionMinor = fraction.padEnd(2, '0').ifEmpty { "00" }.toLongOrNull() ?: return null
    val minor = try {
        Math.addExact(Math.multiplyExact(wholeMinor, 100), fractionMinor)
    } catch (_: ArithmeticException) {
        return null
    }
    return if (sign.isNotEmpty()) -minor else minor
}

/** Parses a percentage input like "12.5" into 0-100, or null if it is not a valid share. */
fun parsePercent(input: String): Double? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null

    val value = trimmed.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0 || value > 100) return null
    return value
}

/** Percentage of [planned] consumed by [actual], clamped for use as a bar width. */
fun percentOf(actual: Long, planned: Long): Int {
    if (planned <= 0) return if (actual > 0) 100 else 0
    return min(100, max(0, ((actual.toDouble() / planned.toDouble()) * 100).roundToInt()))
}
