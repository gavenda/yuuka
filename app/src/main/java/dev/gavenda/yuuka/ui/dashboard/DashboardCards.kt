package dev.gavenda.yuuka.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.ui.common.harmonisedColor
import org.koin.compose.koinInject

/**
 * The categories behind the "Over budget" figure, the furthest over first. Each bar is what was spent: the
 * category's colour as far as the plan reached, then the overrun in the error colour, the words beside it
 * saying the same so the colour is never alone. Mirrors `OverBudgetCard.vue`.
 */
@Composable
internal fun OverBudgetCard(entries: List<CategoryBreakdown>, currency: String, modifier: Modifier = Modifier) {
    val visibility = koinInject<AmountVisibility>()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.dashboard_over_budget), style = MaterialTheme.typography.titleLarge)

            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entries.forEach { entry ->
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(entry.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                            Text(
                                stringResource(R.string.amount_over, visibility.displayMoney(-entry.remaining, currency)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        OverrunBar(entry, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            stringResource(
                                R.string.dashboard_spent_of_planned,
                                visibility.displayMoney(entry.actual, currency),
                                visibility.displayMoney(entry.planned, currency),
                            ),
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The plan and the overrun as two lengths of one 8dp bar, 4dp apart — Material's linear indicator's gap. */
@Composable
private fun OverrunBar(entry: CategoryBreakdown, modifier: Modifier = Modifier) {
    val overrun = entry.actual - entry.planned
    Row(modifier = modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier.weight(entry.planned.toFloat()).fillMaxHeight()
                .background(harmonisedColor(entry.color, MaterialTheme.colorScheme.primary), CircleShape),
        )
        Box(Modifier.weight(overrun.toFloat()).widthIn(min = 8.dp).fillMaxHeight().background(MaterialTheme.colorScheme.error, CircleShape))
    }
}
