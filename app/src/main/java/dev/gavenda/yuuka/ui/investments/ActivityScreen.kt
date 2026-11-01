package dev.gavenda.yuuka.ui.investments

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.domain.formatLongDate
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel

/** How many trades the activity shows at a time, as many as a page of Transactions. */
private const val TRADE_PAGE = 50

/** How many items short of the end the next page is shown, so it is usually there before the end is. */
private const val LOAD_AHEAD = 10

/**
 * Every trade, newest first, under the day it was made: opened from Investments' "See all", beside it where the
 * page has room. Every trade is already on the device — the holdings are worked out from all of them — so the
 * list is not asked of the server a page at a time; it shows [TRADE_PAGE] and the next as the end comes within
 * reach, as Transactions does, and ends with Yuuka once there are no more. Mirrors `ActivityView.vue`.
 */
@Composable
fun ActivityScreen(modifier: Modifier = Modifier, viewModel: InvestmentsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var shown by rememberSaveable { mutableIntStateOf(TRADE_PAGE) }
    var trading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Trade?>(null) }
    var pendingDelete by remember { mutableStateOf<Trade?>(null) }
    val switchForm = rememberFormSwitch()
    // A form standing beside the list closes with the trade it was editing, wherever that was deleted.
    LaunchedEffect(state.trades) { if (editing?.let { open -> state.trades.none { it.id == open.id } } == true) trading = false }

    val hasMore = state.trades.size > shown
    // A day cut off at the end of a page still says what the whole of it moved.
    val dayTotals = remember(state.trades) { state.trades.groupBy { it.occurredOn.take(10) }.mapValues { (_, rows) -> rows.sumOf { it.amount } } }
    val listState = rememberLazyListState()
    val endInReach by remember(listState) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastShown = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastShown >= layout.totalItemsCount - 1 - LOAD_AHEAD
        }
    }
    // Again whenever a page lands with the end still in reach.
    LaunchedEffect(endInReach, hasMore, shown) { if (endInReach && hasMore) shown += TRADE_PAGE }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = { DetailTopBar(title = stringResource(R.string.investments_activity)) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            if (!state.loaded) return@LazyColumn
            if (state.trades.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        stringResource(R.string.investments_empty_title),
                        YuukaMood.Attentive,
                        modifier = Modifier.fillParentMaxHeight(),
                        description = stringResource(R.string.investments_empty_description),
                    )
                }
                return@LazyColumn
            }
            activityList(
                state.trades.take(shown),
                dayTotals,
                selectedId = editing?.id?.takeIf { trading },
                onEdit = { trade -> switchForm { editing = trade; trading = true } },
                onDelete = { pendingDelete = it },
            )
            // Nothing can fail here, since nothing is fetched: the next page is only waiting to be drawn.
            if (hasMore) item(key = "more") { NextPageFooter(error = null, onRetry = {}) } else item(key = "end") { ListEnd() }
        }
    }

    if (trading) key(editing?.id) {
        val target = editing
        TradeForm(
            state = state,
            onSave = { if (target != null) viewModel.updateTrade(target.id, it) else viewModel.createTrade(it) },
            onDismiss = { trading = false },
            editing = target,
        )
    }
    pendingDelete?.let { DeleteTradeDialog(it, viewModel, onDone = { pendingDelete = null }) }
}

/**
 * The trades, newest first, under the day they were made, as Transactions lists its rows: the date as the day's
 * heading with the cash the day's trades moved at its end, inset past the rows it names.
 */
private fun LazyListScope.activityList(trades: List<Trade>, dayTotals: Map<String, Long>, selectedId: String?, onEdit: (Trade) -> Unit, onDelete: (Trade) -> Unit) {
    trades.groupBy { it.occurredOn.take(10) }.forEach { (day, rows) ->
        item(key = "day:$day") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatLongDate(day), style = MaterialTheme.typography.titleMedium)
                MoneyText(dayTotals[day] ?: rows.sumOf { it.amount }, tone = MoneyTone.SIGNED, style = MaterialTheme.typography.titleMedium)
            }
        }
        itemsIndexed(rows, key = { _, trade -> "trade:${trade.id}" }) { index, trade ->
            TradeRow(
                trade,
                positionInGroup(index, rows.lastIndex),
                selected = trade.id == selectedId,
                onClick = { onEdit(trade) },
                onDelete = { onDelete(trade) },
            )
        }
    }
}
