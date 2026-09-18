package dev.gavenda.yuuka.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.TransactionRow
import dev.gavenda.yuuka.domain.formatLongDate
import dev.gavenda.yuuka.domain.formatTime
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(modifier: Modifier = Modifier, viewModel: TransactionsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val payees by viewModel.payeeRepository.payees.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = LocalSnackbarHostState.current

    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    val allAccountsLabel = stringResource(R.string.all_accounts)
    val allCategoriesLabel = stringResource(R.string.all_categories)
    val uncategorizedLabel = stringResource(R.string.category_uncategorized)

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::openCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_transaction)) },
            )
        },
    ) { padding ->
        val error = (state.status as? ScreenStatus.Error)?.message
        val grouped = remember(state.rows) { groupByDate(state.rows) }

        Column(modifier = Modifier.padding(padding).fillMaxWidth()) {
            MonthSwitcher(
                month = state.month,
                onMonthChange = viewModel::setMonth,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.searchText,
                            onValueChange = viewModel::setSearchText,
                            label = { Text(stringResource(R.string.search_payee_notes)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterDropdown(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.label_account),
                                selectedLabel = state.accounts.firstOrNull { it.id == state.accountFilter }?.name ?: allAccountsLabel,
                                options = listOf(null to allAccountsLabel) + state.accounts.map { it.id to it.name },
                                onSelect = viewModel::setAccountFilter,
                            )
                            FilterDropdown(
                                modifier = Modifier.weight(1f),
                                label = stringResource(R.string.label_category),
                                selectedLabel = when (state.categoryFilter) {
                                    null -> allCategoriesLabel
                                    "none" -> uncategorizedLabel
                                    else -> state.categories.firstOrNull { it.id == state.categoryFilter }?.name ?: allCategoriesLabel
                                },
                                options = listOf(null to allCategoriesLabel, "none" to uncategorizedLabel) + state.categories.map { it.id to it.name },
                                onSelect = viewModel::setCategoryFilter,
                            )
                        }
                    }
                }

                if (error != null) {
                    item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
                }

                if (state.rows.isEmpty() && state.status != ScreenStatus.Loading) {
                    item {
                        EmptyState(
                            stringResource(R.string.transactions_empty_title),
                            modifier = Modifier.padding(16.dp),
                            description = stringResource(R.string.transactions_empty_description),
                        )
                    }
                } else {
                    grouped.forEach { (date, rows) ->
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    formatLongDate(date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                MoneyText(dailyAccrued(rows), tone = MoneyTone.SIGNED, currency = state.currency, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        items(rows) { row ->
                            TransactionRowItem(
                                row = row,
                                currency = state.currency,
                                onClick = {
                                    when (row) {
                                        is TransactionRow.Transfer -> viewModel.openEdit(row.leg, row.toAccountId)
                                        is TransactionRow.Single -> viewModel.openEdit(row.transaction)
                                    }
                                },
                                onDelete = {
                                    pendingDelete = when (row) {
                                        is TransactionRow.Transfer -> row.leg
                                        is TransactionRow.Single -> row.transaction
                                    }
                                },
                            )
                        }
                    }

                    if (state.hasMore) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        if (state.status == ScreenStatus.Loading) {
                                            stringResource(R.string.loading_ellipsis)
                                        } else {
                                            stringResource(R.string.load_more, state.loadedCount, state.total)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (formState.open) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = viewModel::closeForm, sheetState = sheetState) {
            WithSnackbarOverlay {
                TransactionForm(
                    editing = formState.editing,
                    transferToAccountId = formState.transferToAccountId,
                    accounts = state.accounts.filter { !it.archived },
                    categories = state.categories,
                    payees = payees,
                    defaultAccountId = state.defaultAccountId,
                    submitting = formState.submitting,
                    error = formState.error,
                    onSubmit = viewModel::submit,
                    onCancel = viewModel::closeForm,
                )
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        val deleting = state.deletingId == toDelete.id
        // Deletion is fire-and-forget on the ViewModel, so the dialog stays open (showing the
        // spinner) until deletingId reverts to null, rather than closing the instant it's tapped.
        var started by remember(toDelete.id) { mutableStateOf(false) }
        LaunchedEffect(deleting) {
            if (deleting) started = true else if (started) pendingDelete = null
        }

        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text(if (toDelete.transferId != null) stringResource(R.string.delete_transfer_confirm_title) else stringResource(R.string.delete_transaction_confirm_title)) },
            text = {
                WithSnackbarOverlay {
                    if (toDelete.transferId != null) Text(stringResource(R.string.delete_transfer_body))
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(toDelete) }, enabled = !deleting) {
                    if (deleting) {
                        MutationLoadingIndicator()
                    } else {
                        Text(stringResource(R.string.action_delete))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }, enabled = !deleting) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun groupByDate(rows: List<TransactionRow>): List<Pair<String, List<TransactionRow>>> {
    val groups = LinkedHashMap<String, MutableList<TransactionRow>>()
    for (row in rows) {
        val date = when (row) {
            is TransactionRow.Transfer -> row.leg.occurredOn.take(10)
            is TransactionRow.Single -> row.transaction.occurredOn.take(10)
        }
        groups.getOrPut(date) { mutableListOf() }.add(row)
    }
    return groups.entries.map { it.key to it.value }
}

/** Net change to your accounts' balances for the day — transfers move money between your own accounts, so they don't count, mirroring why [AccountGroup.total] sums balances rather than raw amounts. */
private fun dailyAccrued(rows: List<TransactionRow>): Long =
    rows.filterIsInstance<TransactionRow.Single>().sumOf { it.transaction.amount }

@Composable
private fun TransactionRowItem(row: TransactionRow, currency: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val visibility = koinInject<AmountVisibility>()
    val notes = when (row) {
        is TransactionRow.Transfer -> row.notes
        is TransactionRow.Single -> row.transaction.notes
    }.trim()

    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        actions = { ActionIconButton(ActionIcon.DELETE, stringResource(R.string.action_delete), onDelete, danger = true) },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (row) {
                    is TransactionRow.Transfer -> {
                        val accountFlow = stringResource(R.string.transfer_account_flow, row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
                        val title = if (row.payee.isBlank() || row.payee == accountFlow) stringResource(R.string.category_kind_transfer) else row.payee
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, style = MaterialTheme.typography.bodyMedium)
                            AccountFlow(row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
                            formatTime(row.leg.occurredOn)?.let { time ->
                                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MoneyText(row.amount, tone = MoneyTone.TRANSFER, currency = currency, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                visibility.displayMoney(row.leg.runningBalance, currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            row.categoryName?.let { name -> CategoryLabel(name, row.categoryColor) }
                        }
                    }

                    is TransactionRow.Single -> {
                        val transaction = row.transaction
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(transaction.payee.ifBlank { transaction.categoryName ?: stringResource(R.string.category_uncategorized) }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                transaction.accountName.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            formatTime(transaction.occurredOn)?.let { time ->
                                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MoneyText(transaction.amount, tone = MoneyTone.SIGNED, currency = currency, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                visibility.displayMoney(transaction.runningBalance, currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            transaction.categoryName?.let { name -> CategoryLabel(name, transaction.categoryColor) }
                        }
                    }
                }
            }

            if (notes.isNotEmpty()) {
                HorizontalDivider()
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryLabel(name: String, colorHex: String?) {
    val color = colorHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (color != null) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selectedLabel: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}
