package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.PayeeKind
import dev.gavenda.yuuka.domain.isExhausted
import dev.gavenda.yuuka.domain.rankPayees

/**
 * A remembered-payee autosuggest: typing offers back what was last filed under
 * that name so the rest of the form can fill itself in. Mirrors `PayeeInput.vue`.
 */
@Composable
fun PayeeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    payees: List<Payee>,
    onSelect: (Payee) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val matches = remember(payees, value) { rankPayees(payees, value) }
    val showList = focused && matches.isNotEmpty() && !isExhausted(matches, value)

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        )

        DropdownMenu(expanded = showList, onDismissRequest = { }, properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
            matches.forEach { entry ->
                DropdownMenuItem(
                    text = {
                        androidx.compose.foundation.layout.Column {
                            Text(entry.payee)
                            val hint = listOfNotNull(
                                if (entry.kind == PayeeKind.transfer) listOfNotNull(entry.accountName, entry.toAccountName).joinToString(" → ").ifBlank { null } else entry.accountName,
                                entry.categoryName,
                            ).joinToString(" · ")
                            if (hint.isNotBlank()) {
                                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    onClick = { onSelect(entry); focused = false },
                    modifier = Modifier.widthIn(min = 240.dp),
                )
            }
        }
    }
}
