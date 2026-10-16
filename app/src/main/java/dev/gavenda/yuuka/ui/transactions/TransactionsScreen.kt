package dev.gavenda.yuuka.ui.transactions

import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.graphics.Color
import dev.gavenda.yuuka.data.model.Payee
import androidx.compose.ui.tooling.preview.Preview

/** How many items short of a list's end its next page is asked for, so it is usually there before the end is. */
private const val LOAD_AHEAD = 10

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
    // Each filter button opens its options as a row of chips under the bar; pressing it again puts the row away.
    var openFilter by remember { mutableStateOf<FilterKind?>(null) }
    val toggleFilter = { kind: FilterKind -> openFilter = if (openFilter == kind) null else kind }
    val openChips = when (openFilter) {
        FilterKind.ACCOUNTS -> FilterChips(FilterKind.ACCOUNTS, accountOptions, state.accountFilter, stringResource(R.string.no_accounts_yet), onAccountFilterChange)
        FilterKind.CATEGORIES -> FilterChips(FilterKind.CATEGORIES, categoryOptions, state.categoryFilter, stringResource(R.string.no_categories_yet), onCategoryFilterChange)
        FilterKind.TAGS -> FilterChips(FilterKind.TAGS, tagOptions, state.tagFilter, stringResource(R.string.no_tags_yet), onTagFilterChange)
        null -> null
    }

    // The field owns the text; what is typed is handed to the view model, which does the filtering.
    val searchFieldState = rememberTextFieldState(state.searchText)
    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collectLatest { onSearchTextChange(it) }
    }

    // Scrolling down gives the list the bar's height back; the first scroll up returns it.
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    val openEdit = { row: TransactionRow ->
        when (row) {
            is TransactionRow.Transfer -> onOpenEdit(row.leg, row.toAccountId)
            is TransactionRow.Single -> onOpenEdit(row.transaction, null)
        }
    }
    val askDelete = { row: TransactionRow ->
        pendingDelete = when (row) {
            is TransactionRow.Transfer -> row.leg
            is TransactionRow.Single -> row.transaction
        }
    }

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.appBarScroll(scrollBehavior),
        topBar = {
            MonthSearchTopBar(
                month = state.month,
                onMonthChange = onMonthChange,
                search = searchFieldState,
                searchHint = stringResource(R.string.search_payee_notes),
                scrollBehavior = scrollBehavior,
            ) {
                // With a snackbar host of its own, since the view is a window over the one the shell's is in.
                WithSnackbarOverlay {
                    TransactionResults(state = state, onOpen = openEdit, onLoadMore = onLoadMore, modifier = Modifier.fillMaxSize())
                }
            }
        },
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_transaction),
                icon = R.drawable.add_48px,
                onClick = onOpenCreate,
            )
        },
    ) { padding ->
        TransactionList(
            state = state,
            // Room for the last row to clear the button that floats over the list.
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            onOpen = openEdit,
            onDelete = askDelete,
            onLoadMore = onLoadMore,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            // The filters lead the list and scroll away with it, so the page is one list rather than a list under a header.
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                // Under the bar and in line with it: the account button's icon stands under the drawer button's, and
                // the tag and category icons under the bar's two actions.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = barAlignedEdgePadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The button is only as wide as its label; the box takes the rest of the row, which pushes the icons to the end.
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        AccountFilterButton(
                            open = openFilter == FilterKind.ACCOUNTS,
                            options = accountOptions,
                            selected = state.accountFilter,
                            allLabel = stringResource(R.string.all_accounts),
                            onClick = { toggleFilter(FilterKind.ACCOUNTS) },
                        )
                    }
                    FilterIconButton(
                        icon = if (openFilter == FilterKind.TAGS) R.drawable.sell_fill_48px else R.drawable.sell_48px,
                        description = stringResource(R.string.filter_by_tag),
                        count = state.tagFilter.size,
                        onClick = { toggleFilter(FilterKind.TAGS) },
                    )
                    FilterIconButton(
                        icon = if (openFilter == FilterKind.CATEGORIES) R.drawable.filter_list_fill_48px else R.drawable.filter_list_48px,
                        description = stringResource(R.string.filter_by_category),
                        count = state.categoryFilter.size,
                        onClick = { toggleFilter(FilterKind.CATEGORIES) },
                    )
                }
                FilterChipRow(openChips)
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
            icon = { Icon(painterResource(R.drawable.delete_48px), contentDescription = null) },
            iconContentColor = MaterialTheme.colorScheme.error,
            title = { Text(if (toDelete.transferId != null) stringResource(R.string.delete_transfer_confirm_title) else stringResource(R.string.delete_transaction_confirm_title)) },
            text = {
                WithSnackbarOverlay {
                    Text(stringResource(if (toDelete.transferId != null) R.string.delete_transfer_body else R.string.delete_transaction_body))
                }
            },
            confirmButton = {
                Button(onClick = { onDelete(toDelete) }, enabled = !deleting, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                    if (deleting) {
                        MutationLoadingIndicator()
                    } else {
                        Text(stringResource(R.string.action_yes_delete))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }, enabled = !deleting) { Text(stringResource(R.string.action_no_go_back)) } },
        )
    }
}

