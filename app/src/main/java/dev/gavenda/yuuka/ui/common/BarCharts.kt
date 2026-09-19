package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.DaySeriesEntry
import dev.gavenda.yuuka.domain.RankableEntry
import dev.gavenda.yuuka.domain.axisScale
import dev.gavenda.yuuka.domain.compactAmount
import dev.gavenda.yuuka.domain.currencySymbol
import dev.gavenda.yuuka.domain.dailyAverage
import dev.gavenda.yuuka.domain.formatLongDate
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject

/**
 * A day-of-month spend chart. Chart.js draws the full web equivalent
 * (`SpendChart.vue`); Compose Canvas is the native fit here, so the shape is
 * kept — one bar per day.
 *
 * It follows Material's expressive chart: wide pill bars with the day beneath each, a value axis down
 * the right and a thin line across at the month's daily average ([dailyAverage]). A day at or above
 * the average is set apart — a tertiary bar carrying a scalloped [badgeShape] with the display
 * [currency]'s symbol inside. A day with nothing spent is only a faint empty slot.
 *
 * A month is too many wide bars for a phone, so the plot scrolls sideways (starting at the latest day
 * with spending) while the axis stays put. Tapping a day names it, as hovering does on the web: the
 * selected bar keeps its colour while the others dim, and a tooltip above it gives the date and amount,
 * through [AmountVisibility.displayMoney] so a hidden amount stays hidden. The axis figures are masked
 * with it: they would give the magnitude away just as the amount does.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailySpendChart(
    days: List<DaySeriesEntry>,
    modifier: Modifier = Modifier,
    currency: String = DEFAULT_CURRENCY,
    badgeShape: RoundedPolygon = MaterialShapes.Cookie12Sided,
) {
    val visibility = koinInject<AmountVisibility>()
    val hidden by visibility.hidden.collectAsStateWithLifecycle()
    val max = days.maxOfOrNull { it.amount } ?: 0L
    val average = dailyAverage(days)
    val scale = remember(max) { axisScale(max) }
    val glyph = currencySymbol(currency)
    val month = days.firstOrNull()?.date?.take(7)

    val metrics = ChartMetrics(LocalDensity.current, days.size, scale.top)
    val badge = badgeShape.toShape()
    val textMeasurer = rememberTextMeasurer()
    val scrollState = rememberScrollState()
    var viewportWidth by remember { mutableIntStateOf(0) }

    // A different month is a different chart, so the selection does not carry over to it.
    var selected by remember(month) { mutableStateOf<Int?>(null) }
    val selectedEntry = selected?.let { days.getOrNull(it) }

    val colors = MaterialTheme.colorScheme
    val dayStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val selectedDayStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.Medium)
    val axisStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val averageStyle = MaterialTheme.typography.labelSmall.copy(color = colors.primary, fontWeight = FontWeight.Medium)
    val glyphStyle = MaterialTheme.typography.labelMedium.copy(
        color = colors.onTertiaryContainer,
        fontWeight = FontWeight.Medium,
        // A one-character symbol has room to be read; a three-letter code has to shrink to fit the badge.
        fontSize = when (glyph.length) {
            1 -> 13.sp
            2 -> 10.sp
            else -> 8.sp
        },
    )

    // Something that scrolls under the finger is not something to keep a tooltip pinned to.
    LaunchedEffect(scrollState.isScrollInProgress) { if (scrollState.isScrollInProgress) selected = null }

    // Start on the latest day with spending, its bar against the right edge.
    val lastSpent = days.indexOfLast { it.amount > 0 }
    LaunchedEffect(month, viewportWidth) {
        if (viewportWidth <= 0 || lastSpent < 0) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }.first { it > 0 }
        scrollState.scrollTo((metrics.left(lastSpent) + metrics.barWidth + metrics.pad - viewportWidth).toInt().coerceAtLeast(0))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(PLOT_HEIGHT + LABEL_HEIGHT)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().onSizeChanged { viewportWidth = it.width }) {
                Box(modifier = Modifier.fillMaxHeight().horizontalScroll(scrollState)) {
                    // The plot's own box, moving with the scroll, so the tooltip is anchored to it and not to the viewport.
                    Box(modifier = Modifier.width(with(LocalDensity.current) { metrics.contentWidth.toDp() }).fillMaxHeight()) {
                        Canvas(
                            modifier = Modifier.fillMaxSize().pointerInput(days.size, scale.top) {
                                detectTapGestures { offset ->
                                    val index = metrics.indexAt(offset.x)
                                    selected = if (selected == index) null else index
                                }
                            },
                        ) {
                            if (max <= 0 || days.isEmpty()) return@Canvas

                            val badgeOutline = badge.createOutline(Size(metrics.badgeSize, metrics.badgeSize), layoutDirection, this)
                            val glyphLayout = textMeasurer.measure(glyph, glyphStyle, maxLines = 1, softWrap = false)

                            // Behind the bars, so a bar covers the line where it crosses.
                            if (average != null) {
                                val y = metrics.y(average)
                                drawLine(colors.primary, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
                            }

                            days.forEachIndexed { index, entry ->
                                val left = metrics.left(index)
                                val highlighted = average != null && entry.amount > 0 && entry.amount >= average
                                // With a day selected the rest step back, so the one being read stands out.
                                val fade = if (selected == null || selected == index) 1f else DIMMED_ALPHA

                                val barColor = when {
                                    entry.amount <= 0 -> colors.surfaceVariant
                                    highlighted -> colors.tertiary
                                    else -> colors.primary
                                }
                                val height = metrics.barHeight(entry.amount, highlighted)
                                val top = metrics.plotHeight - height
                                drawRoundRect(
                                    color = barColor.copy(alpha = barColor.alpha * fade),
                                    topLeft = Offset(left, top),
                                    size = Size(metrics.barWidth, height),
                                    cornerRadius = CornerRadius(metrics.barWidth / 2),
                                )

                                if (highlighted) {
                                    val badgeLeft = left + (metrics.barWidth - metrics.badgeSize) / 2
                                    val badgeTop = top + metrics.badgeInset
                                    translate(left = badgeLeft, top = badgeTop) {
                                        drawOutline(outline = badgeOutline, color = colors.tertiaryContainer.copy(alpha = fade))
                                    }
                                    drawText(
                                        textLayoutResult = glyphLayout,
                                        color = colors.onTertiaryContainer.copy(alpha = fade),
                                        topLeft = Offset(
                                            badgeLeft + (metrics.badgeSize - glyphLayout.size.width) / 2,
                                            badgeTop + (metrics.badgeSize - glyphLayout.size.height) / 2,
                                        ),
                                    )
                                }

                                val label = textMeasurer.measure(entry.day.toString(), if (selected == index) selectedDayStyle else dayStyle, maxLines = 1)
                                drawText(
                                    textLayoutResult = label,
                                    topLeft = Offset(left + (metrics.barWidth - label.size.width) / 2, metrics.plotHeight + metrics.labelGap),
                                )
                            }
                        }

                        if (selectedEntry != null && max > 0) {
                            val index = selected ?: return@Box
                            val highlighted = average != null && selectedEntry.amount > 0 && selectedEntry.amount >= average
                            val anchorX = metrics.left(index) + metrics.barWidth / 2
                            val anchorY = metrics.plotHeight - metrics.barHeight(selectedEntry.amount, highlighted)
                            val margin = with(LocalDensity.current) { 8.dp.toPx() }

                            Popup(
                                popupPositionProvider = remember(anchorX, anchorY, margin) {
                                    object : PopupPositionProvider {
                                        override fun calculatePosition(
                                            anchorBounds: IntRect,
                                            windowSize: IntSize,
                                            layoutDirection: LayoutDirection,
                                            popupContentSize: IntSize,
                                        ): IntOffset {
                                            // Centred over the bar, but never off either edge of the screen.
                                            val x = (anchorBounds.left + anchorX - popupContentSize.width / 2f)
                                                .toInt()
                                                .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                                            val y = (anchorBounds.top + anchorY - popupContentSize.height - margin).toInt().coerceAtLeast(0)
                                            return IntOffset(x, y)
                                        }
                                    }
                                },
                            ) {
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = colors.inverseSurface,
                                    contentColor = colors.inverseOnSurface,
                                    shadowElevation = 3.dp,
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(formatLongDate(selectedEntry.date), style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            visibility.displayMoney(selectedEntry.amount, currency),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colors.inverseOnSurface.copy(alpha = 0.72f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // The value axis stays where it is while the plot scrolls behind its own edge.
            Canvas(modifier = Modifier.width(AXIS_WIDTH).fillMaxHeight()) {
                if (max <= 0) return@Canvas

                val inset = 8.dp.toPx()
                val averageY = average?.let { metrics.y(it) }

                scale.ticks.forEach { tick ->
                    val y = metrics.y(tick)
                    // The average's own label wins where the two would sit on top of each other.
                    if (averageY != null && kotlin.math.abs(y - averageY) < AXIS_LABEL_CLEARANCE.toPx()) return@forEach
                    val layout = textMeasurer.measure(if (hidden) AXIS_MASK else compactAmount(tick), axisStyle, maxLines = 1)
                    drawText(layout, topLeft = Offset(inset, y - layout.size.height / 2f))
                }
                if (average != null && averageY != null) {
                    val layout = textMeasurer.measure(if (hidden) AXIS_MASK else compactAmount(average), averageStyle, maxLines = 1)
                    drawText(layout, topLeft = Offset(inset, averageY - layout.size.height / 2f))
                }
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(swatch = { Box(Modifier.size(width = 16.dp, height = 2.dp).background(colors.primary)) }, label = stringResource(R.string.chart_average_per_day))
            LegendItem(swatch = { Box(Modifier.size(10.dp).background(colors.tertiary, CircleShape)) }, label = stringResource(R.string.chart_at_or_above_average))
        }
    }
}

@Composable
private fun LegendItem(swatch: @Composable () -> Unit, label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        swatch()
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val BAR_WIDTH = 28.dp
private val BAR_GAP = 6.dp
private val PLOT_HEIGHT = 168.dp
private val LABEL_HEIGHT = 24.dp
private val AXIS_WIDTH = 44.dp
private val AXIS_LABEL_CLEARANCE = 14.dp

/** What stands in for an axis figure while amounts are hidden. Shorter than [dev.gavenda.yuuka.domain.MASK]: the axis is narrow. */
private const val AXIS_MASK = "••"

