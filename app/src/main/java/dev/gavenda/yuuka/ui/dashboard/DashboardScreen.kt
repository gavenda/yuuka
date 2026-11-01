package dev.gavenda.yuuka.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(onOpenInvestments: () -> Unit, modifier: Modifier = Modifier, viewModel: DashboardViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreenContent(state = state, modifier = modifier, onMonthChange = viewModel::setMonth, onOpenInvestments = onOpenInvestments)
}

/** From this page width the sections stand in two columns: the month on the left, where things stand overall on the right. */
private val TwoColumnMinWidth = 840.dp

/**
 * The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model.
 *
 * Four sections, each left out when it has nothing to say: the month on show (one leading figure, the rest of
 * its figures, and the plan), what needs attention, where the month's money went, and where things stand
 * overall. Mirrors `DashboardView.vue`.
 */
@Composable
internal fun DashboardScreenContent(
    state: DashboardUiState,
    modifier: Modifier = Modifier,
    onMonthChange: (String) -> Unit = {},
    onOpenInvestments: () -> Unit = {},
) {
    // Scrolling down gives the page the bar's height back; the first scroll up returns it.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.appBarScroll(scrollBehavior),
        topBar = { MonthTopBar(month = state.month, onMonthChange = onMonthChange, scrollBehavior = scrollBehavior) },
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val twoColumns = maxWidth >= TwoColumnMinWidth
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                val error = (state.status as? ScreenStatus.Error)?.message
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                if (twoColumns) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        // A carousel bleeding through the page's padding would scroll into the gap between the columns.
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
                            MonthSection(state, edgeBleed = 0.dp)
                            AttentionSection(state)
                            BreakdownSection(state)
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SectionSpacing)) {
                            OverallSection(state, onOpenInvestments)
                        }
                    }
                } else {
                    MonthSection(state, edgeBleed = 16.dp)
                    AttentionSection(state)
                    BreakdownSection(state)
                    OverallSection(state, onOpenInvestments)
                }
            }
        }
    }
}

private val SectionSpacing = 32.dp

/** A section's heading over its cards. */
@Composable
private fun DashboardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/**
 * The month on show: what can still be spent each day while it runs with a plan left, and otherwise its net,
 * then its other figures, then how it stands against the plan.
 */
@Composable
private fun MonthSection(state: DashboardUiState, edgeBleed: Dp) {
    val summary = state.summary
    val net = summary?.net ?: 0
    val pace = budgetPace(summary?.categories.orEmpty(), state.month)
    val safe = safeToSpend(pace)
    val visibility = koinInject<AmountVisibility>()

    val rate = savingsRate(net, summary?.income ?: 0)
    val netCaption = when {
        net < 0 -> stringResource(R.string.dashboard_overspent)
        rate != null -> stringResource(R.string.dashboard_saved_share, rate)
        else -> stringResource(R.string.dashboard_saved)
    }

    DashboardSection(formatMonth(state.month)) {
        if (safe != null) {
            StatCard(
                label = stringResource(R.string.dashboard_safe_to_spend),
                amount = safe.perDay,
                modifier = Modifier.fillMaxWidth(),
                currency = state.currency,
                caption = pluralStringResource(R.plurals.dashboard_safe_to_spend_left, safe.daysLeft, visibility.displayMoney(safe.left, state.currency), safe.daysLeft),
                hero = true,
                tone = StatTone.Tertiary,
                watermark = true,
            )
        } else {
            StatCard(
                label = stringResource(R.string.dashboard_net_this_month),
                amount = net,
                modifier = Modifier.fillMaxWidth(),
                currency = state.currency,
                caption = netCaption,
                hero = true,
                tone = StatTone.Tertiary,
                watermark = true,
            )
        }

        val cashflow = summary?.cashflow ?: 0
        val roundUps = summary?.roundUps ?: 0
        StatCarousel(
            buildList {
                add(StatItem(stringResource(R.string.category_kind_income), summary?.income ?: 0, state.currency))
                add(StatItem(stringResource(R.string.label_spent), summary?.expenses ?: 0, state.currency))
                // The net leads the section when there is nothing safe to spend, so it is only one of the rest otherwise.
                if (safe != null) {
                    add(StatItem(stringResource(R.string.dashboard_net_this_month), net, state.currency, signed = true))
                }
                // Only once something has been moved: most months of someone who never files a transfer would read zero.
                if (cashflow > 0) add(StatItem(stringResource(R.string.dashboard_set_aside), cashflow, state.currency, tone = StatTone.Secondary))
                if (roundUps > 0) add(StatItem(stringResource(R.string.dashboard_rounded_up), roundUps, state.currency, tone = StatTone.Secondary))
            },
            edgeBleed = edgeBleed,
        )

        val incomePercent = incomeProgress(summary?.income ?: 0, summary?.plannedIncome ?: 0)
        if (pace != null || incomePercent != null) {
            PlanCard(pace, summary?.income ?: 0, summary?.plannedIncome ?: 0, incomePercent, state.currency)
        }
    }
}

