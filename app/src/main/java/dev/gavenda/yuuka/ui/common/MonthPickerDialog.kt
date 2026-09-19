package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.currentMonth
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * A month picker in the standard Material 3 dialog. Material 3 ships a day picker but no
 * month one, so this is a year header with previous/next arrows over a 3 × 4 grid of month
 * names, styled after the date picker's own year grid. Tapping a month picks it at once.
 *
 * [month] and the result are `YYYY-MM`, the same shape the API and [MonthSwitcher] use.
 */
@Composable
fun MonthPickerDialog(
    month: String,
    onDismiss: () -> Unit,
    onPicked: (String) -> Unit,
) {
    val selected = YearMonth.parse(month)
    val current = YearMonth.parse(currentMonth())
    var year by rememberSaveable { mutableIntStateOf(selected.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { year-- }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.cd_previous_year))
                    }
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { year++ }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.cd_next_year))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    for (row in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            for (col in 0 until 3) {
                                val ym = YearMonth.of(year, row * 3 + col + 1)
                                MonthCell(
                                    label = ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                    isSelected = ym == selected,
                                    isCurrent = ym == current,
                                    onClick = { onPicked("%04d-%02d".format(ym.year, ym.monthValue)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MonthCell(
    label: String,
    isSelected: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isCurrent -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = if (isCurrent && !isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
        modifier = modifier.height(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