/**
 * The page's list: the transactions on show under their days, with what stands in for them when there are
 * none and what follows the last.
 */
@Composable
private fun TransactionList(
    state: TransactionsUiState,
    contentPadding: PaddingValues,
    onOpen: (TransactionRow) -> Unit,
    onDelete: (TransactionRow) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
) {
    val error = (state.status as? ScreenStatus.Error)?.message
    val grouped = remember(state.rows) { groupByDate(state.rows) }
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state, onLoadMore)

    LazyColumn(modifier = modifier, state = listState, contentPadding = contentPadding) {
        item(key = "header", contentType = "header") { header() }

        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }

        if (state.rows.isEmpty() && state.status != ScreenStatus.Loading) {
            item {
                EmptyState(
                    stringResource(R.string.transactions_empty_title),
                    YuukaMood.Uneasy,
                    modifier = Modifier.fillParentMaxHeight().padding(16.dp),
                    description = stringResource(R.string.transactions_empty_description),
                )
            }
        } else {
            grouped.forEach { (date, rows) ->
                item(key = "day:$date", contentType = "day") {
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
                itemsIndexed(rows, key = { _, row -> row.key }) { index, row ->
                    TransactionRowItem(
                        row = row,
                        position = positionInGroup(index, rows.lastIndex),
                        currency = state.currency,
                        onClick = { onOpen(row) },
                        onDelete = { onDelete(row) },
                    )
                }
            }

            if (state.hasMore) {
                item { NextPageFooter(state.moreError, onRetry = onLoadMore) }
            } else {
                // The end of the list, and only once there is no more of it to load: Yuuka, asleep
                // over closed books, saying so, so the last row is not mistaken for a page that
                // stopped short.
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        YuukaPortrait(YuukaMood.Asleep, width = 96.dp)
                        // As tall as the badge she is in, so the tail is level with her face.
                        Box(
                            modifier = Modifier.weight(1f, fill = false).heightIn(min = 96.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            SpeechBubble(stringResource(R.string.transactions_end))
                        }
                    }
                }
            }
        }
    }
}

/**
 * What the search view lists under its field: the transactions on show as Material's plain list items — the
 * day over what it was, where it was and what is noted on it beneath, the amount at the end — with none of the
 * page's days, grouped shapes or swipes. Pressing one opens it. The view is a dialog, so Yuuka is not drawn in
 * it: nothing matching is said in plain text, and the last item is simply the last.
 */
@Composable
private fun TransactionResults(
    state: TransactionsUiState,
    onOpen: (TransactionRow) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val error = (state.status as? ScreenStatus.Error)?.message
    val listState = rememberLazyListState()
    LoadMoreAtEnd(listState, state, onLoadMore)

    LazyColumn(modifier = modifier, state = listState) {
        if (error != null) {
            item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        }

        if (state.rows.isEmpty() && state.status != ScreenStatus.Loading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(R.string.transactions_empty_title), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                    Text(stringResource(R.string.transactions_empty_description), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                }
            }
        } else {
            items(state.rows, key = { it.key }) { row ->
                TransactionResultItem(row = row, currency = state.currency, onClick = { onOpen(row) })
            }

            if (state.hasMore) {
                item { NextPageFooter(state.moreError, onRetry = onLoadMore) }
            }
        }
    }
}

