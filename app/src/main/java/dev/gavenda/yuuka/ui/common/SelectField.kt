package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.domain.CategoryGroup

/** One choice in a [SelectField]; [indent] sets a subcategory under its parent. */
data class SelectOption<T>(val value: T, val label: String, val indent: Boolean = false)

/**
 * A preference row — the label, with the current choice under it — that, when tapped, opens a dialog of radio buttons to change it,
 * the way a list preference works in Android's own settings. Choosing closes the dialog at once. It is for the settings
 * screens only; a form keeps its outlined combo box (`ExposedDropdownMenuBox`).
 * It is a row rather than a field, so it belongs outside a screen's side padding: the highlight runs the full width
 * and the row's own inset keeps the text aligned with the fields around it.
 * [value] is the text shown in the field, which is not always an option's label (an unset choice reads
 * as a placeholder), and [selected] is what the radio buttons mark.
 */
@Composable
fun <T> SelectField(
    label: String,
    value: String,
    options: List<SelectOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    /** Inside the tap target, keeping the text off the edge of the highlight. */
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
) {
    var open by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button) { open = true }
            .padding(contentPadding),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        )
        Text(
            value.ifBlank { " " },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                LazyColumn {
                    items(options) { option ->
                        val isSelected = option.value == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = isSelected,
                                    onClick = {
                                        onSelect(option.value)
                                        open = false
                                    },
                                    role = Role.RadioButton,
                                )
                                .padding(start = if (option.indent) 24.dp else 0.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(option.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** The uncategorised choice (null), then each parent followed by its own children, indented. */
fun categoryOptions(groups: List<CategoryGroup>, noneLabel: String): List<SelectOption<String?>> =
    listOf(SelectOption<String?>(null, noneLabel)) + groups.flatMap { group ->
        listOf(SelectOption<String?>(group.parent.id, group.parent.name)) +
            group.children.map { SelectOption<String?>(it.id, it.name, indent = true) }
    }