/** How far the bars other than the selected one step back. */
private const val DIMMED_ALPHA = 0.4f

/**
 * Where everything sits in the plot, in pixels: the drawing, the touch handling and the tooltip all
 * read it, so what is touched is what is drawn. The plot is [plotHeight] tall with the day labels
 * beneath it, and its bars reach the axis' [top] at the very top.
 */
private class ChartMetrics(density: Density, private val count: Int, private val top: Long) {
    val barWidth = with(density) { BAR_WIDTH.toPx() }
    val plotHeight = with(density) { PLOT_HEIGHT.toPx() }
    val labelGap = with(density) { 6.dp.toPx() }
    val badgeInset = with(density) { 3.dp.toPx() }
    val badgeSize = barWidth - 2 * badgeInset

    private val gap = with(density) { BAR_GAP.toPx() }

    /** Room at either end of the plot, so the first and last bars are not up against the edge. */
    val pad = with(density) { 4.dp.toPx() }
    val contentWidth = pad * 2 + count * barWidth + (count - 1).coerceAtLeast(0) * gap

    fun left(index: Int) = pad + index * (barWidth + gap)

    /** The day under [x], which is the nearest one for a touch in the gap between two bars or past either end. */
    fun indexAt(x: Float) = ((x - pad + gap / 2) / (barWidth + gap)).toInt().coerceIn(0, count - 1)

