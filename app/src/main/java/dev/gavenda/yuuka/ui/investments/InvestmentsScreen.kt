package dev.gavenda.yuuka.ui.investments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.Holding
import dev.gavenda.yuuka.domain.MASK
import dev.gavenda.yuuka.domain.TradeKinds
import dev.gavenda.yuuka.domain.formatPrice
import dev.gavenda.yuuka.domain.percentChange
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Every PSE holding across the broker accounts: what it is worth at the last close, how far that is from what it
 * cost, what it is made of, and the trades behind it.
 */
@Composable
fun InvestmentsScreen(onOpenHolding: (String) -> Unit, modifier: Modifier = Modifier, viewModel: InvestmentsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var trading by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Trade?>(null) }
    val switchForm = rememberFormSwitch()
    val held = state.held
    val visibility = koinInject<AmountVisibility>()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = { ScreenTopBar(title = stringResource(R.string.destination_investments)) },
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.investments_new_trade),
                icon = R.drawable.add_48px,
                onClick = { switchForm { trading = true } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            if (state.loaded && state.trades.isEmpty()) {
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

            val marketValue = held.sumOf { it.marketValue }
            val cost = held.sumOf { it.costBasis }
            val unrealised = marketValue - cost
            val dayChange = held.sumOf { it.dayChange }

            item(key = "value") {
                StatCard(
                    label = stringResource(R.string.investments_market_value),
                    amount = marketValue,
                    hero = true,
                    watermark = true,
                    caption = state.pricesAsOf?.let { asOf ->
                        stringResource(
                            R.string.investments_as_of,
                            visibility.displayMoney(cost),
                            LocalDate.parse(asOf).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                        )
                    },
                )
            }
            item(key = "gains") {
                Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GainCard(stringResource(R.string.investments_unrealised), unrealised, percentChange(unrealised, cost), Modifier.weight(1f))
                        GainCard(stringResource(R.string.investments_today), dayChange, percentChange(dayChange, marketValue - dayChange), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GainCard(stringResource(R.string.investments_realised), state.realisedThisYear, null, Modifier.weight(1f))
                        GainCard(stringResource(R.string.investments_dividends), state.dividendsThisYear, null, Modifier.weight(1f))
                    }
                }
            }
            if (held.isNotEmpty()) item(key = "allocation") { AllocationCard(held, Modifier.padding(top = 8.dp)) }
            item(key = "tabs") {
                ConnectedButtonGroup(
                    options = listOf(0, 1),
                    selected = tab,
                    onSelect = { tab = it },
                    label = { stringResource(if (it == 0) R.string.investments_tab_holdings else R.string.investments_tab_activity) },
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
            }
            if (tab == 0) holdingsList(held, state.brokerCash, onOpenHolding) else activityList(state.trades) { pendingDelete = it }
        }
    }

    if (trading) {
        TradeForm(state = state, onSave = viewModel::createTrade, onDismiss = { trading = false })
    }
    pendingDelete?.let { DeleteTradeDialog(it, viewModel, onDone = { pendingDelete = null }) }
}

