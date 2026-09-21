package dev.gavenda.yuuka.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.ConnectedButtonGroup
import dev.gavenda.yuuka.ui.common.DropdownField
import dev.gavenda.yuuka.ui.common.FieldError
import dev.gavenda.yuuka.ui.common.MutationLoadingIndicator
import dev.gavenda.yuuka.ui.common.PayeeField
import dev.gavenda.yuuka.ui.common.PickerField
import dev.gavenda.yuuka.ui.common.SelectOption
import dev.gavenda.yuuka.ui.common.YuukaTextField
import dev.gavenda.yuuka.ui.common.categoryOptions
import dev.gavenda.yuuka.ui.common.rememberFormValidation
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
    val tagIds: List<String> = emptyList(),
)

private fun seedFrom(transaction: Transaction?, transferToAccountId: String?, defaultAccountId: String?, accounts: List<Account>): FormFields {
    if (transaction == null) {
        val preferred = accounts.firstOrNull { it.id == defaultAccountId } ?: accounts.firstOrNull()
        return FormFields(accountId = preferred?.id ?: "", date = LocalDate.parse(today()), time = LocalTime.now())
    }

    val isTransfer = transaction.transferId != null
    val date = LocalDate.parse(transaction.occurredOn.take(10))
    // An automated transaction has no time of day, whatever a stray string might say: a subscription
    // posts it at 00:00 UTC and the API refuses to give it one.
    val time = if (transaction.occurredOn.length > 10 && !transaction.automated) runCatching { LocalTime.parse(transaction.occurredOn.substring(11, 16)) }.getOrNull() else null

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
        tagIds = transaction.tags.map { it.id },
    )
}

