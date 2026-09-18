package dev.gavenda.yuuka.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.MutationLoadingIndicator
import dev.gavenda.yuuka.ui.common.PayeeField
import dev.gavenda.yuuka.ui.common.YuukaDatePickerDialog
import dev.gavenda.yuuka.ui.common.YuukaTimePickerDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class FormMode(@androidx.annotation.StringRes val labelRes: Int) {
    EXPENSE(R.string.category_kind_expense),
    INCOME(R.string.category_kind_income),
    TRANSFER(R.string.category_kind_transfer),
}

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
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
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
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FormMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = fields.mode == mode,
                        onClick = { fields = fields.copy(mode = mode, categoryId = "") },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = FormMode.entries.size),
                        label = { Text(stringResource(mode.labelRes)) },
                    )
                }
            }
        }

        PayeeField(
            value = fields.payee,
            onValueChange = { fields = fields.copy(payee = it) },
            label = if (fields.mode == FormMode.TRANSFER) stringResource(R.string.label_name) else stringResource(R.string.label_payee),
            placeholder = if (fields.mode == FormMode.TRANSFER) stringResource(R.string.placeholder_transfer_name) else stringResource(R.string.placeholder_who_was_paid),
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
            label = { Text(stringResource(R.string.label_amount)) },
            placeholder = { Text(stringResource(R.string.placeholder_amount_decimal)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        var accountMenuOpen by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = accountMenuOpen, onExpandedChange = { accountMenuOpen = it }) {
            OutlinedTextField(
                value = accounts.firstOrNull { it.id == fields.accountId }?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(if (fields.mode == FormMode.TRANSFER) stringResource(R.string.label_from_account) else stringResource(R.string.label_account)) },
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
                    label = { Text(stringResource(R.string.label_to_account)) },
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
        val uncategorizedLabel = stringResource(R.string.category_uncategorized)
        val selectedCategoryLabel = categoryGroups.flatMap { listOf(it.parent) + it.children }.firstOrNull { it.id == fields.categoryId }?.name ?: uncategorizedLabel
        ExposedDropdownMenuBox(expanded = categoryMenuOpen, onExpandedChange = { categoryMenuOpen = it }) {
            OutlinedTextField(
                value = selectedCategoryLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(if (fields.mode == FormMode.TRANSFER) stringResource(R.string.label_cashflow_category) else stringResource(R.string.label_category)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                DropdownMenuItem(text = { Text(uncategorizedLabel) }, onClick = { fields = fields.copy(categoryId = ""); categoryMenuOpen = false })
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

        val optionalPlaceholder = stringResource(R.string.placeholder_optional)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = fields.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                onValueChange = {},
                enabled = false,
                label = { Text(stringResource(R.string.label_date)) },
                colors = readOnlyFieldColors(),
                modifier = Modifier.weight(1f).clickableField { pickingDate = true },
            )
            OutlinedTextField(
                value = fields.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                onValueChange = {},
                enabled = false,
                label = { Text(stringResource(R.string.label_time)) },
                placeholder = { Text(optionalPlaceholder) },
                colors = readOnlyFieldColors(),
                modifier = Modifier.weight(1f).clickableField { pickingTime = true },
            )
        }

        if (pickingDate) {
            YuukaDatePickerDialog(
                initial = fields.date,
                onDismiss = { pickingDate = false },
                onPicked = {
                    fields = fields.copy(date = it)
                    pickingDate = false
                },
            )
        }

        if (pickingTime) {
            YuukaTimePickerDialog(
                initial = fields.time ?: LocalTime.now(),
                onDismiss = { pickingTime = false },
                onPicked = {
                    fields = fields.copy(time = it)
                    pickingTime = false
                },
            )
        }

        OutlinedTextField(
            value = fields.notes,
            onValueChange = { fields = fields.copy(notes = it) },
            label = { Text(stringResource(R.string.label_notes)) },
            placeholder = { Text(optionalPlaceholder) },
            modifier = Modifier.fillMaxWidth(),
        )

        val shownError = localError ?: error
        if (shownError != null) {
            Text(shownError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        val amountGreaterThanZeroError = stringResource(R.string.error_amount_greater_than_zero)
        val chooseDifferentAccountsError = stringResource(R.string.error_choose_different_accounts)
        val chooseAccountError = stringResource(R.string.error_choose_account)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(
                modifier = Modifier.weight(1f),
                enabled = !submitting,
                onClick = {
                    val minor = parseMoney(fields.amount)
                    if (minor == null || minor <= 0) {
                        localError = amountGreaterThanZeroError
                        return@Button
                    }
                    if (fields.mode == FormMode.TRANSFER && fields.accountId == fields.toAccountId) {
                        localError = chooseDifferentAccountsError
                        return@Button
                    }
                    if (fields.accountId.isBlank() || (fields.mode == FormMode.TRANSFER && fields.toAccountId.isBlank())) {
                        localError = chooseAccountError
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
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(if (isEditing) stringResource(R.string.save_changes) else stringResource(R.string.add_transaction))
                }
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
