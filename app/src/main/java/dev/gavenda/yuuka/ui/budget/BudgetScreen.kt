package dev.gavenda.yuuka.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.IncomePlanMode
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.BudgetHealth
import dev.gavenda.yuuka.domain.budgetStatus
import dev.gavenda.yuuka.domain.computeNetPay
import dev.gavenda.yuuka.domain.parseMoney
import dev.gavenda.yuuka.domain.parsePercent
import dev.gavenda.yuuka.domain.percentOf
import dev.gavenda.yuuka.domain.statusColor
import dev.gavenda.yuuka.domain.toDecimalString
import dev.gavenda.yuuka.ui.common.DenseOutlinedTextField
import dev.gavenda.yuuka.ui.common.EmptyState
import dev.gavenda.yuuka.ui.common.MonthSwitcher
import dev.gavenda.yuuka.ui.common.MoneyText
import dev.gavenda.yuuka.ui.common.StatCard
import dev.gavenda.yuuka.ui.common.rememberIsWideLayout
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BudgetScreen(modifier: Modifier = Modifier, viewModel: BudgetViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val visibility = koinInject<AmountVisibility>()
    val isPhp = state.currency == "PHP"
    val compactAmounts = rememberIsWideLayout()

    var incomeEditing by remember { mutableStateOf(false) }
    var incomeMode by remember { mutableStateOf(IncomePlanMode.gross) }
    var incomeDraft by remember { mutableStateOf("") }

    LaunchedEffect(state.summary) {
        incomeMode = state.plannedIncomeMode
        incomeDraft = if (isPhp && incomeMode == IncomePlanMode.gross) {
            state.plannedIncomeGrossAmount?.let { toDecimalString(it) } ?: ""
        } else {
            if (state.plannedIncome > 0) toDecimalString(state.plannedIncome) else ""
        }
    }

    val usingGross = isPhp && incomeMode == IncomePlanMode.gross
    val grossDraft = if (incomeDraft.isBlank()) 0L else (parseMoney(incomeDraft) ?: 0L).coerceAtLeast(0)
    val netPayBreakdown = if (usingGross) computeNetPay(grossDraft) else null

    Column(modifier = modifier.fillMaxWidth()) {
        MonthSwitcher(month = state.month, onMonthChange = viewModel::setMonth, modifier = Modifier.fillMaxWidth().padding(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Planned income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (usingGross) "Enter your gross monthly pay — the take-home net is what you budget from." else "Set what you expect to bring in, then budget a category as a percentage of it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        if (isPhp) {
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    incomeMode == IncomePlanMode.gross,
                                    onClick = { incomeMode = IncomePlanMode.gross; incomeEditing = false },
                                    label = { Text("Gross", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                    modifier = Modifier.weight(1f),
                                )
                                FilterChip(
                                    incomeMode == IncomePlanMode.fixed,
                                    onClick = { incomeMode = IncomePlanMode.fixed; incomeEditing = false },
                                    label = { Text("Fixed", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (incomeEditing) {
                            Row(
                                modifier = Modifier.padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                DenseOutlinedTextField(
                                    value = incomeDraft,
                                    onValueChange = { incomeDraft = it },
                                    placeholder = if (usingGross) "Gross 0.00" else "0.00",
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = {
                                    val amount = if (incomeDraft.isBlank()) 0L else parseMoney(incomeDraft)
                                    if (amount == null || amount < 0) return@TextButton
                                    val toSave = if (usingGross && amount > 0) computeNetPay(amount).netPay else amount
                                    viewModel.setIncomePlan(toSave, if (usingGross) IncomePlanMode.gross else IncomePlanMode.fixed, if (usingGross) amount else null)
                                    incomeEditing = false
                                }) { Text("Save") }
                            }
                        } else {
                            TextButton(onClick = { incomeEditing = true }, modifier = Modifier.padding(top = 8.dp)) {
                                Text(
                                    if (usingGross) {
                                        if (grossDraft > 0) visibility.displayMoney(grossDraft, "PHP") else "Set gross income"
                                    } else {
                                        if (state.plannedIncome > 0) visibility.displayMoney(state.plannedIncome, state.currency) else "Set income"
                                    },
                                )
                            }
                        }

                        if (netPayBreakdown != null) {
                            Text("Monthly contributions", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 16.dp))
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                netPayBreakdown.contributions.forEach { contribution ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(contribution.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        MoneyText(contribution.amount, currency = "PHP", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (netPayBreakdown != null) {
                item {
                    StatCard(
                        "Net pay",
                        netPayBreakdown.netPay,
                        currency = "PHP",
                        caption = "Used as planned income",
                        compact = compactAmounts,
                        onClick = {},
                    )
                }
            }

            if (state.plannedIncome > 0) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            "Allocated",
                            state.totalAllocated,
                            currency = state.currency,
                            caption = "Planned across categories",
                            compact = compactAmounts,
                            onClick = {},
                        )
                        StatCard(
                            "Unallocated",
                            state.unallocatedIncome,
                            currency = state.currency,
                            signed = true,
                            compact = compactAmounts,
                            onClick = {},
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Planned", state.totalPlanned, currency = state.currency, caption = "Across expense categories", onClick = {})
                    StatCard(
                        "Spent",
                        state.totalActual,
                        currency = state.currency,
                        caption = if (state.totalPlanned > 0) "${percentOf(state.totalActual, state.totalPlanned)}% of plan" else "No plan set",
                        onClick = {},
                    )
                }
            }

            if (!state.hasAnyCategories) {
                item { EmptyState("No categories yet", description = "Budgets are set per category, so create a few first.") }
            }

            if (state.expenseBreakdown.isNotEmpty()) {
                item { Text("Expense", style = MaterialTheme.typography.titleSmall) }
                items(state.expenseBreakdown) { entry -> BudgetRow(entry, state.currency, viewModel::setBudgetAmount, viewModel::setBudgetPercent) }
            }

            if (state.cashflowBreakdown.isNotEmpty()) {
                item { Text("Cashflow", style = MaterialTheme.typography.titleSmall) }
                items(state.cashflowBreakdown) { entry -> BudgetRow(entry, state.currency, viewModel::setBudgetAmount, viewModel::setBudgetPercent) }
            }

            if (state.incomeBreakdown.isNotEmpty()) {
                item { Text("Income", style = MaterialTheme.typography.titleSmall) }
                items(state.incomeBreakdown) { entry ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                        Text(visibility.displayMoney(entry.actual, state.currency), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    entry: CategoryBreakdown,
    currency: String,
    onSetAmount: (String, Long) -> Unit,
    onSetPercent: (String, Double) -> Unit,
) {
    val visibility = koinInject<AmountVisibility>()
    val health = budgetStatus(entry.actual, entry.planned)
    val percent = percentOf(entry.actual, entry.planned)
    val over = entry.planned > 0 && entry.remaining < 0

    var editing by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(if (entry.plannedPercent != null) "percent" else "amount") }
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                visibility.displayMoney(entry.actual, currency) + if (entry.planned > 0) " / ${visibility.displayMoney(entry.planned, currency)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(6.dp)
                    .background(androidx.compose.ui.graphics.Color(statusColor(health)), RoundedCornerShape(3.dp)),
            )
        }

        Text(
            when {
                entry.planned <= 0 -> if (entry.actual > 0) "Unbudgeted" else "No budget set"
                over -> "${visibility.displayMoney(-entry.remaining, currency)} over"
                else -> "${visibility.displayMoney(entry.remaining, currency)} left"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (editing) {
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                FilterChip(mode == "amount", onClick = { mode = "amount" }, label = { Text(currency) })
                FilterChip(mode == "percent", onClick = { mode = "percent" }, label = { Text("%") })
                DenseOutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = if (mode == "percent") "0" else "0.00",
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = {
                    if (draft.isBlank()) {
                        onSetAmount(entry.categoryId, 0)
                    } else if (mode == "percent") {
                        parsePercent(draft)?.let { onSetPercent(entry.categoryId, it) }
                    } else {
                        parseMoney(draft)?.takeIf { it >= 0 }?.let { onSetAmount(entry.categoryId, it) }
                    }
                    editing = false
                }) { Text("Save") }
            }
        } else {
            TextButton(onClick = {
                mode = if (entry.plannedPercent != null) "percent" else "amount"
                draft = if (entry.plannedPercent != null) entry.plannedPercent.toString() else if (entry.planned > 0) toDecimalString(entry.planned) else ""
                editing = true
            }, modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    when {
                        entry.plannedPercent != null -> "${entry.plannedPercent}% · ${visibility.displayMoney(entry.planned, currency)}"
                        entry.planned > 0 -> visibility.displayMoney(entry.planned, currency)
                        else -> "Set budget"
                    },
                )
            }
        }
    }
}