/** One transaction as Material's own list item, on the search view's colour rather than a container of its own. */
@Composable
private fun TransactionResultItem(row: TransactionRow, currency: String, onClick: () -> Unit) {
    val transaction = when (row) {
        is TransactionRow.Transfer -> row.leg
        is TransactionRow.Single -> row.transaction
    }
    val where = when (row) {
        is TransactionRow.Transfer -> stringResource(R.string.transfer_account_flow, row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
        is TransactionRow.Single -> transaction.accountName.orEmpty()
    }
    val notes = when (row) {
        is TransactionRow.Transfer -> row.notes
        is TransactionRow.Single -> transaction.notes
    }.trim()
    val tags = when (row) {
        is TransactionRow.Transfer -> row.tags
        is TransactionRow.Single -> transaction.tags
    }
    // A search also matches notes and tag names, so both are on the item for a match to be seen in.
    val supporting = listOf(where, notes, tags.joinToString { it.name }).filter { it.isNotEmpty() }.joinToString(" · ")

    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        overlineContent = { Text(formatLongDate(transaction.occurredOn)) },
        supportingContent = if (supporting.isNotEmpty()) ({ Text(supporting) }) else null,
        trailingContent = {
            when (row) {
                is TransactionRow.Transfer -> MoneyText(row.amount, tone = MoneyTone.TRANSFER, currency = currency, style = MaterialTheme.typography.bodyLarge)
                is TransactionRow.Single -> MoneyText(transaction.amount, tone = MoneyTone.SIGNED_ALERT, currency = currency, style = MaterialTheme.typography.bodyLarge)
            }
        },
        content = { Text(rowTitle(row)) },
    )
}

/** What names a row in either list: its payee, or failing that what it was filed under. */
@Composable
private fun rowTitle(row: TransactionRow): String = when (row) {
    is TransactionRow.Transfer -> {
        val accountFlow = stringResource(R.string.transfer_account_flow, row.fromAccountName.orEmpty(), row.toAccountName.orEmpty())
        if (row.payee.isBlank() || row.payee == accountFlow) stringResource(R.string.category_kind_transfer) else row.payee
    }

    is TransactionRow.Single -> row.transaction.payee.ifBlank { row.transaction.categoryName ?: stringResource(R.string.category_uncategorized) }
}

private val TransactionRow.key: String
    get() = when (this) {
        is TransactionRow.Transfer -> id
        is TransactionRow.Single -> transaction.id
    }

/**
 * Makes either list an endless one: the next page is asked for as the end comes within [LOAD_AHEAD] items of
 * showing, so there is nothing to press for it. It is asked for again when a page lands with the end still in
 * reach, and once a load that was in the way has finished. A page that could not be had is not asked for again
 * from here, which would only repeat the failure for as long as the end stayed in reach — the foot of the list
 * says so and offers to ([NextPageFooter]).
 */
@Composable
private fun LoadMoreAtEnd(listState: LazyListState, state: TransactionsUiState, onLoadMore: () -> Unit) {
    val endInReach by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastShown = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastShown >= layout.totalItemsCount - 1 - LOAD_AHEAD
        }
    }

    LaunchedEffect(endInReach, state.hasMore, state.loadedCount, state.status, state.moreError) {
        if (endInReach && state.hasMore && state.moreError == null) onLoadMore()
    }
}

/**
 * The foot of either list for as long as there is more of it to load. Reaching it has already asked for the next
 * page, so it only says one is on its way — or, when it could not be had, why, with a way to ask again.
 */