    /** The height up the plot of [value] on the axis. */
    fun y(value: Long) = plotHeight * (1f - value.toFloat() / top.toFloat())

    /**
     * A bar for [amount], never shorter than it is wide — so it is still a pill — nor, when [highlighted],
     * shorter than the badge it has to hold.
     */
    fun barHeight(amount: Long, highlighted: Boolean): Float {
        if (amount <= 0) return barWidth
        val least = if (highlighted) badgeSize + 2 * badgeInset else barWidth
        return maxOf(plotHeight * amount.toFloat() / top.toFloat(), least)
    }
}

/**
 * A horizontal ranked bar per category, each carrying its own value label —
 * colour never carries the figure alone. Mirrors `CategoryBars.vue`.
 */
@Composable
fun CategoryBarList(entries: List<RankableEntry>, modifier: Modifier = Modifier, currency: String = DEFAULT_CURRENCY) {
    val visibility = koinInject<AmountVisibility>()
    val total = entries.sumOf { it.actual }
    val max = entries.maxOfOrNull { it.actual } ?: 0L

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        entries.forEach { entry ->
            val share = if (total > 0) ((entry.actual.toDouble() / total.toDouble()) * 100).toInt() else 0
            val fraction = if (max > 0) (entry.actual.toFloat() / max.toFloat()).coerceIn(0f, 1f) else 0f
            val color =
                runCatching { Color(android.graphics.Color.parseColor(entry.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "${visibility.displayMoney(entry.actual, currency)} · $share%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth().height(8.dp),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    drawStopIndicator = {},
                )
            }
        }
    }
}
