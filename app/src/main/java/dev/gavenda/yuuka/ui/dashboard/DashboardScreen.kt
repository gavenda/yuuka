package dev.gavenda.yuuka.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.CategoryScope
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.TransactionRow
import dev.gavenda.yuuka.domain.formatDate
import dev.gavenda.yuuka.domain.formatTime
import dev.gavenda.yuuka.domain.monthSeries
import dev.gavenda.yuuka.domain.rankAndFold
import dev.gavenda.yuuka.ui.common.CategoryBarList
import dev.gavenda.yuuka.ui.common.DailySpendChart
import dev.gavenda.yuuka.ui.common.EmptyState
import dev.gavenda.yuuka.ui.common.MoneyText
import dev.gavenda.yuuka.ui.common.MoneyTone
import dev.gavenda.yuuka.ui.common.MonthSwitcher
import dev.gavenda.yuuka.ui.common.ScreenStatus
import dev.gavenda.yuuka.ui.common.StatCard
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

            item { StatCard("Net worth", state.summary?.netWorth ?: 0, currency = state.currency, hero = true, onClick = {}) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Income", state.summary?.income ?: 0, Modifier.weight(1f), currency = state.currency, onClick = {})
                    StatCard("Spent", state.summary?.expenses ?: 0, Modifier.weight(1f), currency = state.currency, onClick = {})
                }
            }

            item {
                val expenseBreakdown = state.summary?.categories.orEmpty().filter { it.appliesTo == CategoryScope.standard && it.kind == CategoryKind.expense }
                val unspent = expenseBreakdown.sumOf { maxOf(0, it.remaining) }
                val overspent = expenseBreakdown.sumOf { minOf(0, it.remaining) }

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        "Net this month",
                        state.summary?.net ?: 0,
                        currency = state.currency,
                        signed = true,
                        caption = if ((state.summary?.net ?: 0) >= 0) "Saved" else "Overspent",
                        onClick = {},
                    )
                    StatCard("Budget remaining", unspent, currency = state.currency, caption = "Across budgeted categories", onClick = {})
                    StatCard(
                        "Over budget",
                        overspent,
                        currency = state.currency,
                        signed = true,
                        caption = if (overspent < 0) "Needs attention" else "Nothing overspent",
                        onClick = {},
                    )
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Spending by day", style = MaterialTheme.typography.titleSmall)
                        val series = monthSeries(state.month, state.summary?.dailySpend.orEmpty())
                        val total = series.sumOf { it.amount }
                        val spentDays = series.count { it.amount > 0 }
                        if (total > 0) {
                            Text(
                                "${visibility.displayMoney(total, state.currency)} across $spentDays ${if (spentDays == 1) "day" else "days"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            DailySpendChart(series, modifier = Modifier.padding(top = 12.dp))
                        } else {
                            Text(
                                "No spending recorded this month.",
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
                        Text("Where the money went", style = MaterialTheme.typography.titleSmall)
                        val entries = rankAndFold(state.summary?.categories.orEmpty().filter { it.appliesTo == CategoryScope.standard && it.kind == CategoryKind.expense }, 8)
                        if (entries.isEmpty()) {
                            Text(
                                "Nothing recorded this month.",
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
                    Text("Recent activity", style = MaterialTheme.typography.titleSmall)
                    TextButton(
                        onClick = onViewAllTransactions,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    ) { Text("View all") }
                }
            }

            if (state.recentRows.isEmpty()) {
                item { EmptyState("Nothing recorded this month yet") }
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
                    Text(row.payee.ifBlank { "${row.fromAccountName} → ${row.toAccountName}" }, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        listOfNotNull(formatDate(row.leg.occurredOn), formatTime(row.leg.occurredOn)).joinToString(" · ") + " · ${row.fromAccountName} → ${row.toAccountName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                MoneyText(row.amount, tone = MoneyTone.TRANSFER, currency = currency)
            }

            is TransactionRow.Single -> {
                val transaction = row.transaction
                Column(Modifier.weight(1f)) {
                    Text(transaction.payee.ifBlank { transaction.categoryName ?: "Uncategorized" }, style = MaterialTheme.typography.bodyMedium)
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
