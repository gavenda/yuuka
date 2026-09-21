package dev.gavenda.yuuka.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.Payee
import dev.gavenda.yuuka.data.model.PayeeKind
import dev.gavenda.yuuka.data.model.Subscription
import dev.gavenda.yuuka.domain.*
import dev.gavenda.yuuka.ui.common.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** What the form hands back. [startOn] is null when an edit left the date alone, so the schedule is not restarted. */
private data class SubscriptionSubmission(
    val accountId: String,
    val categoryId: String?,
    val amount: Long,
    val payee: String,
    val notes: String,
    val startOn: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(modifier: Modifier = Modifier, viewModel: SubscriptionsViewModel = org.koin.compose.viewmodel.koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    var creating by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Subscription?>(null) }
    var pendingDelete by remember { mutableStateOf<Subscription?>(null) }

    val activeAccounts = remember(state.accounts) { state.accounts.filter { !it.archived } }
    val accountsById = remember(state.accounts) { state.accounts.associateBy { it.id } }

    val addedMessage = stringResource(R.string.subscription_added)
    val updatedMessage = stringResource(R.string.subscription_updated)
    val deletedMessage = stringResource(R.string.subscription_deleted)
    val pausedMessage = stringResource(R.string.subscription_paused)
    val resumedMessage = stringResource(R.string.subscription_resumed)

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            // Nowhere to post to until there is an account.
            if (activeAccounts.isNotEmpty()) {
                ScreenFab(
                    label = stringResource(R.string.new_subscription),
                    icon = Icons.Filled.Add,
                    onClick = { creating = true },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.subscriptions_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            if (state.subscriptions.isNotEmpty()) {
                item {
                    val pausedCount = state.subscriptions.count { !it.enabled }
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.subscriptions_total_per_month), style = MaterialTheme.typography.bodyMedium)
                            MoneyText(
                                monthlyTotal(state.subscriptions),
                                currency = state.displayCurrency,
                                tone = MoneyTone.SIGNED,
                                explicit = true,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        if (pausedCount > 0) {
                            Text(
                                stringResource(R.string.subscriptions_total_paused, pausedCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.subscriptions.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(R.string.no_subscriptions_yet),
                        description = stringResource(R.string.subscriptions_empty_description),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            items(state.subscriptions, key = { it.id }) { subscription ->
                val toggleKey = "toggle:${subscription.id}"
                SubscriptionRow(
                    subscription = subscription,
                    currency = accountsById[subscription.accountId]?.currency ?: DEFAULT_CURRENCY,
                    toggling = busy.isBusy(toggleKey),
                    onEdit = { editing = subscription },
                    onToggle = {
                        busy.run(toggleKey, snackbarHostState, successMessage = if (subscription.enabled) pausedMessage else resumedMessage) {
                            viewModel.setEnabled(subscription.id, !subscription.enabled)
                        }
                    },
                    onDelete = { pendingDelete = subscription },
                )
            }
        }
    }

    if (creating || editing != null) {
        val formKey = "subscription-form"
        val submitting = busy.isBusy(formKey)
        val target = editing
        val close = { creating = false; editing = null }

        ModalBottomSheet(
            onDismissRequest = { if (!submitting) close() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            WithSnackbarOverlay {
                SubscriptionForm(
                    editing = target,
                    accounts = activeAccounts,
                    categories = state.categories,
                    payees = state.payees,
                    defaultAccountId = state.defaultAccountId,
                    submitting = submitting,
                    onCancel = { if (!submitting) close() },
                    onSubmit = { submission ->
                        busy.run(
                            formKey,
                            snackbarHostState,
                            successMessage = if (target != null) updatedMessage else addedMessage,
                            onSuccess = close,
                        ) {
                            if (target != null) {
                                viewModel.update(
                                    target.id,
                                    submission.accountId,
                                    submission.categoryId,
                                    submission.amount,
                                    submission.payee,
                                    submission.notes,
                                    submission.startOn,
                                )
                            } else {
                                viewModel.create(
                                    submission.accountId,
                                    submission.categoryId,
                                    submission.amount,
                                    submission.payee,
                                    submission.notes,
                                    submission.startOn!!,
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    pendingDelete?.let { subscription ->
        val deleteKey = "delete:${subscription.id}"
        val deleting = busy.isBusy(deleteKey)

        AlertDialog(
            onDismissRequest = { if (!deleting) pendingDelete = null },
            title = { Text(stringResource(R.string.delete_subscription_confirm_title)) },
            text = { WithSnackbarOverlay { Text(stringResource(R.string.delete_subscription_body, subscription.payee)) } },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        busy.run(deleteKey, snackbarHostState, successMessage = deletedMessage, onSuccess = { pendingDelete = null }) {
                            viewModel.delete(subscription.id)
                        }
                    },
                ) {
                    if (deleting) MutationLoadingIndicator() else Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }, enabled = !deleting) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun SubscriptionRow(
    subscription: Subscription,
    currency: String,
    toggling: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val subtitle = listOfNotNull(subscription.accountName, subscription.categoryName).joinToString(" · ")
    val schedule = stringResource(R.string.subscription_schedule, ordinal(subscription.dayOfMonth)) +
        if (subscription.enabled) " · " + stringResource(R.string.subscription_next, formatLongDate(subscription.nextRunOn)) else ""

    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        actions = {
            ActionIconButton(
                if (subscription.enabled) ActionIcon.PAUSE else ActionIcon.RESUME,
                stringResource(if (subscription.enabled) R.string.cd_pause_item else R.string.cd_resume_item, subscription.payee),
                onToggle,
                loading = toggling,
            )
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, subscription.payee), onDelete, danger = true)
        },
    ) {
        Card(colors = yuukaCardColors(), modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val dot = subscription.categoryColor?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
                        if (dot != null) Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                        Text(subscription.payee, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
                        if (!subscription.enabled) {
                            Text(
                                stringResource(R.string.subscription_paused_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(schedule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MoneyText(subscription.amount, tone = MoneyTone.SIGNED, explicit = true, currency = currency, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private enum class Direction(@androidx.annotation.StringRes val labelRes: Int) {
    EXPENSE(R.string.category_kind_expense),
    INCOME(R.string.category_kind_income),
}

@Composable
private fun SubscriptionForm(
    editing: Subscription?,
    accounts: List<Account>,
    categories: List<Category>,
    payees: List<Payee>,
    defaultAccountId: String?,
    submitting: Boolean,
    onSubmit: (SubscriptionSubmission) -> Unit,
    onCancel: () -> Unit,
) {
    // The API measures "not in the past" against the UTC calendar, so the picker does too.
    val today = remember { utcToday() }
    // A paused subscription's next run may already be behind us; leaving it alone is fine, choosing a new one is not.
    val initialStart = remember(editing) { editing?.let { LocalDate.parse(it.nextRunOn) } ?: today.plusDays(1) }

    var direction by remember(editing) { mutableStateOf(if (editing != null && editing.amount >= 0) Direction.INCOME else Direction.EXPENSE) }
    var payee by remember(editing) { mutableStateOf(editing?.payee ?: "") }
    var amount by remember(editing) { mutableStateOf(editing?.let { toDecimalString(kotlin.math.abs(it.amount)) } ?: "") }
    var accountId by remember(editing) {
        mutableStateOf(editing?.accountId ?: (accounts.firstOrNull { it.id == defaultAccountId } ?: accounts.firstOrNull())?.id ?: "")
    }
    var categoryId by remember(editing) { mutableStateOf(editing?.categoryId ?: "") }
    var startOn by remember(editing) { mutableStateOf(initialStart) }
    var notes by remember(editing) { mutableStateOf(editing?.notes ?: "") }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    val categoryGroups = remember(direction, categories) {
        groupForPicker(if (direction == Direction.INCOME) incomeCategories(categories) else expenseCategories(categories))
    }
    val selectable = remember(categoryGroups) { categoryGroups.flatMap { listOf(it.parent) + it.children } }

    // Every field's problem is worked out from what it holds now, and shown once its field has been left or a save tried.
    val form = rememberFormValidation()
    val amountMinor = parseMoney(amount)
    val payeeField = form.field(
        "payee",
        when {
            payee.isBlank() -> stringResource(R.string.error_payee_required)
            payee.trim().length > PAYEE_MAX -> stringResource(R.string.error_too_long, PAYEE_MAX)
            else -> null
        },
    )
    val amountField = form.field(
        "amount",
        when {
            amount.isBlank() -> stringResource(R.string.error_amount_required)
            amountMinor == null -> stringResource(R.string.error_amount_number)
            amountMinor <= 0 -> stringResource(R.string.error_amount_greater_than_zero)
            else -> null
        },
    )
    val accountField = form.field("account", if (accounts.none { it.id == accountId }) stringResource(R.string.error_choose_account) else null)
    val notesField = form.field("notes", if (notes.trim().length > NOTES_MAX) stringResource(R.string.error_too_long, NOTES_MAX) else null)

    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            stringResource(if (editing != null) R.string.edit_subscription else R.string.new_subscription),
            style = MaterialTheme.typography.titleMedium,
        )

        ConnectedButtonGroup(
            options = Direction.entries,
            selected = direction,
            onSelect = {
                direction = it
                categoryId = ""
            },
            label = { stringResource(it.labelRes) },
        )

        PayeeField(
            value = payee,
            onValueChange = { payee = it },
            label = stringResource(R.string.label_payee),
            placeholder = stringResource(R.string.placeholder_subscription_payee),
            payees = payees,
            field = payeeField,
            onSelect = { entry ->
                // A transfer's history is not a subscription's to reuse.
                if (entry.kind == PayeeKind.transfer) {
                    payee = entry.payee
                    return@PayeeField
                }

                val wanted = if (entry.kind == PayeeKind.income) Direction.INCOME else Direction.EXPENSE
                val usable = groupForPicker(if (wanted == Direction.INCOME) incomeCategories(categories) else expenseCategories(categories))
                    .flatMap { listOf(it.parent) + it.children }

                payee = entry.payee
                direction = wanted
                entry.accountId?.takeIf { id -> accounts.any { it.id == id } }?.let { accountId = it }
                categoryId = entry.categoryId?.takeIf { id -> usable.any { it.id == id } } ?: ""
                if (notes.isBlank()) notes = entry.notes
            },
        )

        YuukaTextField(
            value = amount,
            onValueChange = { amount = it },
            label = stringResource(R.string.label_amount),
            placeholder = stringResource(R.string.placeholder_amount_decimal),
            singleLine = true,
            field = amountField,
        )

        DropdownField(
            label = stringResource(R.string.label_account),
            value = accounts.firstOrNull { it.id == accountId }?.name ?: "",
            options = accounts.map { SelectOption(it.id, it.name) },
            onSelect = { accountId = it },
            field = accountField,
        )

        val uncategorizedLabel = stringResource(R.string.category_uncategorized)
        DropdownField(
            label = stringResource(R.string.label_category),
            value = selectable.firstOrNull { it.id == categoryId }?.name ?: uncategorizedLabel,
            options = categoryOptions(categoryGroups, uncategorizedLabel).map { SelectOption(it.value ?: "", it.label, it.indent) },
            onSelect = { categoryId = it },
        )

        PickerField(
            value = startOn.format(DateTimeFormatter.ISO_LOCAL_DATE),
            label = stringResource(if (editing != null) R.string.label_next_posts_on else R.string.label_starts_on),
            supportingText = stringResource(R.string.subscription_start_hint) +
                if (editing != null) " " + stringResource(R.string.subscription_restart_hint) else "",
            onClick = { pickingDate = true },
            modifier = Modifier.fillMaxWidth(),
        )

        if (pickingDate) {
            YuukaDatePickerDialog(
                initial = startOn,
                earliest = if (editing != null && initialStart.isBefore(today)) null else today,
                onDismiss = { pickingDate = false },
                onPicked = {
                    startOn = it
                    pickingDate = false
                },
            )
        }

        YuukaTextField(
            value = notes,
            onValueChange = { notes = it },
            label = stringResource(R.string.label_notes),
            placeholder = stringResource(R.string.placeholder_optional),
            field = notesField,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(
                modifier = Modifier.weight(1f),
                enabled = !submitting && form.valid(payeeField, amountField, accountField, notesField),
                onClick = {
                    val minor = amountMinor ?: return@Button

                    onSubmit(
                        SubscriptionSubmission(
                            accountId = accountId,
                            categoryId = categoryId.ifBlank { null },
                            amount = if (direction == Direction.EXPENSE) -minor else minor,
                            payee = payee.trim(),
                            notes = notes,
                            // Editing the date restarts the schedule from it; leaving it alone keeps the schedule as it is.
                            startOn = if (editing == null || startOn != initialStart) startOn.format(DateTimeFormatter.ISO_LOCAL_DATE) else null,
                        ),
                    )
                },
            ) {
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(stringResource(if (editing != null) R.string.save_changes else R.string.add_subscription))
                }
            }
        }
    }
}
