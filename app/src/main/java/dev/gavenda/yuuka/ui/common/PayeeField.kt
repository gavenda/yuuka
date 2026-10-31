package dev.gavenda.yuuka.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
    field: FieldState? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // Put away by a tap outside it, Back or a choice, until the name is typed in again.
    var dismissed by remember { mutableStateOf(false) }
    val matches = remember(payees, value) { rankPayees(payees, value) }
    var fieldWidth by remember { mutableIntStateOf(0) }
    val showList = focused && !dismissed && matches.isNotEmpty() && !isExhausted(matches, value)

    // Back puts the list away before it closes the form.
    BackHandler(enabled = showList) { dismissed = true }

    androidx.compose.foundation.layout.Box(modifier = modifier) {
        YuukaTextField(
            value = value,
            onValueChange = {
                dismissed = false
                onValueChange(it)
            },
            label = label,
            placeholder = placeholder,
            singleLine = true,
            field = field,
            modifier = Modifier.fillMaxWidth().onSizeChanged { fieldWidth = it.width }.onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) dismissed = false
            },
        )

        DropdownMenu(
            expanded = showList,
            onDismissRequest = { dismissed = true },
            modifier = Modifier.width(with(LocalDensity.current) { fieldWidth.toDp() }),
            properties = androidx.compose.ui.window.PopupProperties(focusable = false),
        ) {
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
                                Text(hint, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    onClick = {
                        onSelect(entry)
                        dismissed = true
                    },
                )
            }
        }
    }
}
