package dev.gavenda.yuuka.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.BudgetHealth
import dev.gavenda.yuuka.domain.BudgetPace
import dev.gavenda.yuuka.domain.statusColor
import dev.gavenda.yuuka.ui.common.AccountLogo
import dev.gavenda.yuuka.ui.common.MoneyText
import dev.gavenda.yuuka.ui.common.MoneyTone
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

/**
 * How the month stands against its plans: the expense budget used, with a mark where the month has got to
 * while it runs, and the income received against what was planned. Each is left out without a plan to be
 * measured against, and the card with them. Mirrors `PlanCard.vue`.
 */
@Composable
internal fun PlanCard(pace: BudgetPace?, income: Long, plannedIncome: Long, incomePercent: Int?, currency: String, modifier: Modifier = Modifier) {
    val visibility = koinInject<AmountVisibility>()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.dashboard_against_plan), style = MaterialTheme.typography.titleLarge)

            if (pace != null) {
                PlanMeter(
                    label = stringResource(R.string.dashboard_budget_used),
                    percent = pace.percent,
                    color = statusColor(pace.health),
                    marker = pace.day?.let { it.toFloat() / pace.days },
                    lines = listOf(
                        stringResource(R.string.dashboard_spent_of_planned, visibility.displayMoney(pace.spent, currency), visibility.displayMoney(pace.planned, currency)),
                        when {
                            pace.health == BudgetHealth.CRITICAL -> stringResource(R.string.dashboard_pace_past)
                            pace.day == null -> stringResource(R.string.dashboard_pace_within)
                            pace.health == BudgetHealth.WARNING -> stringResource(R.string.dashboard_pace_ahead, pace.day, pace.days)
                            else -> stringResource(R.string.dashboard_pace_keeping, pace.day, pace.days)
                        },
                    ),
                )
            }

            if (incomePercent != null) {
                PlanMeter(
                    label = stringResource(R.string.dashboard_income_received),
                    percent = incomePercent,
                    color = MaterialTheme.colorScheme.primary,
                    lines = listOf(stringResource(R.string.dashboard_spent_of_planned, visibility.displayMoney(income, currency), visibility.displayMoney(plannedIncome, currency))),
                )
            }
        }
    }
}

/**
 * One figure against its plan: the label and the share, Material's linear indicator filled as far as the share
 * (full past 100), an optional [marker] standing across it at a fraction of the way, and the [lines] under it.
 */
@Composable
private fun PlanMeter(label: String, percent: Int, color: Color, lines: List<String>, marker: Float? = null) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$percent%", style = MaterialTheme.typography.bodyMedium)
        }
        BoxWithConstraints(modifier = Modifier.padding(top = 6.dp).fillMaxWidth().height(16.dp), contentAlignment = Alignment.CenterStart) {
            LinearProgressIndicator(
                progress = { (percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                drawStopIndicator = {},
            )
            if (marker != null) {
                Box(
                    Modifier.offset(x = (maxWidth - 2.dp) * marker.coerceIn(0f, 1f)).width(2.dp).fillMaxHeight()
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                )
            }
        }
        lines.forEach { line ->
            Text(line, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Every account in use with its balance, under its type, each in the currency it holds. Net worth adds them
 * as they stand, so when they hold more than one currency the card says so rather than let the total pass for
 * one currency's figure. Mirrors `AccountsCard.vue`.
 */
@Composable
internal fun AccountsCard(groups: List<Pair<String, List<Account>>>, mixed: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(stringResource(R.string.dashboard_accounts), style = MaterialTheme.typography.titleLarge)

            groups.forEach { (type, accounts) ->
                if (type.isNotEmpty()) {
                    Text(
                        type,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AccountLogo(account.name, account.logoUrl, account.logoInvertDark, size = 24)
                        Text(account.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        MoneyText(account.balance, currency = account.currency, tone = MoneyTone.SIGNED_ALERT, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (mixed) {
                Text(
                    stringResource(R.string.dashboard_mixed_currencies),
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
