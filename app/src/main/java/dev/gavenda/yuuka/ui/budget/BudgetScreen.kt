package dev.gavenda.yuuka.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.IncomePlanMode
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BudgetScreen(modifier: Modifier = Modifier, viewModel: BudgetViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savingKeys by viewModel.savingKeys.collectAsStateWithLifecycle()
    val visibility = koinInject<AmountVisibility>()
    val snackbarHostState = LocalSnackbarHostState.current
    val isPhp = state.currency == "PHP"
    val compactAmounts = rememberIsWideLayout()

    var incomeEditing by remember { mutableStateOf(false) }
    var incomeMode by remember { mutableStateOf(IncomePlanMode.gross) }
    var incomeDraft by remember { mutableStateOf("") }

    LaunchedEffect(viewModel) {
        viewModel.mutationErrors.collect { message -> snackbarHostState.showSnackbar(message) }
    }

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
                Card(colors = yuukaCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.planned_income_label).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )

                        if (incomeEditing) {
                            val savingIncome = BudgetViewModel.INCOME_KEY in savingKeys
                            // setIncomePlan is fire-and-forget, so the row stays open (showing the
                            // spinner) until the save actually finishes rather than collapsing on tap.
                            var incomeSaveStarted by remember { mutableStateOf(false) }
                            LaunchedEffect(savingIncome) {
                                if (savingIncome) incomeSaveStarted = true else if (incomeSaveStarted) incomeEditing = false
                            }

                            Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                                androidx.compose.runtime.CompositionLocalProvider(
                                    androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineSmall,
                                ) {
                                    DenseOutlinedTextField(
                                        value = incomeDraft,
                                        onValueChange = { incomeDraft = it },
                                        placeholder = if (usingGross) stringResource(R.string.placeholder_gross_income) else stringResource(R.string.placeholder_amount_decimal),
                                        prefix = currencySymbol(state.currency),
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !savingIncome,
                                        onClear = { incomeDraft = "" },
                                    )
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    androidx.compose.material3.TextButton(
                                        enabled = !savingIncome,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            incomeEditing = false
                                        },
                                    ) {
                                        Text(stringResource(R.string.action_cancel))
                                    }
                                    androidx.compose.material3.Button(
                                        enabled = !savingIncome,
                                        modifier = Modifier.weight(1f),
                                        onClick = {
                                            val amount = if (incomeDraft.isBlank()) 0L else parseMoney(incomeDraft)
                                            if (amount == null || amount < 0) return@Button
                                            val toSave = if (usingGross && amount > 0) computeNetPay(amount).netPay else amount
                                            viewModel.setIncomePlan(toSave, if (usingGross) IncomePlanMode.gross else IncomePlanMode.fixed, if (usingGross) amount else null)
                                        },
                                    ) {
                                        if (savingIncome) {
                                            MutationLoadingIndicator()
                                        } else {
                                            Text(stringResource(R.string.action_save))
                                        }
                                    }
                                }

                            }
                        } else {
                            TextButton(onClick = { incomeEditing = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    if (usingGross) {
                                        if (grossDraft > 0) visibility.displayMoney(grossDraft, "PHP") else stringResource(R.string.set_gross_income)
                                    } else {
                                        if (state.plannedIncome > 0) visibility.displayMoney(state.plannedIncome, state.currency) else stringResource(R.string.set_income)
                                    },
                                    style = MaterialTheme.typography.headlineMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }


                        if (isPhp) {
                            ConnectedButtonGroup(
                                options = IncomePlanMode.entries,
                                selected = incomeMode,
                                onSelect = { incomeMode = it; incomeEditing = false },
                                label = { if (it == IncomePlanMode.gross) stringResource(R.string.income_mode_gross) else stringResource(R.string.label_fixed) },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        Text(
                            if (usingGross) stringResource(R.string.gross_income_hint) else stringResource(R.string.net_income_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )

                        if (netPayBreakdown != null) {
                            Text(stringResource(R.string.monthly_contributions), style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
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

                        if (state.incomeBreakdown.isNotEmpty()) {
                            Text(stringResource(R.string.category_kind_income), style = MaterialTheme.typography.labelMedium, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                state.incomeBreakdown.forEach { entry ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(entry.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        MoneyText(entry.actual, currency = state.currency, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                StatCarousel(
                    buildList {
                        add(StatItem(stringResource(R.string.label_planned), state.totalPlanned, state.currency, caption = stringResource(R.string.across_expense_categories)))
                        add(
                            StatItem(
                                stringResource(R.string.label_spent),
                                state.totalActual,
                                state.currency,
                                caption = if (state.totalPlanned > 0) stringResource(R.string.percent_of_plan, percentOf(state.totalActual, state.totalPlanned)) else stringResource(R.string.no_plan_set),
                            ),
                        )
                        if (netPayBreakdown != null) {
                            add(
                                StatItem(
                                    stringResource(R.string.net_pay),
                                    netPayBreakdown.netPay,
                                    "PHP",
                                    caption = stringResource(R.string.used_as_planned_income),
                                    compact = compactAmounts,
                                ),
                            )
                        }
                        if (state.plannedIncome > 0) {
                            add(
                                StatItem(
                                    stringResource(R.string.allocated),
                                    state.totalAllocated,
                                    state.currency,
                                    caption = stringResource(R.string.planned_across_categories),
                                    compact = compactAmounts,
                                ),
                            )
                            add(
                                StatItem(
                                    stringResource(R.string.unallocated),
                                    state.unallocatedIncome,
                                    state.currency,
                                    // Unclamped, unlike percentOf: over-allocating reads as a negative share.
                                    caption = stringResource(
                                        R.string.percent_of_income_unallocated,
                                        Math.round(state.unallocatedIncome.toDouble() / state.plannedIncome.toDouble() * 100).toInt(),
                                    ),
                                    signed = true,
                                    compact = compactAmounts,
                                ),
                            )
                        }
                    },
                )
            }

            if (!state.hasAnyCategories) {
                item { EmptyState(stringResource(R.string.no_categories_yet), description = stringResource(R.string.budget_empty_description)) }
            }

            if (state.expenseBreakdown.isNotEmpty()) {
                item { Text(stringResource(R.string.category_kind_expense), style = MaterialTheme.typography.titleSmall) }
                items(state.expenseBreakdown) { entry ->
                    BudgetRow(entry, state.currency, entry.categoryId in savingKeys, viewModel::setBudgetAmount, viewModel::setBudgetPercent)
                }
            }

            if (state.cashflowBreakdown.isNotEmpty()) {
                item { Text(stringResource(R.string.category_kind_cashflow), style = MaterialTheme.typography.titleSmall) }
                items(state.cashflowBreakdown) { entry ->
                    BudgetRow(entry, state.currency, entry.categoryId in savingKeys, viewModel::setBudgetAmount, viewModel::setBudgetPercent)
                }
            }
        }
    }
}

@Composable
private fun BudgetRow(
    entry: CategoryBreakdown,
    currency: String,
    saving: Boolean,
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
    // A fresh form each time the row is opened, so an earlier attempt's errors do not greet the next one.
    val form = remember(editing) { FormValidation() }
    val amountPlaceholder = stringResource(R.string.placeholder_amount_decimal)
    val percentPlaceholder = stringResource(R.string.placeholder_percent_zero)

    // setBudgetAmount/Percent is fire-and-forget, so this row stays open (showing the spinner)
    // until saving actually finishes rather than collapsing the instant Save is tapped.
    var saveStarted by remember { mutableStateOf(false) }
    LaunchedEffect(saving) {
        if (saving) saveStarted = true else if (saveStarted) { editing = false; saveStarted = false }
    }

    Card(colors = yuukaCardColors(), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    visibility.displayMoney(entry.actual, currency) + if (entry.planned > 0) " / ${visibility.displayMoney(entry.planned, currency)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (editing) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    val modes = listOf("amount" to currency, "percent" to "%")
                    ConnectedButtonGroup(
                        options = modes,
                        selected = modes.first { it.first == mode },
                        onSelect = { (value, _) -> if (mode != value) { mode = value; draft = "" } },
                        label = { it.second },
                        enabled = !saving,
                    )
                    // Empty clears the plan, which is a change like any other; anything else has to be a share, or an amount that is not negative.
                    val plannedAmount = if (mode == "amount") parseMoney(draft) else null
                    val plannedProblem = when {
                        draft.isBlank() -> null
                        mode == "percent" -> if (parsePercent(draft) == null) stringResource(R.string.percent_range_hint) else null
                        plannedAmount == null -> stringResource(R.string.error_budget_number)
                        plannedAmount < 0 -> stringResource(R.string.error_budget_negative)
                        else -> null
                    }
                    val plannedField = form.field("planned", plannedProblem)
                    DenseOutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = if (mode == "percent") percentPlaceholder else amountPlaceholder,
                        // An empty string rather than null keeps the prefix slot always present, so
                        // M3's focus/empty-driven fade animation doesn't get stuck after the slot is
                        // added and removed across mode switches (it should only ever be added once).
                        prefix = if (mode == "amount") currencySymbol(currency) else "",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        enabled = !saving,
                        field = plannedField,
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(
                            enabled = !saving,
                            modifier = Modifier.weight(1f),
                            onClick = { editing = false },
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        androidx.compose.material3.Button(
                            enabled = !saving && form.valid(plannedField),
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (draft.isBlank()) {
                                    onSetAmount(entry.categoryId, 0)
                                } else if (mode == "percent") {
                                    parsePercent(draft)?.let { onSetPercent(entry.categoryId, it) }
                                } else {
                                    plannedAmount?.let { onSetAmount(entry.categoryId, it) }
                                }
                            },
                        ) {
                            if (saving) {
                                MutationLoadingIndicator()
                            } else {
                                Text(stringResource(R.string.action_save))
                            }
                        }
                    }
                }
            } else {
                TextButton(
                    onClick = {
                        mode = if (entry.plannedPercent != null) "percent" else "amount"
                        draft = if (entry.plannedPercent != null) entry.plannedPercent.toString() else if (entry.planned > 0) toDecimalString(entry.planned) else ""
                        editing = true
                    },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        when {
                            entry.plannedPercent != null -> "${entry.plannedPercent}%"
                            entry.planned > 0 -> visibility.displayMoney(entry.planned, currency)
                            else -> stringResource(R.string.set_a_budget)
                        },
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        entry.planned <= 0 -> if (entry.actual > 0) stringResource(R.string.unbudgeted) else stringResource(R.string.no_budget_set)
                        over -> stringResource(R.string.amount_over, visibility.displayMoney(-entry.remaining, currency))
                        else -> stringResource(R.string.amount_left, visibility.displayMoney(entry.remaining, currency))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                BudgetProgressRing(percent = percent, color = statusColor(health))
            }

        }
    }
}

@Composable
private fun BudgetProgressRing(percent: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { percent / 100f },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            amplitude =  { progress ->
                // Sets the amplitude 0 when completed.
                if (progress >= 1) {
                    0f
                } else {
                    1f
                }
            }
        )
        Text("$percent%", style = MaterialTheme.typography.labelSmall)
    }
}
