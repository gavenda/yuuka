package dev.gavenda.yuuka.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.*
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.UNCATEGORIZED_FILTER_ID
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.TransactionRow
import dev.gavenda.yuuka.domain.formatLongDate
import dev.gavenda.yuuka.domain.formatTime
import dev.gavenda.yuuka.ui.common.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.graphics.Color
import dev.gavenda.yuuka.data.model.Payee
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(modifier: Modifier = Modifier, viewModel: TransactionsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val formState by viewModel.formState.collectAsStateWithLifecycle()
    val payees by viewModel.payeeRepository.payees.collectAsStateWithLifecycle(initialValue = emptyList())
    val snackbarHostState = LocalSnackbarHostState.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    TransactionsScreenContent(
        state = state,
        modifier = modifier,
        formState = formState,
        payees = payees,
        onMonthChange = viewModel::setMonth,
        onSearchTextChange = viewModel::setSearchText,
        onAccountFilterChange = viewModel::setAccountFilter,
        onCategoryFilterChange = viewModel::setCategoryFilter,
        onTagFilterChange = viewModel::setTagFilter,
        onLoadMore = viewModel::loadMore,
        onOpenCreate = viewModel::openCreate,
        onOpenEdit = viewModel::openEdit,
        onCloseForm = viewModel::closeForm,
        onSubmit = viewModel::submit,
        onDelete = viewModel::delete,
    )
}

