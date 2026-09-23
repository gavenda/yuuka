package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var picking by rememberSaveable { mutableStateOf(false) }

    if (picking) {
        MonthPickerDialog(
            month = month,
            onDismiss = { picking = false },
            onPicked = {
                picking = false
                onMonthChange(it)
            },
        )
    }

    Card(modifier = modifier) {
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
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = stringResource(R.string.cd_pick_month)) { picking = true }
                    .padding(vertical = 12.dp),
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
