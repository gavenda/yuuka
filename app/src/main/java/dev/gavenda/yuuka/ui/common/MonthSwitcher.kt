package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.addMonths
import dev.gavenda.yuuka.domain.currentMonth
import dev.gavenda.yuuka.domain.formatMonth

/**
 * The same switcher as an app bar's title: the two arrows and the month in a pill of their own.
 *
 * A phone's bar carries this in place of the screen's name — the bottom bar already says which
 * destination is showing, and the month is the one thing on these screens worth reaching at any
 * scroll position. The controls are sized down a little to leave room for "Today" beside them, and
 * the pill keeps them reading as one control rather than three loose icons on the bar.
 */
@Composable
fun MonthTitle(month: String, onMonthChange: (String) -> Unit, modifier: Modifier = Modifier) {
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

    // It fills the title slot — every pixel between the menu button and the hide-amounts switch — so the
    // month sits in the middle of the bar rather than floating against its left edge.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            IconButton(onClick = { onMonthChange(addMonths(month, -1)) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_month))
            }

            Text(
                text = formatMonth(month),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = stringResource(R.string.cd_pick_month)) { picking = true }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )

            IconButton(onClick = { onMonthChange(addMonths(month, 1)) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_month))
            }

            if (!isCurrent) {
                TextButton(
                    onClick = { onMonthChange(currentMonth()) },
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Text(stringResource(R.string.action_today), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

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
