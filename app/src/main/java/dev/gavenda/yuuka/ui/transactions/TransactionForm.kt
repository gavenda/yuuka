package dev.gavenda.yuuka.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.domain.expenseCategories
import dev.gavenda.yuuka.domain.groupForPicker
import dev.gavenda.yuuka.domain.incomeCategories
import dev.gavenda.yuuka.domain.parseMoney
import dev.gavenda.yuuka.domain.today
import dev.gavenda.yuuka.domain.toDecimalString
import dev.gavenda.yuuka.domain.transferCategories
import dev.gavenda.yuuka.ui.common.PayeeField
import dev.gavenda.yuuka.ui.common.showDatePicker
import dev.gavenda.yuuka.ui.common.showTimePicker
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class FormMode { EXPENSE, INCOME, TRANSFER }

private data class FormFields(
    val mode: FormMode = FormMode.EXPENSE,
    val accountId: String = "",
    val toAccountId: String = "",
    val categoryId: String = "",
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime? = null,
    val payee: String = "",
    val notes: String = "",
)

private fun seedFrom(transaction: Transaction?, transferToAccountId: String?, defaultAccountId: String?, accounts: List<Account>): FormFields {
    if (transaction == null) {
        val preferred = accounts.firstOrNull { it.id == defaultAccountId } ?: accounts.firstOrNull()
        return FormFields(accountId = preferred?.id ?: "", date = LocalDate.parse(today()), time = LocalTime.now())
    }

    val isTransfer = transaction.transferId != null
    val date = LocalDate.parse(transaction.occurredOn.take(10))
    val time = if (transaction.occurredOn.length > 10) runCatching { LocalTime.parse(transaction.occurredOn.substring(11, 16)) }.getOrNull() else null

    return FormFields(
        mode = when {
            isTransfer -> FormMode.TRANSFER
            transaction.amount >= 0 -> FormMode.INCOME
            else -> FormMode.EXPENSE
        },
        accountId = transaction.accountId,
        toAccountId = if (isTransfer) transferToAccountId ?: "" else "",
        categoryId = transaction.categoryId ?: "",
        amount = toDecimalString(kotlin.math.abs(transaction.amount)),
        date = date,
        time = time,
        payee = transaction.payee,
        notes = transaction.notes,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionForm(
    editing: Transaction?,
    transferToAccountId: String?,
    accounts: List<Account>,
    categories: List<Category>,
    payees: List<Payee>,
    defaultAccountId: String?,
    submitting: Boolean,
    error: String?,
    onSubmit: (TransactionSubmission) -> Unit,
    onCancel: () -> Unit,
) {
    var fields by remember(editing) { mutableStateOf(seedFrom(editing, transferToAccountId, defaultAccountId, accounts)) }
    var localError by remember(editing) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val isEditing = editing != null

    val categoryGroups = remember(fields.mode, categories) {
        groupForPicker(
            when (fields.mode) {
                FormMode.EXPENSE -> expenseCategories(categories)
                FormMode.INCOME -> incomeCategories(categories)
                FormMode.TRANSFER -> transferCategories(categories)
            },
        )
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!isEditing) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(fields.mode == FormMode.EXPENSE, onClick = { fields = fields.copy(mode = FormMode.EXPENSE, categoryId = "") }, label = { Text("Expense") })
                FilterChip(fields.mode == FormMode.INCOME, onClick = { fields = fields.copy(mode = FormMode.INCOME, categoryId = "") }, label = { Text("Income") })
                FilterChip(fields.mode == FormMode.TRANSFER, onClick = { fields = fields.copy(mode = FormMode.TRANSFER, categoryId = "") }, label = { Text("Transfer") })
            }
        }

        PayeeField(
            value = fields.payee,
            onValueChange = { fields = fields.copy(payee = it) },
            label = if (fields.mode == FormMode.TRANSFER) "Name" else "Payee",
            placeholder = if (fields.mode == FormMode.TRANSFER) "Leave blank to name it From → To" else "Who was paid",
            payees = payees,
            onSelect = { entry ->
                fields = fields.copy(
                    payee = entry.payee,
                    accountId = entry.accountId ?: fields.accountId,
                    toAccountId = if (fields.mode == FormMode.TRANSFER) entry.toAccountId ?: fields.toAccountId else fields.toAccountId,
                    categoryId = entry.categoryId ?: fields.categoryId,
                    notes = fields.notes.ifBlank { entry.notes },
                )
            },
        )

        OutlinedTextField(
            value = fields.amount,
            onValueChange = { fields = fields.copy(amount = it) },
            label = { Text("Amount") },
            placeholder = { Text("0.00") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        var accountMenuOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = accountMenuOpen, onExpandedChange = { accountMenuOpen = it }) {
            OutlinedTextField(
                value = accounts.firstOrNull { it.id == fields.accountId }?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(if (fields.mode == FormMode.TRANSFER) "From account" else "Account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                accounts.forEach { account ->
                    DropdownMenuItem(text = { Text(account.name) }, onClick = { fields = fields.copy(accountId = account.id); accountMenuOpen = false })
                }
            }
        }

        if (fields.mode == FormMode.TRANSFER) {
            var toMenuOpen by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = toMenuOpen, onExpandedChange = { toMenuOpen = it }) {
                OutlinedTextField(
                    value = accounts.firstOrNull { it.id == fields.toAccountId }?.name ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("To account") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = toMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = toMenuOpen, onDismissRequest = { toMenuOpen = false }) {
                    accounts.forEach { account ->
                        DropdownMenuItem(text = { Text(account.name) }, onClick = { fields = fields.copy(toAccountId = account.id); toMenuOpen = false })
                    }
                }
            }
        }

        var categoryMenuOpen by remember { mutableStateOf(false) }
        val selectedCategoryLabel = categoryGroups.flatMap { listOf(it.parent) + it.children }.firstOrNull { it.id == fields.categoryId }?.name ?: "Uncategorized"
        ExposedDropdownMenuBox(expanded = categoryMenuOpen, onExpandedChange = { categoryMenuOpen = it }) {
            OutlinedTextField(
                value = selectedCategoryLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(if (fields.mode == FormMode.TRANSFER) "Cashflow category" else "Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Uncategorized") }, onClick = { fields = fields.copy(categoryId = ""); categoryMenuOpen = false })
                categoryGroups.forEach { group ->
                    DropdownMenuItem(text = { Text(group.parent.name) }, onClick = { fields = fields.copy(categoryId = group.parent.id); categoryMenuOpen = false })
                    group.children.forEach { child ->
                        DropdownMenuItem(
                            text = { Text("    ${child.name}") },
                            onClick = { fields = fields.copy(categoryId = child.id); categoryMenuOpen = false },
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = fields.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                onValueChange = {},
                enabled = false,
                label = { Text("Date") },
                colors = readOnlyFieldColors(),
                modifier = Modifier.weight(1f).clickableField { showDatePicker(context, fields.date) { fields = fields.copy(date = it) } },
            )
            OutlinedTextField(
                value = fields.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                onValueChange = {},
                enabled = false,
                label = { Text("Time") },
                placeholder = { Text("Optional") },
                colors = readOnlyFieldColors(),
                modifier = Modifier.weight(1f).clickableField { showTimePicker(context, fields.time ?: LocalTime.now()) { fields = fields.copy(time = it) } },
            )
        }

        OutlinedTextField(
            value = fields.notes,
            onValueChange = { fields = fields.copy(notes = it) },
            label = { Text("Notes") },
            placeholder = { Text("Optional") },
            modifier = Modifier.fillMaxWidth(),
        )

        val shownError = localError ?: error
        if (shownError != null) {
            Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                enabled = !submitting,
                onClick = {
                    val minor = parseMoney(fields.amount)
                    if (minor == null || minor <= 0) {
                        localError = "Enter an amount greater than zero."
                        return@Button
                    }
                    if (fields.mode == FormMode.TRANSFER && fields.accountId == fields.toAccountId) {
                        localError = "Choose two different accounts."
                        return@Button
                    }
                    if (fields.accountId.isBlank() || (fields.mode == FormMode.TRANSFER && fields.toAccountId.isBlank())) {
                        localError = "Choose an account."
                        return@Button
                    }
                    localError = null

                    val datePart = fields.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val occurredOn = fields.time?.let { "$datePart" + "T" + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: datePart

                    val submission = when (fields.mode) {
                        FormMode.TRANSFER -> TransactionSubmission.Transfer(
                            fromAccountId = fields.accountId,
                            toAccountId = fields.toAccountId,
                            categoryId = fields.categoryId.ifBlank { null },
                            amount = minor,
                            occurredOn = occurredOn,
                            payee = fields.payee,
                            notes = fields.notes,
                        )
                        else -> TransactionSubmission.Plain(
                            accountId = fields.accountId,
                            categoryId = fields.categoryId.ifBlank { null },
                            amount = if (fields.mode == FormMode.EXPENSE) -minor else minor,
                            occurredOn = occurredOn,
                            payee = fields.payee,
                            notes = fields.notes,
                        )
                    }
                    onSubmit(submission)
                },
            ) {
                Text(if (isEditing) "Save changes" else "Add transaction")
            }
        }
    }
}

/** A read-only field that reacts to taps rather than the keyboard — used for date/time entry. */
private fun Modifier.clickableField(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

/**
 * OutlinedTextField must be disabled (not just readOnly) for the outer tap handler to receive
 * clicks — otherwise the field's own focus/cursor gesture detector consumes the touch first.
 * This restyles the disabled state to look identical to an enabled field.
 */
@Composable
private fun readOnlyFieldColors() = OutlinedTextFieldDefaults.colors(
    disabledTextColor = MaterialTheme.colorScheme.onSurface,
    disabledBorderColor = MaterialTheme.colorScheme.outline,
    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
