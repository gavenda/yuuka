package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

/**
 * A read-only field that opens a picker instead of a keyboard, used for date/time entry. It stays
 * an enabled field, so it keeps its focus and accessibility semantics; the tap is read off its
 * interaction source because the field's own gesture handling would otherwise swallow a `clickable`.
 *
 * With [enabled] false it shows its [value] but never opens — for a value someone else decides,
 * such as the time of day of a transaction a subscription posted.
 */
@Composable
fun PickerField(
    value: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val currentOnClick by rememberUpdatedState(onClick)
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { if (it is PressInteraction.Release) currentOnClick() }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}
