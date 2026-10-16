package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.PALETTE
import dev.gavenda.yuuka.domain.isHexColour

/** Where a custom colour starts, and what the wheel shows while the typed hex is not one yet. */
private const val CUSTOM_START = "#64748b"

/**
 * A tag's or a category's colour (`ColourField.vue` on the web): the validated palette as a row of swatches,
 * then a ninth, custom slot. Picking that one opts out of the palette's colour-vision-deficiency guarantee,
 * so it stays a deliberate extra step — a wheel and a hex field — rather than a slot in the same row.
 */
@Composable
fun ColourField(color: String, onColorChange: (String) -> Unit, field: FieldState) {
    val isCustomColor = PALETTE.none { it.light.equals(color, ignoreCase = true) }
    val isValidColor = isHexColour(color)

    Text(stringResource(R.string.label_colour), style = MaterialTheme.typography.labelMedium)
    Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PALETTE.forEach { slot ->
            val selected = color.equals(slot.light, ignoreCase = true)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colorFromHex(slot.light))
                    .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .semantics { contentDescription = slot.name }
                    .selectable(selected = selected, role = Role.RadioButton) { onColorChange(slot.light) },
            )
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isCustomColor && isValidColor) colorFromHex(color) else MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    width = if (isCustomColor) 2.dp else 1.dp,
                    color = if (isCustomColor) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape,
                )
                .selectable(selected = isCustomColor, role = Role.RadioButton) { if (!isCustomColor) onColorChange(CUSTOM_START) },
            contentAlignment = Alignment.Center,
        ) {
            if (!isCustomColor) {
                Icon(
                    painterResource(R.drawable.add_48px),
                    contentDescription = stringResource(R.string.custom_colour),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (isCustomColor) {
        ColorWheelPicker(
            color = colorFromHex(if (isValidColor) color else CUSTOM_START),
            onColorChange = { picked -> onColorChange(picked.toHexString()) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )

        YuukaTextField(
            value = color,
            onValueChange = onColorChange,
            label = stringResource(R.string.custom_colour_hex),
            placeholder = stringResource(R.string.placeholder_hex_sample),
            singleLine = true,
            field = field,
        )
    }
}

@Composable
private fun colorFromHex(hex: String): Color =
    runCatching { Color(hex.toColorInt()) }.getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)

private fun Color.toHexString(): String = String.format("#%06X", 0xFFFFFF and toArgb())