/** The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionsScreenContent(
    state: TransactionsUiState,
    modifier: Modifier = Modifier,
    formState: TransactionFormState = TransactionFormState(),
    payees: List<Payee> = emptyList(),
    onMonthChange: (String) -> Unit = {},
    onSearchTextChange: (String) -> Unit = {},
    onAccountFilterChange: (Set<String>) -> Unit = {},
    onCategoryFilterChange: (Set<String>) -> Unit = {},
    onTagFilterChange: (Set<String>) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenCreate: () -> Unit = {},
    onOpenEdit: (transaction: Transaction, transferToAccountId: String?) -> Unit = { _, _ -> },
    onCloseForm: () -> Unit = {},
    onSubmit: (TransactionSubmission) -> Unit = {},
    onDelete: (Transaction) -> Unit = {},
) {
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }

    val uncategorizedLabel = stringResource(R.string.category_uncategorized)
    val accountOptions = remember(state.accounts) { state.accounts.map { FilterOption(it.id, it.name) } }
    val categoryOptions = remember(state.categories, uncategorizedLabel) {
        listOf(FilterOption(UNCATEGORIZED_FILTER_ID, uncategorizedLabel)) +
            state.categories.filter { it.parentId == null }.map { FilterOption(it.id, it.name) }
    }
    val tagOptions = remember(state.tags) { state.tags.map { FilterOption(it.id, it.name) } }
    var openFilter by remember { mutableStateOf<FilterKind?>(null) }

    // The field owns the text; what is typed is handed to the view model, which does the filtering.
    val searchFieldState = rememberTextFieldState(state.searchText)
    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collectLatest { onSearchTextChange(it) }
    }

    // Scrolling down gives the list the bar's height back; the first scroll up returns it.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        modifier = modifier.appBarScroll(scrollBehavior),
        topBar = { MonthTopBar(month = state.month, onMonthChange = onMonthChange, scrollBehavior = scrollBehavior) },
        
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_transaction),
                icon = Icons.Filled.Add,
                onClick = onOpenCreate,
            )
        },
    ) { padding ->
        val error = (state.status as? ScreenStatus.Error)?.message
        val grouped = remember(state.rows) { groupByDate(state.rows) }

        Column(
            modifier = Modifier.padding(padding).fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchField(
                state = searchFieldState,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The button is only as wide as its label; the box takes the rest of the row, which pushes the icons to the end.
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    AccountFilterButton(
                        options = accountOptions,
                        selected = state.accountFilter,
                        allLabel = stringResource(R.string.all_accounts),
                        onClick = { openFilter = FilterKind.ACCOUNTS },
                    )
                }
                FilterIconButton(
                    icon = Icons.Outlined.Sell,
                    description = stringResource(R.string.filter_by_tag),
                    count = state.tagFilter.size,
                    onClick = { openFilter = FilterKind.TAGS },
                )
                FilterIconButton(
                    icon = Icons.Outlined.FilterList,
                    description = stringResource(R.string.filter_by_category),
                    count = state.categoryFilter.size,
                    onClick = { openFilter = FilterKind.CATEGORIES },
                )
            }

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
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
                            // A day's heading, drawn like the account groups': the date in primary, its
                            // net at the end, inset past the rows it names.
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    formatLongDate(date),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                MoneyText(dailyAccrued(rows), tone = MoneyTone.SIGNED, currency = state.currency, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        itemsIndexed(rows, key = { _, row ->
                            when (row) {
                                is TransactionRow.Transfer -> row.id
                                is TransactionRow.Single -> row.transaction.id
                            }
                        }) { index, row ->
                            TransactionRowItem(
                                row = row,
                                position = positionInGroup(index, rows.lastIndex),
                                currency = state.currency,
                                onClick = {
                                    when (row) {
                                        is TransactionRow.Transfer -> onOpenEdit(row.leg, row.toAccountId)
                                        is TransactionRow.Single -> onOpenEdit(row.transaction, null)
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
                                TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
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
        TransactionForm(
            editing = formState.editing,
            transferToAccountId = formState.transferToAccountId,
            accounts = state.accounts.filter { !it.archived },
            categories = state.categories,
            tags = state.tags,
            payees = payees,
            defaultAccountId = state.defaultAccountId,
            submitting = formState.submitting,
            error = formState.error,
            onSubmit = onSubmit,
            onCancel = onCloseForm,
        )
    }

    val dismissFilter = { openFilter = null }
    when (openFilter) {
        FilterKind.ACCOUNTS -> FilterSheet(
            title = stringResource(R.string.filter_by_account),
            options = accountOptions,
            selected = state.accountFilter,
            emptyText = stringResource(R.string.no_accounts_yet),
            onChange = onAccountFilterChange,
            onDismiss = dismissFilter,
        )

        FilterKind.CATEGORIES -> FilterSheet(
            title = stringResource(R.string.filter_by_category),
            options = categoryOptions,
            selected = state.categoryFilter,
            emptyText = stringResource(R.string.no_categories_yet),
            onChange = onCategoryFilterChange,
            onDismiss = dismissFilter,
        )

        FilterKind.TAGS -> FilterSheet(
            title = stringResource(R.string.filter_by_tag),
            options = tagOptions,
            selected = state.tagFilter,
            emptyText = stringResource(R.string.no_tags_yet),
            onChange = onTagFilterChange,
            onDismiss = dismissFilter,
        )

        null -> Unit
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
                TextButton(onClick = { onDelete(toDelete) }, enabled = !deleting) {
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
private fun TransactionRowItem(
    row: TransactionRow,
    position: ItemPosition,
    currency: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val visibility = koinInject<AmountVisibility>()
    val notes = when (row) {
        is TransactionRow.Transfer -> row.notes
        is TransactionRow.Single -> row.transaction.notes
    }.trim()
    val tags = when (row) {
        is TransactionRow.Transfer -> row.tags
        is TransactionRow.Single -> row.transaction.tags
    }

    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
        actions = { ActionIconButton(ActionIcon.DELETE, stringResource(R.string.action_delete), onDelete, danger = true) },
    ) {
        // One of the day's rows rather than a card of its own: round where the day starts and ends,
        // near-square where it meets the row beside it, as the account groups are drawn.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            shape = groupedItemShape(position),
            color = groupedItemColor,
        ) {
          Column {
            // The headline, what is under it and the figures at the end sit in a plain row of two columns;
            // the card, the tap target and the notes row below stay outside it.
            when (row) {
                is TransactionRow.Transfer -> {
                    val accountFlow = stringResource(R.string.transfer_account_flow, row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
                    val title = if (row.payee.isBlank() || row.payee == accountFlow) stringResource(R.string.category_kind_transfer) else row.payee
                    TransactionSummary(
                        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
                        supporting = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccountFlow(row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
                                formatTime(row.leg.occurredOn)?.let { time ->
                                    Text(time, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MoneyText(row.amount, tone = MoneyTone.TRANSFER, currency = currency, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    visibility.displayMoney(row.leg.runningBalance, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    // An overdrawn account reads in the error colour, as its balance does on the Accounts screen.
                                    color = if (row.leg.runningBalance < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                                )
                                row.categoryName?.let { name -> CategoryLabel(name, row.categoryColor) }
                            }
                        },
                    )
                }

                is TransactionRow.Single -> {
                    val transaction = row.transaction
                    TransactionSummary(
                        title = {
                            Text(
                                transaction.payee.ifBlank { transaction.categoryName ?: stringResource(R.string.category_uncategorized) },
                                style = MaterialTheme.typography.titleMedium,
                            )
                        },
                        supporting = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    transaction.accountName.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (transaction.automated) {
                                    Text(
                                        stringResource(R.string.automated_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                formatTime(transaction.occurredOn)?.let { time ->
                                    Text(time, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        },
                        trailing = {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MoneyText(transaction.amount, tone = MoneyTone.SIGNED_ALERT, currency = currency, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    visibility.displayMoney(transaction.runningBalance, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    // An overdrawn account reads in the error colour, as its balance does on the Accounts screen.
                                    color = if (transaction.runningBalance < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                                )
                                transaction.categoryName?.let { name -> CategoryLabel(name, transaction.categoryColor) }
                            }
                        },
                    )
                }
            }
          }
        }
    }
}

/** Filters the list as it is typed. A plain text field drawn as a search bar — nothing here expands or takes the screen over. */
@Composable
private fun SearchField(state: TextFieldState, modifier: Modifier = Modifier) {
    TextField(
        state = state,
        modifier = modifier,
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = CircleShape,
        colors = pillTextFieldColors(),
        textStyle = MaterialTheme.typography.bodyLarge,
        placeholder = { Text(stringResource(R.string.search_payee_notes)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (state.text.isNotEmpty()) {
                IconButton(onClick = { state.clearText() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear))
                }
            }
        },
    )
}

