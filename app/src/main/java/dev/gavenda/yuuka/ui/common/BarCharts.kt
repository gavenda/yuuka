package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.domain.AmountVisibility
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY
import dev.gavenda.yuuka.domain.DaySeriesEntry
import dev.gavenda.yuuka.domain.RankableEntry
import org.koin.compose.koinInject

/**
 * A day-of-month spend chart. Chart.js draws the full web equivalent
 * (`SpendChart.vue`); Compose Canvas is the native fit here, so the shape is
 * kept — one bar per day, zero-height for days with nothing spent.
 */
@Composable
fun DailySpendChart(days: List<DaySeriesEntry>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    val max = days.maxOfOrNull { it.amount } ?: 0L

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        if (max <= 0 || days.isEmpty()) return@Canvas

        val gap = 2.dp.toPx()
        val barWidth = ((size.width - gap * (days.size - 1)) / days.size).coerceAtLeast(1f)

        days.forEachIndexed { index, entry ->
            val barHeight = (entry.amount.toFloat() / max.toFloat()) * size.height
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(1f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            )
        }
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
            val color = runCatching { Color(android.graphics.Color.parseColor(entry.color)) }.getOrDefault(MaterialTheme.colorScheme.primary)

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "${visibility.displayMoney(entry.actual, currency)} · $share%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .background(color, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
