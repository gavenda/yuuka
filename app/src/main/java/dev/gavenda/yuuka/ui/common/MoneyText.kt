package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import org.koin.compose.koinInject
import kotlin.math.abs

/** Tone a figure reads in, mirroring `MoneyText.vue`'s colour rules. */
/** [SIGNED_ALERT] is [SIGNED] with the negative side in `error` — for a figure where below zero is worth noticing, such as an account's balance or a transaction's amount. */
enum class MoneyTone { NEUTRAL, SIGNED, TRANSFER, SIGNED_ALERT }

/**
 * The colour a figure reads in, taken from the theme so it follows dynamic colour:
 * a routine outflow stays in the content colour (`error` is for errors, and a purchase is not one), inflows
 * are `primary` and transfers `secondary`, a movement between your own accounts being neither. `tertiary`
 * is kept for what is worth a glance without being either an error or an action (a budget near its limit,
 * a day above the average), so it never marks a direction.
 */
@Composable
fun moneyColor(amount: Long, tone: MoneyTone): Color = when (tone) {
    MoneyTone.TRANSFER -> MaterialTheme.colorScheme.secondary
    MoneyTone.NEUTRAL -> LocalContentColor.current
    MoneyTone.SIGNED_ALERT -> when {
        amount > 0 -> MaterialTheme.colorScheme.primary
        amount < 0 -> MaterialTheme.colorScheme.error
        else -> LocalContentColor.current
    }
    MoneyTone.SIGNED -> when {
        amount > 0 -> MaterialTheme.colorScheme.primary
        else -> LocalContentColor.current
    }
}

/**
 * Renders an amount through [AmountVisibility.displayMoney] so the privacy
 * toggle can never miss a figure — every money value on screen should go
 * through this rather than formatting directly. Mirrors `MoneyText.vue`.
 */
@Composable
fun MoneyText(
    amount: Long,
    modifier: Modifier = Modifier,
    currency: String = DEFAULT_CURRENCY,
    tone: MoneyTone = MoneyTone.NEUTRAL,
    /** Always show a leading + or -, even when [tone] is not [MoneyTone.SIGNED]. */
    explicit: Boolean = false,
    style: TextStyle = TextStyle.Default,
    fontSize: TextUnit = TextUnit.Unspecified,
) {
    val visibility = koinInject<AmountVisibility>()
    val text = visibility.displayMoney(abs(amount), currency)
    val prefix = when {
        explicit -> if (amount < 0) "−" else "+"
        amount < 0 -> "−"
        else -> ""
    }
    Text(text = "$prefix$text", modifier = modifier, style = style, fontSize = fontSize, color = moneyColor(amount, tone))
}
