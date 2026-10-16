package dev.gavenda.yuuka.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.TrendMonth
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.axisRange
import dev.gavenda.yuuka.domain.axisScale
import dev.gavenda.yuuka.domain.compactAmount
import dev.gavenda.yuuka.domain.formatMonth
import dev.gavenda.yuuka.domain.shortMonth
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.ceil

/*
 * The geometry both trend charts share, as the web's (`IncomeSpendChart.vue`, `NetWorthChart.vue`): a plot
 * with the months beneath it and a value axis down the right that is masked with the amounts.
 */
private val PLOT_HEIGHT = 160.dp
private val LABEL_HEIGHT = 24.dp
private val AXIS_WIDTH = 44.dp
private const val AXIS_MASK = "••"
private const val DIMMED_ALPHA = 0.4f

/**
 * Income and spending month by month: a pair of bars per month on one axis, income in the inflow colour and
 * spending in the neutral the figures use for a routine outflow, 2dp apart. Two series, so a legend names both;
 * tapping a month names its figures in a tooltip. Mirrors `IncomeSpendChart.vue`.
 */
@Composable
internal fun IncomeSpendChart(months: List<TrendMonth>, currency: String, modifier: Modifier = Modifier) {
    val visibility = koinInject<AmountVisibility>()
    val hidden by visibility.hidden.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scale = remember(months) { axisScale(months.maxOfOrNull { maxOf(it.income, it.expenses) } ?: 0) }
    var selected by remember(months.lastOrNull()?.month) { mutableStateOf<Int?>(null) }
    val kept = months.sumOf { it.net }

    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val selectedLabelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Medium)
    val chartDescription = stringResource(R.string.dashboard_trend_income_spend)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(chartDescription, style = MaterialTheme.typography.titleLarge)
            Text(
                pluralStringResource(
                    if (kept >= 0) R.plurals.dashboard_trend_kept else R.plurals.dashboard_trend_overspent,
                    months.size,
                    visibility.displayMoney(abs(kept), currency),
                    months.size,
                ),
                style = MaterialTheme.typography.bodySmall,
            )

            Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(PLOT_HEIGHT + LABEL_HEIGHT)) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val plotPx = with(density) { PLOT_HEIGHT.toPx() }
                    val slot = if (months.isNotEmpty()) widthPx / months.size else 0f
                    val gap = with(density) { 2.dp.toPx() }
                    val barWidth = ((slot - with(density) { 12.dp.toPx() } - gap) / 2).coerceIn(with(density) { 4.dp.toPx() }, with(density) { 20.dp.toPx() })
                    fun heightOf(value: Long) = if (value > 0) maxOf(plotPx * value / scale.top, with(density) { 2.dp.toPx() }) else 0f

                    Canvas(
                        modifier = Modifier.fillMaxSize()
                            .semantics { contentDescription = chartDescription }
                            .pointerInput(months.size, widthPx) {
                                detectTapGestures { offset ->
                                    val index = (offset.x / slot).toInt().coerceIn(0, months.size - 1)
                                    selected = if (selected == index) null else index
                                }
                            },
                    ) {
                        scale.ticks.forEach { tick ->
                            val y = plotPx * (1f - tick.toFloat() / scale.top)
                            drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        }
                        val radius = CornerRadius(minOf(4.dp.toPx(), barWidth / 2))
                        months.forEachIndexed { index, entry ->
                            val fade = if (selected == null || selected == index) 1f else DIMMED_ALPHA
                            val centre = slot * index + slot / 2
                            val income = heightOf(entry.income)
                            val spent = heightOf(entry.expenses)
                            if (income > 0) {
                                drawRoundRect(colors.primary.copy(alpha = fade), Offset(centre - gap / 2 - barWidth, plotPx - income), Size(barWidth, income), radius)
                            }
                            if (spent > 0) {
                                drawRoundRect(colors.onSurfaceVariant.copy(alpha = fade), Offset(centre + gap / 2, plotPx - spent), Size(barWidth, spent), radius)
                            }
                            val label = textMeasurer.measure(shortMonth(entry.month), if (selected == index) selectedLabelStyle else labelStyle, maxLines = 1)
                            drawText(label, topLeft = Offset(centre - label.size.width / 2f, plotPx + 6.dp.toPx()))
                        }
                    }

                    val entry = selected?.let { months.getOrNull(it) }
                    if (entry != null) {
                        val index = selected ?: 0
                        ChartTooltip(
                            anchorX = slot * index + slot / 2,
                            anchorY = plotPx - maxOf(heightOf(entry.income), heightOf(entry.expenses)),
                            title = formatMonth(entry.month),
                            lines = listOf(
                                "${stringResource(R.string.category_kind_income)}: ${visibility.displayMoney(entry.income, currency)}",
                                "${stringResource(R.string.label_spent)}: ${visibility.displayMoney(entry.expenses, currency)}",
                                "${stringResource(R.string.dashboard_trend_net)}: ${visibility.displayMoney(entry.net, currency)}",
                            ),
                        )
                    }
                }

                ValueAxis(ticks = scale.ticks, yOf = { 1f - it.toFloat() / scale.top }, hidden = hidden)
            }

            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendSwatch(colors.primary, stringResource(R.string.category_kind_income))
                LegendSwatch(colors.onSurfaceVariant, stringResource(R.string.label_spent))
            }
        }
    }
}

/**
 * Net worth at each month's end: one 2dp line in the primary colour with an 8dp marker per month ringed in the
 * card's colour, on an axis that need not start at zero ([axisRange]). One series, so the title names it and
 * there is no legend. Tapping a month stands a crosshair on it and names it in a tooltip. Mirrors `NetWorthChart.vue`.
 */
