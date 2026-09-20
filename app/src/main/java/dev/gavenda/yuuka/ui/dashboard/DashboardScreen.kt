package dev.gavenda.yuuka.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(onViewAllTransactions: () -> Unit, modifier: Modifier = Modifier, viewModel: DashboardViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visibility = koinInject<AmountVisibility>()

    Column(modifier = modifier.fillMaxWidth()) {
        MonthSwitcher(month = state.month, onMonthChange = viewModel::setMonth, modifier = Modifier.fillMaxWidth().padding(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val error = (state.status as? ScreenStatus.Error)?.message
            if (error != null) {
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item {
                StatCarousel(
                    listOf(
                        StatItem(stringResource(R.string.net_worth), state.summary?.netWorth ?: 0, state.currency),
                        StatItem(stringResource(R.string.category_kind_income), state.summary?.income ?: 0, state.currency),
                        StatItem(stringResource(R.string.label_spent), state.summary?.expenses ?: 0, state.currency),
                    ),
                )
            }

            item {
                val expenseBreakdown = state.summary?.categories.orEmpty().filter { it.kind == CategoryKind.expense }
                val unspent = expenseBreakdown.sumOf { maxOf(0, it.remaining) }
                val overspent = expenseBreakdown.sumOf { minOf(0, it.remaining) }
                val net = state.summary?.net ?: 0

                StatCarousel(
                    listOf(
                        StatItem(
                            stringResource(R.string.dashboard_net_this_month),
                            net,
                            state.currency,
                            caption = if (net >= 0) stringResource(R.string.dashboard_saved) else stringResource(R.string.dashboard_overspent),
                            signed = true,
                        ),
                        StatItem(
                            stringResource(R.string.dashboard_budget_remaining),
                            unspent,
                            state.currency,
                            caption = stringResource(R.string.dashboard_across_budgeted_categories),
                        ),
                        StatItem(
                            stringResource(R.string.dashboard_over_budget),
                            overspent,
                            state.currency,
                            caption = if (overspent < 0) stringResource(R.string.dashboard_needs_attention) else stringResource(R.string.dashboard_nothing_overspent),
                            signed = true,
                        ),
                    ),
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.dashboard_spending_by_day), style = MaterialTheme.typography.titleSmall)
                        val series = monthSeries(state.month, state.summary?.dailySpend.orEmpty())
                        val total = series.sumOf { it.amount }
                        val spentDays = series.count { it.amount > 0 }
                        if (total > 0) {
                            Text(
                                stringResource(
                                    R.string.dashboard_spend_summary,
                                    visibility.displayMoney(total, state.currency),
                                    pluralStringResource(R.plurals.days_count, spentDays, spentDays),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DailySpendChart(series, modifier = Modifier.padding(top = 12.dp), currency = state.currency)
                        } else {
                            Text(
                                stringResource(R.string.dashboard_no_spending),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(stringResource(R.string.dashboard_where_money_went), style = MaterialTheme.typography.titleSmall)
                        val entries = rankAndFold(state.summary?.categories.orEmpty().filter { it.kind == CategoryKind.expense }, 8)
                        if (entries.isEmpty()) {
                            Text(
                                stringResource(R.string.dashboard_nothing_recorded_month),
                                modifier = Modifier.padding(top = 12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            CategoryBarList(entries, modifier = Modifier.padding(top = 12.dp), currency = state.currency)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.dashboard_recent_activity), style = MaterialTheme.typography.titleSmall)
                    TextButton(
                        onClick = onViewAllTransactions,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    ) { Text(stringResource(R.string.action_view_all)) }
                }
            }

            if (state.recentRows.isEmpty()) {
                item { EmptyState(stringResource(R.string.dashboard_nothing_recorded_month_yet)) }
            } else {
                items(state.recentRows) { row -> RecentActivityRow(row, state.currency) }
            }
        }
    }
}

@Composable
private fun RecentActivityRow(row: TransactionRow, currency: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        when (row) {
            is TransactionRow.Transfer -> {
                Column(Modifier.weight(1f)) {
                    val from = row.fromAccountName.orEmpty()
                    val to = row.toAccountName.orEmpty()
                    if (row.payee.isBlank()) {
                        AccountFlow(from, to, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    } else {
                        Text(row.payee, style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            listOfNotNull(formatDate(row.leg.occurredOn), formatTime(row.leg.occurredOn)).joinToString(" · ") + " · ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AccountFlow(from, to)
                    }
                }
                MoneyText(row.amount, tone = MoneyTone.TRANSFER, currency = currency)
            }

            is TransactionRow.Single -> {
                val transaction = row.transaction
                Column(Modifier.weight(1f)) {
                    Text(transaction.payee.ifBlank { transaction.categoryName ?: stringResource(R.string.category_uncategorized) }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(formatDate(transaction.occurredOn), formatTime(transaction.occurredOn)).joinToString(" · ") + " · ${transaction.accountName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoneyText(transaction.amount, tone = MoneyTone.SIGNED, currency = currency)
            }
        }
    }
}