@Composable
private fun tagColor(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TransactionForm(
    editing: Transaction?,
    transferToAccountId: String?,
    accounts: List<Account>,
    categories: List<Category>,
    tags: List<Tag>,
    payees: List<Payee>,
    defaultAccountId: String?,
    submitting: Boolean,
    error: String?,
    onSubmit: (TransactionSubmission) -> Unit,
    onCancel: () -> Unit,
) {
    var fields by remember(editing) { mutableStateOf(seedFrom(editing, transferToAccountId, defaultAccountId, accounts)) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val isEditing = editing != null
    // Posted by a subscription at 00:00 UTC: the date can move, the time of day is not anyone's to set.
    val isAutomated = editing?.automated == true

    val categoryGroups = remember(fields.mode, categories) {
        groupForPicker(
            when (fields.mode) {
                FormMode.EXPENSE -> expenseCategories(categories)
                FormMode.INCOME -> incomeCategories(categories)
                FormMode.TRANSFER -> transferCategories(categories)
            },
        )
    }

    // Every field's problem is worked out from what it holds now, and shown once its field has been left or a save tried.
    val form = rememberFormValidation()
    val amountMinor = parseMoney(fields.amount)
    val payeeField = form.field(
        "payee",
        if (fields.payee.trim().length > PAYEE_MAX) stringResource(R.string.error_too_long, PAYEE_MAX) else null,
    )
    val amountField = form.field(
        "amount",
        when {
            fields.amount.isBlank() -> stringResource(R.string.error_amount_required)
            amountMinor == null -> stringResource(R.string.error_amount_number)
            amountMinor <= 0 -> stringResource(R.string.error_amount_greater_than_zero)
            else -> null
        },
    )
    val accountField = form.field("account", if (accounts.none { it.id == fields.accountId }) stringResource(R.string.error_choose_account) else null)
    val toAccountField = form.field(
        "to-account",
        when {
            fields.mode != FormMode.TRANSFER -> null
            accounts.none { it.id == fields.toAccountId } -> stringResource(R.string.error_choose_to_account)
            fields.toAccountId == fields.accountId -> stringResource(R.string.error_choose_different_accounts)
            else -> null
        },
    )
    val notesField = form.field(
        "notes",
        if (fields.notes.trim().length > NOTES_MAX) stringResource(R.string.error_too_long, NOTES_MAX) else null,
    )
    val tagsField = form.field(
        "tags",
        if (fields.tagIds.size > MAX_TAGS_PER_TRANSACTION) stringResource(R.string.error_too_many_tags, MAX_TAGS_PER_TRANSACTION) else null,
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!isEditing) {
            ConnectedButtonGroup(
                options = FormMode.entries,
                selected = fields.mode,
                onSelect = { fields = fields.copy(mode = it, categoryId = "") },
                label = { stringResource(it.labelRes) },
            )
        }

        PayeeField(
            value = fields.payee,
            onValueChange = { fields = fields.copy(payee = it) },
            label = if (fields.mode == FormMode.TRANSFER) stringResource(R.string.label_name) else stringResource(R.string.label_payee),
            placeholder = if (fields.mode == FormMode.TRANSFER) stringResource(R.string.placeholder_transfer_name) else stringResource(R.string.placeholder_who_was_paid),
            payees = payees,
            field = payeeField,
            onSelect = { entry ->
                // The shape it was last filed under comes back too, unless an existing row is being edited (its mode is fixed).
                // A category from the other mode's set is dropped rather than carried over, as switching the mode by hand does.
                val mode = if (isEditing) fields.mode else FormMode.valueOf(entry.kind.name.uppercase())
                fields = fields.copy(
                    mode = mode,
                    payee = entry.payee,
                    accountId = entry.accountId ?: fields.accountId,
                    toAccountId = if (mode == FormMode.TRANSFER) entry.toAccountId ?: fields.toAccountId else fields.toAccountId,
                    categoryId = entry.categoryId ?: if (mode == fields.mode) fields.categoryId else "",
                    notes = fields.notes.ifBlank { entry.notes },
                )
            },
        )

        YuukaTextField(
            value = fields.amount,
            onValueChange = { fields = fields.copy(amount = it) },
            label = stringResource(R.string.label_amount),
            placeholder = stringResource(R.string.placeholder_amount_decimal),
            singleLine = true,
            field = amountField,
        )

        val accountOptions = accounts.map { SelectOption(it.id, it.name) }
        DropdownField(
            label = if (fields.mode == FormMode.TRANSFER) stringResource(R.string.label_from_account) else stringResource(R.string.label_account),
            value = accounts.firstOrNull { it.id == fields.accountId }?.name ?: "",
            options = accountOptions,
            onSelect = { fields = fields.copy(accountId = it) },
            field = accountField,
        )

        if (fields.mode == FormMode.TRANSFER) {
            DropdownField(
                label = stringResource(R.string.label_to_account),
                value = accounts.firstOrNull { it.id == fields.toAccountId }?.name ?: "",
                options = accountOptions,
                onSelect = { fields = fields.copy(toAccountId = it) },
                field = toAccountField,
            )
        }

        val uncategorizedLabel = stringResource(R.string.category_uncategorized)
        val selectedCategoryLabel = categoryGroups.flatMap { listOf(it.parent) + it.children }.firstOrNull { it.id == fields.categoryId }?.name ?: uncategorizedLabel
        DropdownField(
            label = if (fields.mode == FormMode.TRANSFER) stringResource(R.string.label_cashflow_category) else stringResource(R.string.label_category),
            value = selectedCategoryLabel,
            options = categoryOptions(categoryGroups, uncategorizedLabel).map { SelectOption(it.value ?: "", it.label, it.indent) },
            onSelect = { fields = fields.copy(categoryId = it) },
        )

        val optionalPlaceholder = stringResource(R.string.placeholder_optional)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerField(
                value = fields.date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                label = stringResource(R.string.label_date),
                onClick = { pickingDate = true },
                modifier = Modifier.weight(1f),
            )
            PickerField(
                value = if (isAutomated) stringResource(R.string.automated_time_value) else fields.time?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                label = stringResource(R.string.label_time),
                placeholder = optionalPlaceholder,
                onClick = { pickingTime = true },
                enabled = !isAutomated,
                modifier = Modifier.weight(1f),
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

        if (isAutomated) {
            Text(
                stringResource(R.string.automated_transaction_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (pickingTime && !isAutomated) {
            YuukaTimePickerDialog(
                initial = fields.time ?: LocalTime.now(),
                onDismiss = { pickingTime = false },
                onPicked = {
                    fields = fields.copy(time = it)
                    pickingTime = false
                },
            )
        }

        YuukaTextField(
            value = fields.notes,
            onValueChange = { fields = fields.copy(notes = it) },
            label = stringResource(R.string.label_notes),
            placeholder = optionalPlaceholder,
            field = notesField,
        )

        // Tags, unlike the category, are any number of labels. They change no figure.
        if (tags.isNotEmpty()) {
            Text(stringResource(R.string.label_tags), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tags.forEach { tag ->
                    val selected = tag.id in fields.tagIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            tagsField.touch()
                            fields = fields.copy(tagIds = if (selected) fields.tagIds - tag.id else fields.tagIds + tag.id)
                        },
                        label = { Text(tag.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(tagColor(tag.color))) },
                    )
                }
            }
            FieldError(tagsField.error)
        }

        // What is wrong with a field is said beside the field. This is for a failure that is no one field's — the save itself.
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(
                modifier = Modifier.weight(1f),
                enabled = !submitting && form.valid(payeeField, amountField, accountField, toAccountField, notesField, tagsField),
                onClick = {
                    val minor = amountMinor ?: return@Button

                    val datePart = fields.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
                    val occurredOn = fields.time?.takeUnless { isAutomated }?.let { "$datePart" + "T" + it.format(DateTimeFormatter.ofPattern("HH:mm")) } ?: datePart

                    // A tag deleted since the form opened would be refused by the API; drop it here instead.
                    val tagIds = fields.tagIds.filter { id -> tags.any { it.id == id } }

                    val submission = when (fields.mode) {
                        FormMode.TRANSFER -> TransactionSubmission.Transfer(
                            fromAccountId = fields.accountId,
                            toAccountId = fields.toAccountId,
                            categoryId = fields.categoryId.ifBlank { null },
                            amount = minor,
                            occurredOn = occurredOn,
                            payee = fields.payee,
                            notes = fields.notes,
                            tagIds = tagIds,
                        )
                        else -> TransactionSubmission.Plain(
                            accountId = fields.accountId,
                            categoryId = fields.categoryId.ifBlank { null },
                            amount = if (fields.mode == FormMode.EXPENSE) -minor else minor,
                            occurredOn = occurredOn,
                            payee = fields.payee,
                            notes = fields.notes,
                            tagIds = tagIds,
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
