package dev.gavenda.yuuka.domain

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * Philippines take-home pay estimate: the mandatory employee contributions
 * (SSS, PhilHealth, Pag-IBIG) and BIR withholding tax under the TRAIN law,
 * derived from a monthly gross salary. Everything here is a pure calculation in
 * minor units (centavos) — nothing is persisted or sent to the API, it only
 * produces a number the caller may choose to save as a planned income amount.
 * Mirrors `src/lib/philippinesTax.ts`.
 */

/** Converts a whole-peso amount into centavos, for the tables below. */
private fun peso(amount: Long): Long = amount * 100

data class ContributionLine(val label: String, val amount: Long)

data class NetPayBreakdown(
    val gross: Long,
    val contributions: List<ContributionLine>,
    val totalContributions: Long,
    val taxableIncome: Long,
    val incomeTax: Long,
    val netPay: Long,
)

/**
 * SSS employee share: 5% of the Monthly Salary Credit, which brackets actual
 * pay into ₱500 steps between a ₱5,000 floor and a ₱35,000 ceiling.
 */
private fun sssContribution(gross: Long): Long {
    val minMsc = peso(5_000)
    val maxMsc = peso(35_000)
    val step = peso(500)
    val msc = min(max(ceil(gross.toDouble() / step).toLong() * step, minMsc), maxMsc)
    return (msc * 0.05).roundToLong()
}

/**
 * PhilHealth employee share: 2.5% of monthly basic pay (half of the 5% total
 * premium), floored at a ₱10,000 salary and capped at a ₱100,000 one.
 */
private fun philHealthContribution(gross: Long): Long {
    val floor = peso(10_000)
    val ceiling = peso(100_000)
    val base = min(max(gross, floor), ceiling)
    return (base * 0.025).roundToLong()
}

/**
 * Pag-IBIG employee share: 1% of pay up to ₱1,500, 2% above it, fixed at
 * ₱200 once pay passes the ₱10,000 Monthly Fund Salary cap.
 */
private fun pagIbigContribution(gross: Long): Long {
    val threshold = peso(1_500)
    val cap = peso(10_000)
    if (gross > cap) return peso(200)
    return (gross * (if (gross <= threshold) 0.01 else 0.02)).roundToLong()
}

private data class TaxBracket(val over: Long, val base: Long, val rate: Double)

/**
 * BIR monthly withholding tax table under the TRAIN law, effective 2023.
 * Checked highest bracket first; `base` already carries the running total
 * from every bracket below it, so only the excess over its own floor needs
 * the marginal rate applied.
 */
private val TAX_BRACKETS = listOf(
    TaxBracket(peso(666_667), peso(200_833) + 33, 0.35),
    TaxBracket(peso(166_667), peso(40_833) + 33, 0.3),
    TaxBracket(peso(66_667), peso(10_833) + 33, 0.25),
    TaxBracket(peso(33_333), peso(2_500), 0.2),
    TaxBracket(peso(20_833), 0, 0.15),
    TaxBracket(0, 0, 0.0),
)

private fun withholdingTax(taxableIncome: Long): Long {
    // The lowest bracket's `over` is 0, so an income of exactly 0 (common
    // once contributions consume a small gross entirely) must still match it.
    val bracket = TAX_BRACKETS.first { taxableIncome >= it.over }
    return bracket.base + ((taxableIncome - bracket.over) * bracket.rate).roundToLong()
}

/**
 * Computes take-home pay from a monthly gross salary, both in centavos.
 * Contributions are deducted from gross to reach taxable income, and
 * withholding tax is deducted from that to reach net pay.
 */
fun computeNetPay(grossMinor: Long): NetPayBreakdown {
    val gross = max(0, grossMinor)
    val contributions = listOf(
        ContributionLine("SSS", if (gross > 0) sssContribution(gross) else 0),
        ContributionLine("PhilHealth", if (gross > 0) philHealthContribution(gross) else 0),
        ContributionLine("Pag-IBIG", if (gross > 0) pagIbigContribution(gross) else 0),
    )

    if (gross == 0L) return NetPayBreakdown(0, contributions, 0, 0, 0, 0)

    val totalContributions = contributions.sumOf { it.amount }
    val taxableIncome = max(0, gross - totalContributions)
    val incomeTax = withholdingTax(taxableIncome)
    val netPay = gross - totalContributions - incomeTax

    return NetPayBreakdown(gross, contributions, totalContributions, taxableIncome, incomeTax, netPay)
}
