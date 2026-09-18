package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY

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
            fontSize = if (emphasized) (if (hero) (if (compact) 30.sp else 40.sp) else (if (compact) 20.sp else 26.sp)) else TextUnit.Unspecified,
            style = if (emphasized) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold) else MaterialTheme.typography.bodyLarge,
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
        Card(onClick = onClick, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(modifier = modifier.fillMaxWidth()) { body() }
    }
}
