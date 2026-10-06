package dev.gavenda.yuuka.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DashboardScreen(modifier: Modifier = Modifier, viewModel: DashboardViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreenContent(state = state, modifier = modifier, onMonthChange = viewModel::setMonth)
}

/** The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreenContent(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onMonthChange: (String) -> Unit = {},
) {
    val visibility = koinInject<AmountVisibility>()
    // Scrolling down gives the page the bar's height back; the first scroll up returns it.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        modifier = modifier.appBarScroll(scrollBehavior),
        topBar = { MonthTopBar(month = state.month, onMonthChange = onMonthChange, scrollBehavior = scrollBehavior) },
        
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                // The month switcher used to stand between the bar and the first card; its padding goes here instead.
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
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
                        tone = StatTone.Tertiary,
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.dashboard_spending_by_day), style = MaterialTheme.typography.titleLarge)
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
                                )
                                DailySpendChart(series, modifier = Modifier.padding(top = 12.dp), currency = state.currency)
                            } else {
                                Text(
                                    stringResource(R.string.dashboard_no_spending),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(stringResource(R.string.dashboard_where_money_went), style = MaterialTheme.typography.titleLarge)
                            val entries = rankAndFold(state.summary?.categories.orEmpty().filter { it.kind == CategoryKind.expense }, 8)
                            if (entries.isEmpty()) {
                                Text(
                                    stringResource(R.string.dashboard_nothing_recorded_month),
                                    modifier = Modifier.padding(top = 12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else {
                                CategoryBarList(entries, modifier = Modifier.padding(top = 12.dp), currency = state.currency)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    ScreenPreview {
        DashboardScreenContent(DashboardUiState(month = PreviewData.MONTH, summary = PreviewData.summary))
    }
}
