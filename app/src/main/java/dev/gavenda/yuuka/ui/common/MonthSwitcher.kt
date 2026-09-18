package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.addMonths
import dev.gavenda.yuuka.domain.currentMonth
import dev.gavenda.yuuka.domain.formatMonth

/** Mirrors `MonthSwitcher.vue`. */
@Composable
fun MonthSwitcher(month: String, onMonthChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val isCurrent = month == currentMonth()

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = { onMonthChange(addMonths(month, -1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_month))
            }

            Text(
                text = formatMonth(month),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            IconButton(onClick = { onMonthChange(addMonths(month, 1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_month))
            }

            if (!isCurrent) {
                TextButton(onClick = { onMonthChange(currentMonth()) }) {
                    Text(stringResource(R.string.action_today))
                }
            }
        }
    }
}
