package dev.gavenda.yuuka.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.TransactionRow
import dev.gavenda.yuuka.domain.formatLongDate
import dev.gavenda.yuuka.domain.formatTime
import dev.gavenda.yuuka.ui.common.ActionIcon
import dev.gavenda.yuuka.ui.common.ActionIconButton
import dev.gavenda.yuuka.ui.common.EmptyState
import dev.gavenda.yuuka.ui.common.MoneyText
import dev.gavenda.yuuka.ui.common.MoneyTone
import dev.gavenda.yuuka.ui.common.MonthSwitcher
import dev.gavenda.yuuka.ui.common.ScreenStatus
import dev.gavenda.yuuka.ui.common.SwipeToRevealActions
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(modifier: Modifier = Modifier, viewModel: TransactionsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val payees by viewModel.payeeRepository.payees.collectAsStateWithLifecycle(initialValue = emptyList())

    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = { FloatingActionButton(onClick = viewModel::openCreate) { Icon(Icons.Filled.Add, contentDescription = "Add transaction") } },
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
                            label = { Text("Search payee or notes") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterDropdown(
                                modifier = Modifier.weight(1f),
                                label = "Account",
                                selectedLabel = state.accounts.firstOrNull { it.id == state.accountFilter }?.name ?: "All accounts",
                                options = listOf(null to "All accounts") + state.accounts.map { it.id to it.name },
                                onSelect = viewModel::setAccountFilter,
                            )
                            FilterDropdown(
                                modifier = Modifier.weight(1f),
                                label = "Category",
                                selectedLabel = when (state.categoryFilter) {
                                    null -> "All categories"
                                    "none" -> "Uncategorized"
                                    else -> state.categories.firstOrNull { it.id == state.categoryFilter }?.name ?: "All categories"
                                },
                                options = listOf(null to "All categories", "none" to "Uncategorized") + state.categories.map { it.id to it.name },
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
                            "No transactions here",
                            modifier = Modifier.padding(16.dp),
                            description = "Nothing matches these filters yet. Add one, or widen the search.",
                        )
                    }
                } else {
                    grouped.forEach { (date, rows) ->
                        item {
                            Text(
                                formatLongDate(date),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
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
                                    Text(if (state.status == ScreenStatus.Loading) "Loading…" else "Load more (${state.loadedCount} of ${state.total})")
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

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (toDelete.transferId != null) "Delete this transfer?" else "Delete this transaction?") },
            text = {
                if (toDelete.transferId != null) Text("Both sides of the transfer will be removed.")
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(toDelete); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
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

@Composable
private fun TransactionRowItem(row: TransactionRow, currency: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val visibility = koinInject<AmountVisibility>()

    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        actions = { ActionIconButton(ActionIcon.DELETE, "Delete", onDelete, danger = true) },
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
        ) {
            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                when (row) {
                    is TransactionRow.Transfer -> {
                        Column(Modifier.weight(1f)) {
                            Text(row.payee.ifBlank { row.categoryName ?: "Transfer" }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${row.fromAccountName} → ${row.toAccountName}" + (formatTime(row.leg.occurredOn)?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(row.amount, tone = MoneyTone.TRANSFER, currency = currency)
                            Text(
                                visibility.displayMoney(row.leg.runningBalance, currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    is TransactionRow.Single -> {
                        val transaction = row.transaction
                        Column(Modifier.weight(1f)) {
                            Text(transaction.payee.ifBlank { transaction.categoryName ?: "Uncategorized" }, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                transaction.accountName.orEmpty() + (formatTime(transaction.occurredOn)?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            MoneyText(transaction.amount, tone = MoneyTone.SIGNED, currency = currency)
                            Text(
                                visibility.displayMoney(transaction.runningBalance, currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