/** The holdings under the broker account that holds them, each account with what it comes to and the cash beside them. */
private fun LazyListScope.holdingsList(holdings: List<Holding>, cash: Map<String, Long>, onOpen: (String) -> Unit) {
    holdings.groupBy { it.position.accountId }.forEach { (accountId, rows) ->
        val first = rows.first()
        item(key = "account:$accountId") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccountLogo(first.accountName, first.bank, null, false, size = 24)
                Text(first.accountName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                MoneyText(rows.sumOf { it.marketValue }, style = MaterialTheme.typography.titleMedium)
            }
        }
        itemsIndexed(rows, key = { _, holding -> "holding:$accountId:${holding.symbol}" }) { index, holding ->
            HoldingRow(holding, positionInGroup(index == 0, false), onClick = { onOpen(holding.symbol) })
        }
        item(key = "cash:$accountId") {
            GroupedItem(position = positionInGroup(false, true), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.investments_cash), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    MoneyText(cash[accountId] ?: 0, tone = MoneyTone.SIGNED_ALERT, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** The trades, newest first, under the month they were made in. */
private fun LazyListScope.activityList(trades: List<Trade>, onDelete: (Trade) -> Unit) {
    val monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy")
    trades.groupBy { it.occurredOn.take(7) }.forEach { (month, rows) ->
        item(key = "month:$month") {
            Text(
                LocalDate.parse("$month-01").format(monthFormat),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(rows, key = { _, trade -> "trade:${trade.id}" }) { index, trade ->
            TradeRow(trade, positionInGroup(index, rows.lastIndex), onDelete = { onDelete(trade) })
        }
    }
}

@Composable
private fun GainCard(label: String, amount: Long, percent: String?, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
            MoneyText(amount, tone = MoneyTone.SIGNED_ALERT, explicit = amount != 0L, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
            if (percent != null) Text(percent, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** The colours allocation slices take in turn: the brand's three, then their containers. */
@Composable
private fun sliceColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.secondaryContainer, scheme.tertiaryContainer)
}

/** One bar split by what each stock is worth, largest first, and a legend of the shares it is. */
@Composable
private fun AllocationCard(holdings: List<Holding>, modifier: Modifier = Modifier) {
    val bySymbol = holdings.groupBy { it.symbol }.mapValues { (_, rows) -> rows.sumOf { it.marketValue } }.entries.sortedByDescending { it.value }
    val total = bySymbol.sumOf { it.value }.coerceAtLeast(1)
    val colors = sliceColors()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.investments_allocation), style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth().height(16.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                bySymbol.forEachIndexed { index, (_, value) ->
                    if (value > 0) Box(Modifier.weight(value.toFloat() / total).fillMaxHeight().background(colors[index % colors.size]))
                }
            }
            bySymbol.chunked(2).forEachIndexed { row, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    pair.forEachIndexed { column, (symbol, value) ->
                        val index = row * 2 + column
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(10.dp).background(colors[index % colors.size], CircleShape))
                            Text(symbol, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text("%.1f%%".format(value * 100.0 / total), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A share count, masked with the amounts: how many shares someone holds gives the magnitude away as well. */
@Composable
internal fun sharesText(shares: Long): String {
    val hidden by koinInject<AmountVisibility>().hidden.collectAsStateWithLifecycle()
    return if (hidden) MASK else "%,d".format(shares)
}

@Composable
private fun HoldingRow(holding: Holding, position: ItemPosition, onClick: () -> Unit) {
    GroupedItem(position = position, modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SymbolBadge(holding.symbol)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(holding.symbol, style = MaterialTheme.typography.titleMedium)
                Text(holding.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(
                    stringResource(R.string.investments_shares_at, sharesText(holding.shares), formatPrice(holding.priceE4)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MoneyText(holding.marketValue, style = MaterialTheme.typography.titleMedium)
                MoneyText(holding.unrealised, tone = MoneyTone.SIGNED_ALERT, explicit = true, style = MaterialTheme.typography.bodySmall)
                Text(percentChange(holding.unrealised, holding.costBasis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The ticker's first letters on a tonal circle, standing in for a logo the PSE does not hand out. */
@Composable
internal fun SymbolBadge(symbol: String, size: Int = 40) {
    Box(
        modifier = Modifier.size(size.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol.take(2),
            style = if (size >= 48) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun tradeKindLabel(kind: String): String = stringResource(
    when (kind) {
        TradeKinds.BUY -> R.string.investments_trade_buy
        TradeKinds.SELL -> R.string.investments_trade_sell
        else -> R.string.investments_trade_dividend
    },
)

/** A trade's own row: what and where, how many at what, and what it did to the account's cash. */
@Composable
internal fun TradeRow(trade: Trade, position: ItemPosition, onDelete: () -> Unit, showSymbol: Boolean = true) {
    val dateFormat = DateTimeFormatter.ofPattern("d MMM")
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, "${trade.symbol} ${tradeKindLabel(trade.kind)}"), onDelete, danger = true)
        },
    ) {
    GroupedItem(position = position, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val kind = tradeKindLabel(trade.kind)
                Text(if (showSymbol) "$kind · ${trade.symbol}" else kind, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (trade.kind == TradeKinds.DIVIDEND) {
                        stringResource(R.string.investments_dividend_line, sharesText(trade.shares), formatPrice(trade.price))
                    } else {
                        stringResource(R.string.investments_shares_at, sharesText(trade.shares), formatPrice(trade.price))
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    stringResource(R.string.investments_trade_line, LocalDate.parse(trade.occurredOn).format(dateFormat), trade.accountName.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // A buy or sell only turns cash into shares and back; a dividend is income.
            MoneyText(
                trade.amount,
                tone = if (trade.kind == TradeKinds.DIVIDEND) MoneyTone.SIGNED else MoneyTone.TRANSFER,
                explicit = true,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    }
}

/**
 * Asks before deleting a trade, which takes its cash with it; a buy a later sell depends on cannot go, and the
 * dialog says so instead of offering to.
 */
@Composable
internal fun DeleteTradeDialog(trade: Trade, viewModel: InvestmentsViewModel, onDone: () -> Unit) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    val deletedMessage = stringResource(R.string.investments_trade_deleted)
    if (!viewModel.canDelete(trade.id)) {
        AlertDialog(
            onDismissRequest = onDone,
            title = { Text(stringResource(R.string.investments_delete_trade_blocked_title)) },
            text = { Text(stringResource(R.string.investments_delete_trade_blocked_body, trade.symbol)) },
            confirmButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.action_no_go_back)) } },
        )
        return
    }
    ConfirmDeleteDialog(
        title = stringResource(R.string.investments_delete_trade_title, tradeKindLabel(trade.kind).lowercase(), trade.symbol),
        body = stringResource(R.string.investments_delete_trade_body, trade.accountName.orEmpty()),
        deleting = busy.isBusy("delete"),
        onConfirm = { busy.run("delete", snackbarHostState, successMessage = deletedMessage, onSuccess = onDone) { viewModel.deleteTrade(trade.id) } },
        onDismiss = onDone,
    )
}
