package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.gavenda.yuuka.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * Material 3's own date picker, in the standard dialog. Its state is a UTC-midnight
 * timestamp whatever the device's zone, so the conversions to and from [LocalDate]
 * are pinned to UTC — using the local zone would shift the date by a day.
 */
@Composable
fun YuukaDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
    /** When set, days before it cannot be picked. */
    earliest: LocalDate? = null,
) {
    val selectableDates = remember(earliest) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                earliest == null || utcTimeMillis >= earliest.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

            override fun isSelectableYear(year: Int): Boolean = earliest == null || year >= earliest.year
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = selectableDates,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/**
 * The expressive ("vibrant") time picker: scrolling hour and minute columns, with a
 * toggle to type the time in instead.
 */
@Composable
fun YuukaTimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = false)
    // A Boolean rather than the mode itself: TimePickerDisplayMode is a value class, which cannot be saved in a Bundle.
    var typing by rememberSaveable { mutableStateOf(false) }
    val displayMode = if (typing) TimePickerDisplayMode.Input else TimePickerDisplayMode.Scroll

    VibrantTimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.isInputValid,
                onClick = { onPicked(LocalTime.of(state.hour, state.minute)) },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        modeToggleButton = {
            TimePickerDialogDefaults.ScrollDisplayModeToggle(
                onDisplayModeChange = { typing = !typing },
                displayMode = displayMode,
            )
        },
    ) {
        if (typing) {
            TimeInput(state = state, shapes = TimePickerDefaults.shapes())
        } else {
            TimeScroll(state = state, shapes = TimePickerDefaults.shapes())
        }
    }
}