@Composable
private fun NextPageFooter(error: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
        } else {
            Text(
                stringResource(R.string.loading_ellipsis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

/** Net change to your accounts' balances for the day — transfers move money between your own accounts, so they don't count, mirroring why `AccountGroup.total` sums balances rather than raw amounts. */
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = SegmentedGap / 2),
        actions = { ActionIconButton(ActionIcon.DELETE, stringResource(R.string.action_delete), onDelete, danger = true) },
    ) {
        // One of the day's rows rather than a card of its own: round where the day starts and ends,
        // near-square where it meets the row beside it, as the account groups are drawn.
        GroupedItem(position = position, modifier = Modifier.fillMaxWidth(), onClick = onClick) {
          Column {
            // The headline, what is under it and the figures at the end sit in a plain row of two columns;
            // the card, the tap target and the notes row below stay outside it.
            when (row) {
                is TransactionRow.Transfer -> {
                    TransactionSummary(
                        title = { Text(rowTitle(row), style = MaterialTheme.typography.titleMedium) },
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
                        title = { Text(rowTitle(row), style = MaterialTheme.typography.titleMedium) },
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

            // Notes on the left, tags as chips at the right end of the same row, centred on each other.
            // The icon stays with the note's first line.
            if (notes.isNotEmpty() || tags.isNotEmpty()) {
                BoxWithConstraints {
                    val maxChipsWidth = maxWidth * 0.6f
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (notes.isNotEmpty()) {
                                Icon(painterResource(R.drawable.edit_note_48px),
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
    Surface(
        shape = MaterialTheme.shapes.small,
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 24.dp).padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(R.drawable.label_fill_48px), contentDescription = null, modifier = Modifier.size(14.dp), tint = harmonisedColor(colorHex, MaterialTheme.colorScheme.onSurfaceVariant))
            Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
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
        if (color != null) Icon(painterResource(R.drawable.label_fill_48px), contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
    }
}

@Composable
private fun AccountFilterButton(
    open: Boolean,
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
            Icon(
                painterResource(if (open) R.drawable.account_balance_wallet_fill_48px else R.drawable.account_balance_wallet_48px),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(painterResource(R.drawable.arrow_drop_down_48px), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        }
    }
}

@Composable
private fun FilterIconButton(@DrawableRes icon: Int, description: String, count: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        // A count of active filters is not an alert, so it leaves the badge's default error colour.
        BadgedBox(badge = { if (count > 0) Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text(count.toString()) } }) {
            Icon(
                painterResource(icon),
                contentDescription = description,
            )
        }
    }
}

/**
 * The open filter's options as one row of chips that slides down under the filter bar and scrolls sideways;
 * a change applies at once, so the list below updates as chips are picked. Clear stays at the end of the row
 * rather than in it, so it appearing never moves a chip from under a finger.
 */
@Composable
private fun FilterChipRow(chips: FilterChips?) {
    // What was last open stays drawn while the row slides shut.
    var last by remember { mutableStateOf(chips) }
    if (chips != null) SideEffect { last = chips }

    AnimatedVisibility(
        visible = chips != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val shown = chips ?: last ?: return@AnimatedVisibility
        Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (shown.options.isEmpty()) {
                Text(shown.emptyText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp))
            } else {
                // Keyed by the filter, so switching from one to another starts its row from the beginning.
                key(shown.kind) {
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(shown.options, key = { it.id }) { option ->
                            val active = option.id in shown.selected
                            FilterChip(
                                selected = active,
                                onClick = { shown.onChange(if (active) shown.selected - option.id else shown.selected + option.id) },
                                label = { Text(option.label) },
                                leadingIcon = if (active) {
                                    { Icon(painterResource(R.drawable.done_48px), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
                if (shown.selected.isNotEmpty()) {
                    TextButton(onClick = { shown.onChange(emptySet()) }, modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }
        }
    }
}

private enum class FilterKind { ACCOUNTS, CATEGORIES, TAGS }

private data class FilterOption(val id: String, val label: String)

/** One filter as [FilterChipRow] draws it: what can be chosen, what is, and where a change goes. */
private class FilterChips(
    val kind: FilterKind,
    val options: List<FilterOption>,
    val selected: Set<String>,
    val emptyText: String,
    val onChange: (Set<String>) -> Unit,
)

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