@Composable
internal fun NetWorthChart(months: List<TrendMonth>, currency: String, modifier: Modifier = Modifier) {
    val visibility = koinInject<AmountVisibility>()
    val hidden by visibility.hidden.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val range = remember(months) { axisRange(months.minOf { it.netWorth }, months.maxOf { it.netWorth }) }
    var selected by remember(months.lastOrNull()?.month) { mutableStateOf<Int?>(null) }
    val span = (range.top - range.bottom).toFloat()
    fun fractionOf(value: Long) = 1f - (value - range.bottom) / span

    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val selectedLabelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Medium)
    val chartDescription = stringResource(R.string.dashboard_trend_net_worth)

    val first = months.first()
    val change = months.last().netWorth - first.netWorth
    val since = formatMonth(first.month)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(chartDescription, style = MaterialTheme.typography.titleLarge)
            Text(
                when {
                    change > 0 -> stringResource(R.string.dashboard_trend_up, visibility.displayMoney(change, currency), since)
                    change < 0 -> stringResource(R.string.dashboard_trend_down, visibility.displayMoney(-change, currency), since)
                    else -> stringResource(R.string.dashboard_trend_level, since)
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Row(modifier = Modifier.padding(top = 12.dp).fillMaxWidth().height(PLOT_HEIGHT + LABEL_HEIGHT)) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val plotPx = with(density) { PLOT_HEIGHT.toPx() }
                    val pad = with(density) { 8.dp.toPx() }
                    val spacing = if (months.size > 1) (widthPx - 2 * pad) / (months.size - 1) else 0f
                    // The least room a month's label needs, so a narrow plot labels every other one.
                    val labelEvery = maxOf(1, ceil(with(density) { 36.dp.toPx() } / maxOf(1f, spacing)).toInt())
                    fun xOf(index: Int) = pad + index * spacing

                    Canvas(
                        modifier = Modifier.fillMaxSize()
                            .semantics { contentDescription = chartDescription }
                            .pointerInput(months.size, widthPx) {
                                detectTapGestures { offset ->
                                    val index = if (spacing > 0) ((offset.x - pad) / spacing + 0.5f).toInt().coerceIn(0, months.size - 1) else 0
                                    selected = if (selected == index) null else index
                                }
                            },
                    ) {
                        range.ticks.forEach { tick ->
                            val y = plotPx * fractionOf(tick)
                            drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                        }
                        selected?.let { drawLine(colors.onSurfaceVariant, Offset(xOf(it), 0f), Offset(xOf(it), plotPx), strokeWidth = 1.dp.toPx()) }

                        val line = Path()
                        months.forEachIndexed { index, entry ->
                            val y = plotPx * fractionOf(entry.netWorth)
                            if (index == 0) line.moveTo(xOf(index), y) else line.lineTo(xOf(index), y)
                        }
                        drawPath(line, colors.primary, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

                        months.forEachIndexed { index, entry ->
                            val centre = Offset(xOf(index), plotPx * fractionOf(entry.netWorth))
                            val radius = if (selected == index) 6.dp.toPx() else 4.dp.toPx()
                            // A ring in the card's colour keeps a marker clear of the line it sits on.
                            drawCircle(colors.surfaceContainerHighest, radius + 2.dp.toPx(), centre)
                            drawCircle(colors.primary, radius, centre)
                            if ((months.size - 1 - index) % labelEvery == 0) {
                                val label = textMeasurer.measure(shortMonth(entry.month), if (selected == index) selectedLabelStyle else labelStyle, maxLines = 1)
                                drawText(label, topLeft = Offset(centre.x - label.size.width / 2f, plotPx + 6.dp.toPx()))
                            }
                        }
                    }

                    val entry = selected?.let { months.getOrNull(it) }
                    if (entry != null) {
                        val index = selected ?: 0
                        ChartTooltip(
                            anchorX = xOf(index),
                            anchorY = plotPx * fractionOf(entry.netWorth) - with(density) { 4.dp.toPx() },
                            title = formatMonth(entry.month),
                            lines = listOf(visibility.displayMoney(entry.netWorth, currency)),
                        )
                    }
                }

                ValueAxis(ticks = range.ticks, yOf = { fractionOf(it) }, hidden = hidden)
            }
        }
    }
}

/** The value axis down a chart's right, its figures masked while amounts are hidden: they would give the magnitude away. */
@Composable
private fun ValueAxis(ticks: List<Long>, yOf: (Long) -> Float, hidden: Boolean) {
    val textMeasurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(modifier = Modifier.width(AXIS_WIDTH).fillMaxHeight()) {
        val plotPx = PLOT_HEIGHT.toPx()
        ticks.forEach { tick ->
            val layout = textMeasurer.measure(if (hidden) AXIS_MASK else compactAmount(tick), style, maxLines = 1)
            drawText(layout, topLeft = Offset(8.dp.toPx(), plotPx * yOf(tick) - layout.size.height / 2f))
        }
    }
}

@Composable
private fun LegendSwatch(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, MaterialTheme.shapes.extraSmall))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** A tooltip above a point of the plot, centred on it but never off either edge of the screen, as the daily chart's is. */
@Composable
private fun ChartTooltip(anchorX: Float, anchorY: Float, title: String, lines: List<String>) {
    val margin = with(LocalDensity.current) { 8.dp.toPx() }
    Popup(
        popupPositionProvider = remember(anchorX, anchorY, margin) {
            object : PopupPositionProvider {
                override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                    val x = (anchorBounds.left + anchorX - popupContentSize.width / 2f).toInt()
                        .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                    val y = (anchorBounds.top + anchorY - popupContentSize.height - margin).toInt().coerceAtLeast(0)
                    return IntOffset(x, y)
                }
            }
        },
    ) {
        Surface(shape = MaterialTheme.shapes.small, shadowElevation = 3.dp) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
