package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R

/**
 * A single-line outlined field with cut-down vertical padding, for an inline
 * row beside a [androidx.compose.material3.Button] or
 * [androidx.compose.material3.TextButton] — the standard `OutlinedTextField`'s
 * fixed 56dp minimum height always dwarfs a 40dp button next to it, and that
 * overload has no way to shrink it.
 */
@Composable
fun DenseOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    /** Non-null shows a trailing clear icon (per M3's text field guidance) whenever there's text to clear. */
    onClear: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                placeholder = placeholder?.let { { Text(it) } },
                prefix = prefix?.let { { Text(it) } },
                supportingText = supportingText?.let { { Text(it) } },
                trailingIcon = if (onClear != null && value.isNotEmpty()) {
                    {
                        IconButton(onClick = onClear, enabled = enabled) {
                            Icon(Icons.Filled.Cancel, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                } else {
                    null
                },
                contentPadding = OutlinedTextFieldDefaults.contentPaddingWithoutLabel(top = 8.dp, bottom = 8.dp),
            )
        },
    )
}
