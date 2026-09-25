package dev.gavenda.yuuka.ui.transactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.EditNote
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.model.TransactionTag
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

    val uncategorizedLabel = stringResource(R.string.category_uncategorized)
    val accountOptions = remember(state.accounts) { state.accounts.map { FilterOption(it.id, it.name) } }
    val categoryOptions = remember(state.categories, uncategorizedLabel) {
        listOf(FilterOption(UNCATEGORIZED_FILTER_ID, uncategorizedLabel)) +
            state.categories.filter { it.parentId == null }.map { FilterOption(it.id, it.name) }
    }
    val tagOptions = remember(state.tags) { state.tags.map { FilterOption(it.id, it.name) } }
    var openFilter by remember { mutableStateOf<FilterKind?>(null) }

    // The search bar owns the text; what is typed is handed to the view model, which does the filtering.
    val searchFieldState = rememberTextFieldState(state.searchText)
    val searchBarState = rememberSearchBarState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collectLatest(viewModel::setSearchText)
    }

    // One input field, shown by the bar in the page and again by the expanded search above it. Tapping
    // the collapsed bar expands the search — that is what takes the focus and raises the keyboard, so
    // the expanded half is not optional.
    val searchInputField: @Composable () -> Unit = {
        SearchBarDefaults.InputField(
            textFieldState = searchFieldState,
            searchBarState = searchBarState,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text(stringResource(R.string.search_payee_notes)) },
            leadingIcon = {
                if (searchBarState.targetValue == SearchBarValue.Expanded) {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                } else {
                    Icon(Icons.Filled.Search, contentDescription = null)
                }
            },
            trailingIcon = {
                if (searchFieldState.text.isNotEmpty()) {
                    IconButton(onClick = { searchFieldState.clearText() }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.cd_clear))
                    }
                }
            },
        )
    }

    // Scrolling down gives the list the bar's height back; the first scroll up returns it.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { MonthTopBar(month = state.month, onMonthChange = viewModel::setMonth, scrollBehavior = scrollBehavior) },
        
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_transaction),
                icon = Icons.Filled.Add,
                onClick = viewModel::openCreate,
            )
        },
    ) { padding ->
        val error = (state.status as? ScreenStatus.Error)?.message
        val grouped = remember(state.rows) { groupByDate(state.rows) }

        Column(
            modifier = Modifier.padding(padding).fillMaxWidth().padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
                item {
                    // Material's own search bar rather than a text field dressed up as one.
                    SearchBar(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        state = searchBarState,
                        colors = SearchBarDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        inputField = searchInputField,
                    )
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
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                MoneyText(dailyAccrued(rows), tone = MoneyTone.SIGNED, currency = state.currency, style = MaterialTheme.typography.labelMedium)
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

    // What the search expands into: the same rows the page shows, already filtered by what is typed,
    // so a match can be opened without leaving the search.
    ExpandedFullScreenSearchBar(state = searchBarState, inputField = searchInputField) {
        val matches = remember(state.rows) { groupByDate(state.rows) }
        LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 16.dp)) {
            if (state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.transactions_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            matches.forEach { (date, rows) ->
                item {
                    Text(
                        formatLongDate(date),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                    )
                }
                itemsIndexed(rows, key = { _, row ->
                    when (row) {
                        is TransactionRow.Transfer -> "search:${row.id}"
                        is TransactionRow.Single -> "search:${row.transaction.id}"
                    }
                }) { index, row ->
                    TransactionRowItem(
                        row = row,
                        position = positionInGroup(index, rows.lastIndex),
                        currency = state.currency,
                        onClick = {
                            scope.launch { searchBarState.animateToCollapsed() }
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
        }
    }

    if (formState.open) {
        val sheetState = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
        ModalBottomSheet(onDismissRequest = viewModel::closeForm, sheetState = sheetState) {
            WithSnackbarOverlay {
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
                    onSubmit = viewModel::submit,
                    onCancel = viewModel::closeForm,
                )
            }
        }
    }

    val dismissFilter = { openFilter = null }
    when (openFilter) {
        FilterKind.ACCOUNTS -> FilterSheet(
            title = stringResource(R.string.filter_by_account),
            options = accountOptions,
            selected = state.accountFilter,
            emptyText = stringResource(R.string.no_accounts_yet),
            onChange = viewModel::setAccountFilter,
            onDismiss = dismissFilter,
        )

        FilterKind.CATEGORIES -> FilterSheet(
            title = stringResource(R.string.filter_by_category),
            options = categoryOptions,
            selected = state.categoryFilter,
            emptyText = stringResource(R.string.no_categories_yet),
            onChange = viewModel::setCategoryFilter,
            onDismiss = dismissFilter,
        )

        FilterKind.TAGS -> FilterSheet(
            title = stringResource(R.string.filter_by_tag),
            options = tagOptions,
            selected = state.tagFilter,
            emptyText = stringResource(R.string.no_tags_yet),
            onChange = viewModel::setTagFilter,
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
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
          Column {
            // The headline, what is under it and the figures at the end are a ListItem's own three slots;
            // the card, the tap target and the notes row below stay outside it, so its container is
            // transparent and it draws nothing of its own.
            when (row) {
                is TransactionRow.Transfer -> {
                    val accountFlow = stringResource(R.string.transfer_account_flow, row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
                    val title = if (row.payee.isBlank() || row.payee == accountFlow) stringResource(R.string.category_kind_transfer) else row.payee
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        content = { Text(title, style = MaterialTheme.typography.bodyMedium) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AccountFlow(row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
                                formatTime(row.leg.occurredOn)?.let { time ->
                                    Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MoneyText(row.amount, tone = MoneyTone.TRANSFER, currency = currency, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    visibility.displayMoney(row.leg.runningBalance, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                row.categoryName?.let { name -> CategoryLabel(name, row.categoryColor) }
                            }
                        },
                    )
                }

                is TransactionRow.Single -> {
                    val transaction = row.transaction
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        content = {
                            Text(
                                transaction.payee.ifBlank { transaction.categoryName ?: stringResource(R.string.category_uncategorized) },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    transaction.accountName.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (transaction.automated) {
                                    Text(
                                        stringResource(R.string.automated_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                formatTime(transaction.occurredOn)?.let { time ->
                                    Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        trailingContent = {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                MoneyText(transaction.amount, tone = MoneyTone.SIGNED, currency = currency, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    visibility.displayMoney(transaction.runningBalance, currency),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                transaction.categoryName?.let { name -> CategoryLabel(name, transaction.categoryColor) }
                            }
                        },
                    )
                }
            }

            // Notes on the left, tags as chips at the right end of the same row, centred on each other.
            // The icon stays with the note's first line.
            if (notes.isNotEmpty() || tags.isNotEmpty()) {
                HorizontalDivider()
                BoxWithConstraints {
                    val maxChipsWidth = maxWidth * 0.6f
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (notes.isNotEmpty()) {
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
                        // Sized to what they hold, but never past 60% of the row, so a long tag list wraps rather than crowding the notes out.
                        if (tags.isNotEmpty()) TagChips(tags, modifier = Modifier.widthIn(max = maxChipsWidth))
                    }
                }
            }
          }
        }
    }
}

/** A transaction's tags as small chips, wrapping onto further lines and packed toward the end of the row. */
@Composable
private fun TagChips(tags: List<TransactionTag>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag -> TagChip(tag.name, tag.color) }
    }
}

/** Read-only on a card: it is a label, and a tap on the card still opens the transaction. */
@Composable
private fun TagChip(name: String, colorHex: String) {
    val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (labels.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        ),
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
        BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
            Icon(
                icon,
                contentDescription = description,
                tint = if (count > 0) MaterialTheme.colorScheme.primary else LocalContentColor.current,
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
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
