package dev.gavenda.yuuka.ui.transactions

import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import dev.gavenda.yuuka.ui.common.YuukaSays
import dev.gavenda.yuuka.ui.common.YuukaMood
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.data.model.Transaction
import dev.gavenda.yuuka.data.remote.dto.ExchangeRate
import dev.gavenda.yuuka.repository.TransferFee
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.ConnectedButtonGroup
import dev.gavenda.yuuka.ui.common.DropdownField
import dev.gavenda.yuuka.ui.common.FieldError
import dev.gavenda.yuuka.ui.common.FullScreenDialog
import dev.gavenda.yuuka.ui.common.harmonisedColor
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
    /** Between two currencies: one of the source's in the destination's, and what arrived. */
    val rate: String = "",
    val toAmount: String = "",
    /** What the transfer cost, an expense on the source account; blank is none. */
    val fee: String = "",
    val feeCategoryId: String = "",
)

/** What is said under the rate: that it is being looked up, which day's it is, or that there is none. */
private sealed interface RateNote {
    data object Loading : RateNote
    data class Found(val date: String) : RateNote
    data object Unavailable : RateNote
}

private fun seedFrom(transaction: Transaction?, transferInflow: Transaction?, defaultAccountId: String?, accounts: List<Account>): FormFields {
    if (transaction == null) {
        val preferred = accounts.firstOrNull { it.id == defaultAccountId } ?: accounts.firstOrNull()
        return FormFields(accountId = preferred?.id ?: "", date = LocalDate.parse(today()), time = LocalTime.now())
    }

    val isTransfer = transaction.transferId != null
    val date = LocalDate.parse(transaction.occurredOn.take(10))
    // An automated transaction has no time of day, whatever a stray string might say: a subscription
    // posts it at 00:00 UTC and the API refuses to give it one.
    val time = if (transaction.occurredOn.length > 10 && !transaction.automated) runCatching { LocalTime.parse(transaction.occurredOn.substring(11, 16)) }.getOrNull() else null

    val inflow = transferInflow.takeIf { isTransfer }
    // What the transfer moved at is the person's own, so it is shown as it was rather than looked up again.
    val rate = inflow?.let { rateBetween(kotlin.math.abs(transaction.amount), it.amount) }

    return FormFields(
        mode = when {
            isTransfer -> FormMode.TRANSFER
            transaction.amount >= 0 -> FormMode.INCOME
            else -> FormMode.EXPENSE
        },
        accountId = transaction.accountId,
        toAccountId = inflow?.accountId ?: "",
        rate = rate?.let(::formatRate) ?: "",
        toAmount = inflow?.let { toDecimalString(it.amount) } ?: "",
        categoryId = transaction.categoryId ?: "",
        amount = toDecimalString(kotlin.math.abs(transaction.amount)),
        date = date,
        time = time,
        payee = transaction.payee,
        notes = transaction.notes,
        tagIds = transaction.tags.map { it.id },
    )
}

/** The name of the category [id] among [groups], parents and children alike, or [fallback] when it is none of them. */
private fun labelOf(groups: List<CategoryGroup>, id: String, fallback: String): String =
    groups.flatMap { listOf(it.parent) + it.children }.firstOrNull { it.id == id }?.name ?: fallback

