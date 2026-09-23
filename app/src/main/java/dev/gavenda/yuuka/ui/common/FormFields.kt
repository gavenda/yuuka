package dev.gavenda.yuuka.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.NAME_MAX

/**
 * The line beneath a field. It carries the field's error when it has one and its [hint] otherwise — Material 3
 * keeps both in the same slot, so an error replaces the hint rather than stacking with it. The error is a live
 * region, so a screen reader reads it out as it appears rather than leaving it to be found.
 */
internal fun supportText(error: String?, hint: String?): (@Composable () -> Unit)? = when {
    error != null -> {
        { Text(error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
    }

    hint != null -> {
        { Text(hint) }
    }

    else -> null
}

/**
 * What is wrong with a required name, if anything: empty, longer than the API takes, or — where names are kept
 * unique — already in use, worded by [takenMessage] since it reads differently for a tag than for a category.
 */
@Composable
fun nameProblem(value: String, @StringRes takenMessage: Int? = null, isTaken: (String) -> Boolean = { false }): String? {
    val name = value.trim()
    return when {
        name.isEmpty() -> stringResource(R.string.error_name_required)
        name.length > NAME_MAX -> stringResource(R.string.error_too_long, NAME_MAX)
        takenMessage != null && isTaken(name) -> stringResource(takenMessage)
        else -> null
    }
}

/** The trailing icon Material 3 puts on a text field in error. */
@Composable
internal fun ErrorIcon() {
    Icon(Icons.Filled.Error, contentDescription = null)
}

/**
 * An error for a control that is not a text field — a row of chips, a preference row — drawn where a text field
 * would draw its supporting text: in the error colour, led by the error icon, read out as it appears. Nothing
 * is drawn when there is no [message].
 */
@Composable
fun FieldError(message: String?, modifier: Modifier = Modifier) {
    if (message == null) return

    Row(
        modifier = modifier.padding(top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Error, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * An outlined text field that can be in error. Give it a [field] from a [FormValidation] and it turns to the
 * error colour, says what is wrong beneath itself (in place of its [hint]), shows the error icon, and is
 * announced as invalid; leave [field] out for a field with nothing to get wrong.
 */
@Composable
fun YuukaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    field: FieldState? = null,
    hint: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = false,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val error = field?.error

    OutlinedTextField(
        value = value,
        // Typing in a field is visiting it: the error shows as soon as there is something to say, not only once focus leaves.
        onValueChange = { field?.touch(); onValueChange(it) },
        modifier = modifier.tracked(field),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = if (error != null) {
            { ErrorIcon() }
        } else {
            null
        },
        isError = error != null,
        supportingText = supportText(error, hint),
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
    )
}

/**
 * An outlined combo box: a read-only field that opens a menu of [options]. Like [YuukaTextField] it can be in
 * error through [field]; unlike it, it keeps the menu's arrow as its trailing icon, as Material 3 does. A field
 * that only ever opens a menu has nothing to blur, so it counts as visited when its menu closes.
 */
@Composable
fun <T> DropdownField(
    label: String,
    value: String,
    options: List<SelectOption<T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    field: FieldState? = null,
    hint: String? = null,
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val error = field?.error

    fun close() {
        open = false
        field?.touch()
    }

    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            isError = error != null,
            supportingText = supportText(error, hint),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled).tracked(field),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { close() }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (option.indent) "    ${option.label}" else option.label) },
                    onClick = {
                        onSelect(option.value)
                        close()
                    },
                )
            }
        }
    }
}
