package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import org.koin.compose.koinInject
import kotlin.math.abs

/** Tone a figure reads in, mirroring `MoneyText.vue`'s colour rules. */
enum class MoneyTone { NEUTRAL, SIGNED, TRANSFER }

private val Green = Color(0xFF047857)
private val GreenDark = Color(0xFF34D399)
private val Red = Color(0xFFBE123C)
private val RedDark = Color(0xFFFB7185)
private val Blue = Color(0xFF1D4ED8)
private val BlueDark = Color(0xFF60A5FA)

@Composable
fun moneyColor(amount: Long, tone: MoneyTone, dark: Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f): Color = when (tone) {
    MoneyTone.TRANSFER -> if (dark) BlueDark else Blue
    MoneyTone.NEUTRAL -> LocalContentColor.current
    MoneyTone.SIGNED -> when {
        amount > 0 -> if (dark) GreenDark else Green
        amount < 0 -> if (dark) RedDark else Red
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
    val color = moneyColor(amount, tone)
    Text(text = "$prefix$text", modifier = modifier, style = style, fontSize = fontSize, color = color)
}
