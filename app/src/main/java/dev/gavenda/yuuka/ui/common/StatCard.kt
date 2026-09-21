package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.ui.theme.compactFigure

/** One hero figure per view: everything else on the screen explains it. Mirrors `StatCard.vue`. */
@Composable
fun StatCard(
    label: String,
    amount: Long,
    modifier: Modifier = Modifier,
    currency: String = DEFAULT_CURRENCY,
    caption: String? = null,
    signed: Boolean = false,
    hero: Boolean = false,
    compact: Boolean = false,
    /** False renders the value like an ordinary figure instead of the usual hero-ish emphasis — for a stat among many, not the one the card is about. */
    emphasized: Boolean = true,
    /** Non-null gives the card Material's press ripple, purely for touch feedback — pass `{}` where no action is needed. */
    onClick: (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
) {
    val content: @Composable ColumnScope.() -> Unit = {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MoneyText(
            amount = amount,
            modifier = Modifier.padding(top = 8.dp),
            currency = currency,
            tone = if (signed) MoneyTone.SIGNED else MoneyTone.NEUTRAL,
            style = when {
                !emphasized -> MaterialTheme.typography.bodyLarge
                hero -> MaterialTheme.typography.displaySmall
                compact -> MaterialTheme.typography.compactFigure
                else -> MaterialTheme.typography.headlineMedium
            },
        )
        if (caption != null) {
            Text(
                text = caption,
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val body: @Composable () -> Unit = {
        if (icon == null) {
            Column(modifier = Modifier.padding(20.dp), content = content)
        } else {
            Row(modifier = Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), content = content)
                icon()
            }
        }
    }

    if (onClick != null) {
        Card(colors = yuukaCardColors(), onClick = onClick, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(colors = yuukaCardColors(), modifier = modifier.fillMaxWidth()) { body() }
    }
}