@Composable
private fun tagColor(hex: String): Color =
    harmonisedColor(hex, MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun TransactionForm(
    editing: Transaction?,
    transferInflow: Transaction?,
    accounts: List<Account>,
    categories: List<Category>,
    tags: List<Tag>,
    payees: List<Payee>,
    defaultAccountId: String?,
    submitting: Boolean,
    error: String?,
    onSubmit: (TransactionSubmission) -> Unit,
    onCancel: () -> Unit,
    /** The fee the transfer being edited already cost, read from the device. */
    loadTransferFee: suspend (transferId: String) -> Transaction? = { null },
    /** The rate for a pair of currencies on a day, or null when none can be had. */
    lookUpRate: suspend (from: String, to: String, date: String) -> ExchangeRate? = { _, _, _ -> null },
) {
    var initial by remember(editing) { mutableStateOf(seedFrom(editing, transferInflow, defaultAccountId, accounts)) }
    var fields by remember(editing) { mutableStateOf(initial) }

    // The fee is its own row; until it has been read, an edit could not tell "no fee" from "not read yet", so the
    // form waits for it rather than saving a transfer without the fee it has.
    val editingTransferId = editing?.transferId
    var feeLoaded by remember(editing) { mutableStateOf(editingTransferId == null) }
    /** The fee the transfer being edited already has, so an edit changes or takes off that one. */
    var feeId by remember(editing) { mutableStateOf<String?>(null) }
    LaunchedEffect(editingTransferId) {
        if (editingTransferId == null) return@LaunchedEffect
        val fee = loadTransferFee(editingTransferId)
        if (fee != null) {
            feeId = fee.id
            val withFee = { f: FormFields -> f.copy(fee = toDecimalString(kotlin.math.abs(fee.amount)), feeCategoryId = fee.categoryId ?: "") }
            initial = withFee(initial)
            fields = withFee(fields)
        }
        feeLoaded = true
    }

    val fromCurrency = accounts.firstOrNull { it.id == fields.accountId }?.currency?.uppercase()
    val toCurrency = accounts.firstOrNull { it.id == fields.toAccountId }?.currency?.uppercase()
    /** A transfer between accounts of two currencies, which asks for a rate and what arrived. */
    val crossCurrency = fields.mode == FormMode.TRANSFER && fromCurrency != null && toCurrency != null && fromCurrency != toCurrency
    val currencyPair = if (fromCurrency != null && toCurrency != null) "$fromCurrency>$toCurrency" else ""

    // A rate typed, or the one an edited transfer moved at, is the person's own and never overwritten — until either
    // account's currency changes, which makes it a rate for another pair.
    var rateOwn by remember(editing) { mutableStateOf(initial.rate.isNotEmpty()) }
    var ratePair by remember(editing) { mutableStateOf(currencyPair) }
    var rateNote by remember(editing) { mutableStateOf<RateNote?>(null) }

    /**
     * [f] with what left worked out from what arrived at the rate — the figure Wise calls "we converted" (`deriveSent`
     * on the web). It is never typed between two currencies, so while either is not a number yet it is blank.
     */
    fun withSent(f: FormFields): FormFields {
        val received = parseMoney(f.toAmount)?.takeIf { it > 0 }
        val rate = parseRate(f.rate)
        return f.copy(amount = if (received != null && rate != null) toDecimalString(convertAtRate(received, rate)) else "")
    }

    /** Typing a rate makes it the person's own, and what left follows it (`onRateTyped` on the web). */
    fun onRateTyped(typed: String) {
        rateOwn = true
        rateNote = null
        fields = withSent(fields.copy(rate = typed))
    }

    val datePartForRate = fields.date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    LaunchedEffect(currencyPair, datePartForRate, crossCurrency) {
        if (!crossCurrency) {
            rateNote = null
            return@LaunchedEffect
        }
        // A rate — the person's own or one looked up — was for the two currencies it was given for. Another pair
        // clears it and what arrived, so neither can be saved for the wrong currencies while a new one is asked for.
        if (currencyPair != ratePair) {
            ratePair = currencyPair
            rateOwn = false
            fields = fields.copy(rate = "", toAmount = "", amount = "")
        }
        if (rateOwn) return@LaunchedEffect
        rateNote = RateNote.Loading
        // Quoted as one of the destination's in the source's, so it is asked for that way round.
        val answer = lookUpRate(toCurrency!!, fromCurrency!!, datePartForRate)
        if (rateOwn) return@LaunchedEffect
        if (answer == null) {
            rateNote = RateNote.Unavailable
        } else {
            rateNote = RateNote.Found(answer.date)
            fields = withSent(fields.copy(rate = formatRate(answer.rate)))
        }
    }
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
            // Between two currencies the amount is worked out, so a blank one is Received's or Rate's to report.
            fields.amount.isBlank() -> if (crossCurrency) null else stringResource(R.string.error_amount_required)
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
    val rateField = form.field(
        "rate",
        when {
            !crossCurrency -> null
            fields.rate.isBlank() -> stringResource(R.string.error_rate_required)
            parseRate(fields.rate) == null -> stringResource(R.string.error_rate_number)
            else -> null
        },
    )
    val toAmountMinor = parseMoney(fields.toAmount)
    val receivedField = form.field(
        "received",
        when {
            !crossCurrency -> null
            fields.toAmount.isBlank() -> stringResource(R.string.error_received_required)
            toAmountMinor == null -> stringResource(R.string.error_amount_number)
            toAmountMinor <= 0 -> stringResource(R.string.error_amount_greater_than_zero)
            else -> null
        },
    )
    val feeMinor = parseMoney(fields.fee)
    val feeField = form.field(
        "fee",
        when {
            fields.mode != FormMode.TRANSFER || fields.fee.isBlank() -> null
            feeMinor == null -> stringResource(R.string.error_fee_number)
            feeMinor <= 0 -> stringResource(R.string.error_fee_greater_than_zero)
            else -> null
        },
    )

    val submit = submit@{
        val minor = amountMinor ?: return@submit

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
                // Each leg is in its own account's currency; between two of one currency what arrived is what left.
                toAmount = if (crossCurrency) toAmountMinor ?: minor else minor,
                occurredOn = occurredOn,
                payee = fields.payee,
                notes = fields.notes,
                tagIds = tagIds,
                // A fee left blank is none, which on an edit takes off the one it had.
                fee = feeMinor?.takeIf { fields.fee.isNotBlank() && it > 0 }?.let { TransferFee(it, fields.feeCategoryId.ifBlank { null }) },
                feeId = feeId,
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
    }

    FullScreenDialog(
        title = stringResource(if (isEditing) R.string.edit_transaction else R.string.new_transaction),
        onDismiss = onCancel,
        onSave = submit,
        saveEnabled = feeLoaded && form.valid(payeeField, amountField, accountField, toAccountField, notesField, tagsField, rateField, receivedField, feeField),
        submitting = submitting,
        dirty = fields != initial,
    ) {
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

        // Between two currencies what left is worked out from Received and Rate below, so it is read, not typed.
        YuukaTextField(
            value = fields.amount,
            onValueChange = { fields = fields.copy(amount = it) },
            label = stringResource(R.string.label_amount),
            placeholder = stringResource(R.string.placeholder_amount_decimal),
            singleLine = true,
            readOnly = crossCurrency,
            hint = if (crossCurrency) stringResource(R.string.amount_converted) else null,
            field = amountField,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            // The currency of the account it lands on; empty rather than null keeps the prefix slot steady.
            prefix = accounts.firstOrNull { it.id == fields.accountId }?.let { currencySymbol(it.currency) } ?: "",
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

        // Between two currencies, what arrived and the rate are typed, as Wise lists them, and the amount above follows.
        if (crossCurrency) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                YuukaTextField(
                    value = fields.rate,
                    onValueChange = ::onRateTyped,
                    label = stringResource(R.string.label_rate),
                    singleLine = true,
                    field = rateField,
                    hint = when (val note = rateNote) {
                        RateNote.Loading -> stringResource(R.string.rate_loading)
                        is RateNote.Found -> stringResource(R.string.rate_found, formatLongDate(note.date))
                        RateNote.Unavailable -> stringResource(R.string.rate_unavailable)
                        null -> stringResource(R.string.rate_hint, toCurrency!!, fromCurrency!!)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                YuukaTextField(
                    value = fields.toAmount,
                    onValueChange = { fields = withSent(fields.copy(toAmount = it)) },
                    label = stringResource(R.string.label_received),
                    placeholder = stringResource(R.string.placeholder_amount_decimal),
                    singleLine = true,
                    field = receivedField,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = currencySymbol(toCurrency!!),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // A fee is spending, not a movement: it posts its own expense on the account the money left.
        if (fields.mode == FormMode.TRANSFER) {
            YuukaTextField(
                value = fields.fee,
                onValueChange = { fields = fields.copy(fee = it) },
                label = stringResource(R.string.label_fee),
                placeholder = stringResource(R.string.placeholder_optional),
                singleLine = true,
                field = feeField,
                hint = stringResource(R.string.fee_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                prefix = fromCurrency?.let { currencySymbol(it) } ?: "",
            )
            if (fields.fee.isNotBlank()) {
                val feeGroups = remember(categories) { groupForPicker(expenseCategories(categories)) }
                val noCategory = stringResource(R.string.category_uncategorized)
                DropdownField(
                    label = stringResource(R.string.label_fee_category),
                    value = labelOf(feeGroups, fields.feeCategoryId, noCategory),
                    options = categoryOptions(feeGroups, noCategory).map { SelectOption(it.value ?: "", it.label, it.indent) },
                    onSelect = { fields = fields.copy(feeCategoryId = it) },
                )
            }
        }

        val uncategorizedLabel = stringResource(R.string.category_uncategorized)
        val selectedCategoryLabel = labelOf(categoryGroups, fields.categoryId, uncategorizedLabel)
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
                supportingText = if (isAutomated) stringResource(R.string.automated_transaction_hint) else ""
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

        // Only a new transaction: Yuuka at the foot of the form, ready to count it.
        if (!isEditing) {
            YuukaSays(YuukaMood.Attentive, stringResource(R.string.transaction_form_line), Modifier.padding(top = 8.dp))
        }
    }
}
