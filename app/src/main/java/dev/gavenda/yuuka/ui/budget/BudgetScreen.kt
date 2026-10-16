package dev.gavenda.yuuka.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.CategoryBreakdown
import dev.gavenda.yuuka.data.model.IncomePlanMode
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.*
import kotlin.math.roundToInt
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.graphics.Color
import dev.gavenda.yuuka.data.model.CategoryKind
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun BudgetScreen(modifier: Modifier = Modifier, viewModel: BudgetViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BudgetScreenContent(
        state = state,
        modifier = modifier,
        onMonthChange = viewModel::setMonth,
        onSetIncomePlan = viewModel::setIncomePlan,
        onSetBudgetAmount = viewModel::setBudgetAmount,
        onSetBudgetPercent = viewModel::setBudgetPercent,
    )
}

/** The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model. */
@Composable
internal fun BudgetScreenContent(
    state: BudgetUiState,
    modifier: Modifier = Modifier,
    onMonthChange: (String) -> Unit = {},
    onSetIncomePlan: suspend (Long, IncomePlanMode, Long?) -> Unit = { _, _, _ -> },
    onSetBudgetAmount: suspend (String, Long) -> Unit = { _, _ -> },
    onSetBudgetPercent: suspend (String, Double) -> Unit = { _, _ -> },
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    val visibility = koinInject<AmountVisibility>()
    val isPhp = state.currency == "PHP"

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

    // Scrolling down gives the page the bar's height back; the first scroll up returns it.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.appBarScroll(scrollBehavior),
        topBar = { MonthTopBar(
                month = state.month,
                onMonthChange = onMonthChange,
                scrollBehavior = scrollBehavior,
                title = stringResource(R.string.destination_budget),
            ) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            // Nothing stands between the bar and the first card, so the list pads its own top.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.planned_income_label).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )

                        TextButton(
                                onClick = { incomeEditing = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
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
                                        Text(contribution.label, style = MaterialTheme.typography.bodyMedium)
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
                                        Text(entry.name, style = MaterialTheme.typography.bodyMedium)
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
                        add(StatItem(stringResource(R.string.label_planned), state.totalPlanned, state.currency, caption = stringResource(R.string.across_expense_categories), watermark = true))
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
                                        (state.unallocatedIncome.toDouble() / state.plannedIncome.toDouble() * 100).roundToInt(),
                                    ),
                                    signed = true,
                                ),
                            )
                        }
                    },
                    tone = StatTone.Tertiary,
                )
            }

            if (!state.hasAnyCategories) {
                item { EmptyState(stringResource(R.string.budget_empty_title), YuukaMood.Stern, description = stringResource(R.string.budget_empty_description)) }
            }

            if (state.expenseBreakdown.isNotEmpty()) {
                item { Text(stringResource(R.string.category_kind_expense), style = MaterialTheme.typography.titleSmall) }
                items(state.expenseBreakdown, key = { it.categoryId }) { entry ->
                    BudgetRow(entry, state.currency, busy, onSetBudgetAmount, onSetBudgetPercent)
                }
            }

            if (state.cashflowBreakdown.isNotEmpty()) {
                item { Text(stringResource(R.string.category_kind_cashflow), style = MaterialTheme.typography.titleSmall) }
                items(state.cashflowBreakdown, key = { it.categoryId }) { entry ->
                    BudgetRow(entry, state.currency, busy, onSetBudgetAmount, onSetBudgetPercent)
                }
            }
        }
    }

    if (incomeEditing) {
        PlannedIncomeDialog(
            initial = incomeDraft,
            currency = if (usingGross) "PHP" else state.currency,
            usingGross = usingGross,
            saving = busy.isBusy(INCOME_KEY),
            onSave = { draft, amount ->
                incomeDraft = draft
                val toSave = if (usingGross && amount > 0) computeNetPay(amount).netPay else amount
                busy.run(INCOME_KEY, snackbarHostState, onSuccess = { incomeEditing = false }) {
                    onSetIncomePlan(toSave, if (usingGross) IncomePlanMode.gross else IncomePlanMode.fixed, if (usingGross) amount else null)
                }
            },
            onDismiss = { incomeEditing = false },
        )
    }
}

/** The busy key of a planned-income save, beside the category ids that key a plan's. */
private const val INCOME_KEY = "income"