/** What wants doing something about: categories past their plan or heading there, and what is still to post. Left out with none. */
@Composable
private fun AttentionSection(state: DashboardUiState) {
    val categories = state.summary?.categories.orEmpty()
    val over = overBudget(categories)
    val ahead = aheadOfPace(categories, state.month)
    val pace = budgetPace(categories, state.month)
    // Every month after the one under way would just be the whole list again.
    val upcoming = if (state.month == currentMonth()) stillToPost(state.subscriptions, state.month) else emptyList()
    if (over.isEmpty() && ahead.isEmpty() && upcoming.isEmpty()) return

    DashboardSection(stringResource(R.string.dashboard_section_attention)) {
        if (over.isNotEmpty()) OverBudgetCard(over, state.currency)
        if (ahead.isNotEmpty() && pace?.day != null) AheadOfPaceCard(ahead, pace.day, pace.days, state.currency)
        if (upcoming.isNotEmpty()) StillToPostCard(upcoming, state.currency)
    }
}

/** Where the month's money went, day by day, by category and by payee, and where it came from. */
@Composable
private fun BreakdownSection(state: DashboardUiState) {
    val visibility = koinInject<AmountVisibility>()
    val categories = state.summary?.categories.orEmpty()

    DashboardSection(stringResource(R.string.dashboard_section_breakdown)) {
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
                    Text(stringResource(R.string.dashboard_no_spending), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(stringResource(R.string.dashboard_where_money_went), style = MaterialTheme.typography.titleLarge)
                val entries = rankAndFold(categories.filter { it.kind == CategoryKind.expense }, 8)
                if (entries.isEmpty()) {
                    Text(
                        stringResource(R.string.dashboard_nothing_recorded_month),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    CategoryBarList(
                        entries,
                        modifier = Modifier.padding(top = 12.dp),
                        currency = state.currency,
                        changes = state.previous?.let { categoryChanges(categories, it.categories) },
                        comparedTo = state.previous?.let { monthName(it.month) },
                    )
                }
            }
        }

        val payees = state.summary?.topPayees.orEmpty()
        if (payees.isNotEmpty()) TopPayeesCard(payees, state.currency)

        // One source would only repeat the income figure, so it takes two to be worth a chart.
        val sources = rankAndFold(categories.filter { it.kind == CategoryKind.income }, 8)
        if (sources.size >= 2) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.dashboard_where_money_came_from), style = MaterialTheme.typography.titleLarge)
                    CategoryBarList(sources, modifier = Modifier.padding(top = 12.dp), currency = state.currency)
                }
            }
        }
    }
}

/** Where things stand beyond the month: net worth and how it has moved, the months before, the holdings and the accounts. */
@Composable
private fun OverallSection(state: DashboardUiState, onOpenInvestments: () -> Unit) {
    DashboardSection(stringResource(R.string.dashboard_section_overall)) {
        StatCard(
            label = stringResource(R.string.net_worth),
            amount = state.summary?.netWorth ?: 0,
            modifier = Modifier.fillMaxWidth(),
            currency = state.currency,
        )

        // The months up to the one on show; the bars take the last six, which is as many pairs as a phone holds.
        val trendMonths = state.trend?.takeIf { it.month == state.month }?.months.orEmpty()
        if (trendMonths.size >= 2) NetWorthChart(trendMonths, state.currency)
        val barMonths = trendMonths.takeLast(6)
        if (barMonths.size >= 2) IncomeSpendChart(barMonths, state.currency)

        state.investments?.let { InvestmentsCard(it, onOpen = onOpenInvestments) }

        val groups = accountGroups(state.summary?.accounts.orEmpty())
        if (groups.isNotEmpty()) AccountsCard(groups, mixedCurrencies(state.summary?.accounts.orEmpty()))
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    ScreenPreview {
        DashboardScreenContent(DashboardUiState(month = PreviewData.MONTH, summary = PreviewData.summary))
    }
}

@Preview(showBackground = true, widthDp = 1100, heightDp = 900)
@Composable
private fun DashboardScreenWidePreview() {
    ScreenPreview {
        DashboardScreenContent(DashboardUiState(month = PreviewData.MONTH, summary = PreviewData.summary))
    }
}