/** A transaction's headline and what is under it at the start, its figures at the end, top-aligned with each other. */
@Composable
private fun TransactionSummary(
    title: @Composable () -> Unit,
    supporting: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            title()
            supporting()
        }
        trailing()
    }
}

@Composable
private fun CategoryLabel(name: String, colorHex: String?) {
    val color = harmonisedColorOrNull(colorHex)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall)
        if (color != null) Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    }
}

@Composable
private fun AccountFilterButton(
    options: List<FilterOption>,
    selected: Set<String>,
    allLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // In the order they were picked; anything no longer in the list is not counted.
    val labels = selected.mapNotNull { id -> options.firstOrNull { it.id == id }?.label }
    val text = when (labels.size) {
        0 -> allLabel
        1 -> labels.first()
        else -> stringResource(R.string.filter_and_more, labels.first())
    }
    TextButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
    }
}

@Composable
private fun FilterIconButton(icon: ImageVector, description: String, count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        // A count of active filters is not an alert, so it leaves the badge's default error colour.
        BadgedBox(badge = { if (count > 0) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text(count.toString()) } }) {
            Icon(
                icon,
                contentDescription = description,
            )
        }
    }
}

/** Every option as a chip to switch on or off; a change applies at once, so the list behind updates as chips are picked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    title: String,
    options: List<FilterOption>,
    selected: Set<String>,
    emptyText: String,
    onChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (selected.isNotEmpty()) {
                    TextButton(onClick = { onChange(emptySet()) }) { Text(stringResource(R.string.action_clear)) }
                }
            }

            if (options.isEmpty()) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        val active = option.id in selected
                        FilterChip(
                            selected = active,
                            onClick = { onChange(if (active) selected - option.id else selected + option.id) },
                            label = { Text(option.label) },
                            leadingIcon = if (active) {
                                { Icon(Icons.Filled.Done, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

private enum class FilterKind { ACCOUNTS, CATEGORIES, TAGS }

private data class FilterOption(val id: String, val label: String)

@Preview(showBackground = true)
@Composable
private fun TransactionsScreenPreview() {
    ScreenPreview {
        TransactionsScreenContent(
            TransactionsUiState(
                month = PreviewData.MONTH,
                accounts = PreviewData.accounts,
                categories = PreviewData.categories,
                tags = PreviewData.tags,
                rows = PreviewData.rows,
                total = PreviewData.rows.size,
                loadedCount = PreviewData.rows.size,
            ),
        )
    }
}