/** Planned income in a basic dialog of its one field; it stays open, spinning, until the save finishes. */
@Composable
private fun PlannedIncomeDialog(
    initial: String,
    currency: String,
    usingGross: Boolean,
    saving: Boolean,
    onSave: (String, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val amount = if (draft.isBlank()) 0L else parseMoney(draft)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(stringResource(R.string.planned_income_label)) },
        text = {
            DenseOutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = if (usingGross) stringResource(R.string.placeholder_gross_income) else stringResource(R.string.placeholder_amount_decimal),
                prefix = currencySymbol(currency),
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                onClear = { draft = "" },
            )
        },
        confirmButton = {
            Button(
                enabled = !saving && amount != null && amount >= 0,
                onClick = { amount?.let { onSave(draft, it) } },
            ) {
                if (saving) MutationLoadingIndicator() else Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BudgetRow(
    entry: CategoryBreakdown,
    currency: String,
    busy: BusyState,
    onSetAmount: suspend (String, Long) -> Unit,
    onSetPercent: suspend (String, Double) -> Unit,
) {
    val visibility = koinInject<AmountVisibility>()
    val snackbarHostState = LocalSnackbarHostState.current
    val health = budgetStatus(entry.actual, entry.planned)
    val percent = percentOf(entry.actual, entry.planned)
    val over = entry.planned > 0 && entry.remaining < 0

    var editing by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(entry.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    visibility.displayMoney(entry.actual, currency) + if (entry.planned > 0) " / ${visibility.displayMoney(entry.planned, currency)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            TextButton(
                    onClick = { editing = true },
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
                    color = if (over) MaterialTheme.colorScheme.error else Color.Unspecified,
                    modifier = Modifier.weight(1f),
                )
                BudgetProgressRing(percent = percent, color = statusColor(health))
            }

        }
    }

    if (editing) {
        BudgetDialog(
            entry = entry,
            currency = currency,
            saving = busy.isBusy(entry.categoryId),
            onSave = { save -> busy.run(entry.categoryId, snackbarHostState, onSuccess = { editing = false }, action = save) },
            onSetAmount = onSetAmount,
            onSetPercent = onSetPercent,
            onDismiss = { editing = false },
        )
    }
}

/** A category's plan in a basic dialog: the amount-or-share switch over its one field. */
@Composable
private fun BudgetDialog(
    entry: CategoryBreakdown,
    currency: String,
    saving: Boolean,
    /** Runs the save, closing the dialog once it has gone through; it stays open, spinning, until then. */
    onSave: (suspend () -> Unit) -> Unit,
    onSetAmount: suspend (String, Long) -> Unit,
    onSetPercent: suspend (String, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember { mutableStateOf(if (entry.plannedPercent != null) "percent" else "amount") }
    var draft by remember {
        mutableStateOf(if (entry.plannedPercent != null) entry.plannedPercent.toString() else if (entry.planned > 0) toDecimalString(entry.planned) else "")
    }
    val form = remember { FormValidation() }

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

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text(entry.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val modes = listOf("amount" to currency, "percent" to "%")
                ConnectedButtonGroup(
                    options = modes,
                    selected = modes.first { it.first == mode },
                    onSelect = { (value, _) -> if (mode != value) { mode = value; draft = "" } },
                    label = { it.second },
                    enabled = !saving,
                )
                DenseOutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = if (mode == "percent") stringResource(R.string.placeholder_percent_zero) else stringResource(R.string.placeholder_amount_decimal),
                    // An empty string rather than null keeps the prefix slot always present, so
                    // M3's focus/empty-driven fade animation doesn't get stuck after the slot is
                    // added and removed across mode switches (it should only ever be added once).
                    prefix = if (mode == "amount") currencySymbol(currency) else "",
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    field = plannedField,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !saving && form.valid(plannedField),
                onClick = {
                    val percent = if (mode == "percent") parsePercent(draft) else null
                    val amount = if (draft.isBlank()) 0L else plannedAmount
                    onSave {
                        when {
                            percent != null && draft.isNotBlank() -> onSetPercent(entry.categoryId, percent)
                            amount != null -> onSetAmount(entry.categoryId, amount)
                        }
                    }
                },
            ) {
                if (saving) MutationLoadingIndicator() else Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun BudgetProgressRing(percent: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularWavyProgressIndicator(
            progress = { percent / 100f },
            color = color,
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

@Preview(showBackground = true)
@Composable
private fun BudgetScreenPreview() {
    ScreenPreview {
        val categories = PreviewData.summary.categories
        BudgetScreenContent(
            BudgetUiState(
                month = PreviewData.MONTH,
                summary = PreviewData.summary,
                expenseBreakdown = categories.filter { it.kind == CategoryKind.expense },
                incomeBreakdown = categories.filter { it.kind == CategoryKind.income },
                cashflowBreakdown = categories.filter { it.kind == CategoryKind.transfer },
                plannedIncome = PreviewData.summary.plannedIncome,
                unspent = 415_000,
                overspent = -154_900,
            ),
        )
    }
}
