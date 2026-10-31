package dev.gavenda.yuuka.ui.investments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.PricePoint
import dev.gavenda.yuuka.data.model.Trade
import dev.gavenda.yuuka.domain.Holding
import dev.gavenda.yuuka.domain.Position
import dev.gavenda.yuuka.domain.formatPrice
import dev.gavenda.yuuka.domain.percentChange
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel
import java.time.LocalDate

/** How far back the price chart looks. */
private enum class PriceRange(val months: Long, val label: Int) {
    MONTH(1, R.string.investments_price_range_1m),
    QUARTER(3, R.string.investments_price_range_3m),
    YEAR(12, R.string.investments_price_range_1y),
}

/**
 * One stock, across every broker account that holds it: its last close and the day's move, its closes over a
 * month, a quarter or a year, the position in it and the trades that built it. Opened from Investments, beside
 * it where the page has room.
 */
@Composable
fun HoldingScreen(symbol: String, modifier: Modifier = Modifier, viewModel: InvestmentsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val prices by remember(symbol) { viewModel.prices(symbol) }.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(symbol) { viewModel.refreshPrices(symbol) }

    var range by rememberSaveable { mutableStateOf(PriceRange.QUARTER) }
    var trading by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Trade?>(null) }
    val switchForm = rememberFormSwitch()

    val holding = remember(state, symbol) { combined(symbol, state.holdings) }
    val trades = state.trades.filter { it.symbol == symbol }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = { DetailTopBar(title = symbol) },
    ) { padding ->
        if (holding == null) return@Scaffold
        val dayMove = holding.priceE4 - holding.previousCloseE4
        // The latest close joins the history when the history has not caught up with it yet.
        val closes = remember(prices, holding.priceE4, holding.asOf) {
            val asOf = holding.asOf
            if (asOf != null && prices.none { it.day >= asOf }) prices + PricePoint(asOf, holding.priceE4) else prices
        }
        val from = LocalDate.now().minusMonths(range.months).toString()

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SymbolBadge(holding.symbol, size = 56)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(holding.name, style = MaterialTheme.typography.titleLarge)
                        Text(formatPrice(holding.priceE4), style = MaterialTheme.typography.headlineMedium)
                        if (holding.changeBp != null) {
                            Text(
                                stringResource(
                                    R.string.investments_today_change,
                                    (if (dayMove < 0) "−" else "+") + formatPrice(kotlin.math.abs(dayMove)),
                                    "%+.2f%%".format(holding.changeBp / 100.0),
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    dayMove > 0 -> MaterialTheme.colorScheme.primary
                                    dayMove < 0 -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
            item(key = "chart") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        val shown = closes.filter { it.day >= from }
                        if (shown.size >= 2) {
                            PriceChart(symbol, shown.map { it.close })
                        } else {
                            Text(
                                stringResource(R.string.investments_prices_gathering),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp),
                            )
                        }
                        ConnectedButtonGroup(
                            options = PriceRange.entries,
                            selected = range,
                            onSelect = { range = it },
                            label = { stringResource(it.label) },
                        )
                    }
                }
            }
            item(key = "actions") {
                FilledTonalButton(onClick = { switchForm { trading = true } }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Icon(painterResource(R.drawable.add_48px), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.investments_new_trade))
                }
            }
            item(key = "position") { PositionCard(holding) }
            item(key = "trades-header") {
                Text(
                    stringResource(R.string.investments_trades),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            itemsIndexed(trades, key = { _, trade -> trade.id }) { index, trade ->
                TradeRow(trade, positionInGroup(index, trades.lastIndex), onDelete = { pendingDelete = trade }, showSymbol = false)
            }
        }
    }

    if (trading) {
        val account = holding?.let { state.held.firstOrNull { it.symbol == symbol }?.position?.accountId }
        TradeForm(state = state, onSave = viewModel::createTrade, onDismiss = { trading = false }, initialSymbol = symbol, initialAccount = account)
    }
    pendingDelete?.let { DeleteTradeDialog(it, viewModel, onDone = { pendingDelete = null }) }
}

/** The stock's positions in every broker account, added together. */
private fun combined(symbol: String, holdings: List<Holding>): Holding? {
    val rows = holdings.filter { it.symbol == symbol }
    val first = rows.firstOrNull() ?: return null
    return first.copy(
        position = Position(
            accountId = "",
            symbol = symbol,
            shares = rows.sumOf { it.shares },
            costBasis = rows.sumOf { it.costBasis },
            realised = rows.sumOf { it.position.realised },
            dividends = rows.sumOf { it.position.dividends },
        ),
        accountName = rows.joinToString { it.accountName },
    )
}

/** The position as figures, one a row: what is held, what it cost and what it has made. */
@Composable
private fun PositionCard(holding: Holding) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.investments_position), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 4.dp))
            FigureRow(stringResource(R.string.investments_shares)) { Text(sharesText(holding.shares), style = MaterialTheme.typography.bodyLarge) }
            if (holding.shares > 0) {
                FigureRow(stringResource(R.string.investments_average_cost)) { Text(formatPrice(holding.avgCostE4), style = MaterialTheme.typography.bodyLarge) }
            }
            FigureRow(stringResource(R.string.investments_cost_basis)) { MoneyText(holding.costBasis, style = MaterialTheme.typography.bodyLarge) }
            FigureRow(stringResource(R.string.investments_market_value)) { MoneyText(holding.marketValue, style = MaterialTheme.typography.bodyLarge) }
            HorizontalDivider()
            FigureRow(stringResource(R.string.investments_unrealised_gain), percentChange(holding.unrealised, holding.costBasis)) {
                MoneyText(holding.unrealised, tone = MoneyTone.SIGNED_ALERT, explicit = holding.unrealised != 0L, style = MaterialTheme.typography.bodyLarge)
            }
            FigureRow(stringResource(R.string.investments_realised_gain)) {
                MoneyText(holding.position.realised, tone = MoneyTone.SIGNED_ALERT, explicit = holding.position.realised != 0L, style = MaterialTheme.typography.bodyLarge)
            }
            FigureRow(stringResource(R.string.investments_dividends_received)) {
                MoneyText(holding.position.dividends, tone = MoneyTone.SIGNED, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun FigureRow(label: String, caption: String? = null, value: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (caption != null) Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        value()
    }
}

/**
 * Closing prices as one 2dp line with a soft fill beneath, primary when the range ends higher than it began and
 * error when lower, and the range's high and low named at the side. Prices are public, so nothing is masked.
 */
@Composable
private fun PriceChart(symbol: String, closes: List<Long>, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val up = closes.last() >= closes.first()
    val line = if (up) scheme.primary else scheme.error
    val high = closes.max()
    val low = closes.min()
    val span = (high - low).coerceAtLeast(1).toFloat()
    val description = stringResource(R.string.investments_price_chart, symbol)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(percentChange(closes.last() - closes.first(), closes.first()), style = MaterialTheme.typography.titleMedium, color = line)
            Text(formatPrice(high), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = description }) {
            val step = if (closes.size > 1) size.width / (closes.size - 1) else 0f
            fun yOf(value: Long) = size.height * (1f - (value - low) / span)
            val path = Path()
            closes.forEachIndexed { index, close ->
                if (index == 0) path.moveTo(0f, yOf(close)) else path.lineTo(index * step, yOf(close))
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.24f), line.copy(alpha = 0f))))
            drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(line, 4.dp.toPx(), Offset(size.width, yOf(closes.last())))
        }
        Text(formatPrice(low), style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
    }
}
