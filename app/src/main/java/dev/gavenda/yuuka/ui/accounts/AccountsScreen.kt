package dev.gavenda.yuuka.ui.accounts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.rememberTextFieldState
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.currencyName
import dev.gavenda.yuuka.domain.LOGO_URL_MAX
import dev.gavenda.yuuka.domain.PAYEE_MAX
import dev.gavenda.yuuka.domain.formatMoney
import dev.gavenda.yuuka.domain.isCurrencyCode
import dev.gavenda.yuuka.domain.isHttpUrl
import dev.gavenda.yuuka.domain.parseMoney
import dev.gavenda.yuuka.domain.toDecimalString
import dev.gavenda.yuuka.ui.common.rememberFormSwitch
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun AccountsScreen(modifier: Modifier = Modifier, viewModel: AccountsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AccountsScreenContent(
        state = state,
        modifier = modifier,
        onToggleShowArchived = viewModel::toggleShowArchived,
        onSearchTextChange = viewModel::setSearchText,
        onSaveAccount = viewModel::save,
        onSetArchived = viewModel::setArchived,
        onDeleteAccount = viewModel::deleteAccount,
        onAdjustBalance = viewModel::adjustBalance,
    )
}

/** The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model. */
@Composable
internal fun AccountsScreenContent(
    state: AccountsUiState,
    modifier: Modifier = Modifier,
    onToggleShowArchived: () -> Unit = {},
    onSearchTextChange: (String) -> Unit = {},
    onSaveAccount: suspend (id: String?, AccountSubmission) -> Unit = { _, _ -> },
    onSetArchived: suspend (id: String, archived: Boolean) -> Unit = { _, _ -> },
    onDeleteAccount: suspend (id: String, includeTransactions: Boolean) -> Unit = { _, _ -> },
    onAdjustBalance: suspend (id: String, balance: Long, payee: String) -> Unit = { _, _, _ -> },
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf<Account?>(null) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    // Beside a supporting pane the list stays usable, so opening another form asks first if this one has changes.
    val switchForm = rememberFormSwitch()
    // A form standing beside the list closes with the row it was editing, wherever that was deleted.
    LaunchedEffect(state.accounts) { if (editing?.let { open -> state.accounts.none { it.id == open.id } } == true) editing = null }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val accountRestoredMessage = stringResource(R.string.account_restored)
    val accountArchivedMessage = stringResource(R.string.account_archived)
    val accountUpdatedMessage = stringResource(R.string.account_updated)
    val accountAddedMessage = stringResource(R.string.account_added)
    val accountDeletedMessage = stringResource(R.string.account_deleted)
    val balanceAdjustedMessage = stringResource(R.string.balance_adjusted)
    val couldNotDeleteAccountMessage = stringResource(R.string.could_not_delete_account)
    val deleteAccountTransactionsConfirmTemplate = stringResource(R.string.delete_account_transactions_confirm)

    // The field owns the text; what is typed is handed to the view model, which does the filtering.
    val searchFieldState = rememberTextFieldState(state.searchText)
    LaunchedEffect(searchFieldState) {
        snapshotFlow { searchFieldState.text.toString() }.collectLatest { onSearchTextChange(it) }
    }
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.appBarScroll(scrollBehavior),
        // Bottom-bar destinations carry no title: the bar below already says where this is. Not month-scoped, so no month button.
        topBar = {
            MonthSearchTopBar(
                search = searchFieldState,
                searchHint = stringResource(R.string.search_accounts),
                scrollBehavior = scrollBehavior,
            ) {
                WithSnackbarOverlay {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.matches, key = { it.id }) { account ->
                            ListItem(
                                onClick = { switchForm { creating = false; editing = account } },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                content = { Text(if (account.archived) stringResource(R.string.name_archived, account.name) else account.name) },
                                supportingContent = account.typeName?.let { { Text(it) } },
                                trailingContent = { MoneyText(account.balance, tone = MoneyTone.SIGNED_ALERT, currency = account.currency, style = MaterialTheme.typography.bodyLarge) },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_account),
                icon = R.drawable.add_48px,
                onClick = { switchForm { editing = null; creating = true } },
            )
        },
    ) { padding ->
        LazyColumn(
            // The gap under the bar is the column's own, not content that scrolls away: the bar comes back over
            // the list as it scrolls up, and the rows must not touch it.
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            // The rows of a type are a hair apart, the way a settings group is drawn; the space between
            // one type and the next comes from the header's own padding.
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            if (state.groups.isEmpty()) {
                item { EmptyState(stringResource(R.string.accounts_empty_title), YuukaMood.Attentive, modifier = Modifier.fillParentMaxHeight(), description = stringResource(R.string.accounts_empty_description)) }
            } else {
                state.groups.forEach { group ->
                    item(key = "type:${group.id}") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${group.name} (${group.accounts.size})",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            MoneyText(group.total, tone = MoneyTone.SIGNED_ALERT, currency = group.currency, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    itemsIndexed(group.accounts, key = { _, account -> account.id }) { index, account ->
                        val archiveKey = "archive:${account.id}"
                        AccountCard(
                            account = account,
                            position = positionInGroup(index, group.accounts.lastIndex),
                            archiving = busy.isBusy(archiveKey),
                            // The account whose form stands beside the list.
                            selected = account.id == editing?.id,
                            onEdit = { switchForm { creating = false; editing = account } },
                            onAdjust = { adjusting = account },
                            onToggleArchive = {
                                busy.run(
                                    archiveKey,
                                    snackbarHostState,
                                    successMessage = if (account.archived) accountRestoredMessage else accountArchivedMessage,
                                ) { onSetArchived(account.id, !account.archived) }
                            },
                            onDelete = { pendingDelete = account },
                        )
                    }
                }
            }

            if (state.archivedCount > 0) {
                item {
                    TextButton(onClick = onToggleShowArchived) {
                        Text(
                            stringResource(
                                if (state.showArchived) R.string.archived_toggle_hide else R.string.archived_toggle_show,
                                state.archivedCount,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) key(editing?.id) {
        val formKey = "account-form"
        val submitting = busy.isBusy(formKey)
        AccountFormContent(
            account = editing,
            accountTypes = state.accountTypes.filter { !it.archived },
            displayCurrency = state.displayCurrency,
            submitting = submitting,
            onSave = { submission ->
                val id = editing?.id
                busy.run(
                    formKey,
                    snackbarHostState,
                    successMessage = if (editing != null) accountUpdatedMessage else accountAddedMessage,
                    onSuccess = { creating = false; editing = null },
                ) { onSaveAccount(id, submission) }
            },
            onCancel = { creating = false; editing = null },
        )
    }

    val toAdjust = adjusting
    if (toAdjust != null) {
        val adjustKey = "account-adjust:${toAdjust.id}"
        val submitting = busy.isBusy(adjustKey)
        AccountAdjustDialog(
            account = toAdjust,
            submitting = submitting,
            onSave = { balance, payee ->
                busy.run(adjustKey, snackbarHostState, successMessage = balanceAdjustedMessage, onSuccess = { adjusting = null }) {
                    onAdjustBalance(toAdjust.id, balance, payee)
                }
            },
            onCancel = { adjusting = null },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        val deleteKey = "delete:${toDelete.id}"
        val deleting = busy.isBusy(deleteKey)
        ConfirmDeleteDialog(
            title = stringResource(R.string.delete_confirm_title, toDelete.name),
            body = deleteError ?: stringResource(R.string.delete_account_body),
            deleting = deleting,
            confirmLabel = stringResource(if (deleteError != null) R.string.delete_anyway else R.string.action_yes_delete),
            onConfirm = {
                busy.launch(deleteKey) {
                    try {
                        onDeleteAccount(toDelete.id, deleteError != null)
                        pendingDelete = null
                        deleteError = null
                        snackbarHostState.showSnackbar(accountDeletedMessage)
                    } catch (e: ApiError) {
                        if (e.status == 409) {
                            deleteError = deleteAccountTransactionsConfirmTemplate.format(toDelete.name)
                        } else {
                            snackbarHostState.showSnackbar(e.message ?: couldNotDeleteAccountMessage)
                        }
                    }
                }
            },
            onDismiss = { pendingDelete = null; deleteError = null },
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    position: ItemPosition,
    archiving: Boolean,
    selected: Boolean,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(ActionIcon.ADJUST, stringResource(R.string.cd_adjust_balance_item, account.name), onAdjust)
            ActionIconButton(
                if (account.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE,
                stringResource(R.string.cd_archive_item, account.name),
                onToggleArchive,
                loading = archiving,
            )
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, account.name), onDelete, danger = true)
        },
    ) {
        // One of a type's rows rather than a card of its own: square where it meets its neighbours,
        // round on the group's outer edges, the same block the settings groups are drawn as. The surface
        // takes the tap and the shape; what is in it is a plain row of a column and the logo.
        GroupedItem(position = position, modifier = Modifier.fillMaxWidth(), onClick = onEdit, selected = selected) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (account.archived) stringResource(R.string.name_archived, account.name) else account.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(currencyName(account.currency), style = MaterialTheme.typography.bodySmall)
                    MoneyText(account.balance, tone = MoneyTone.SIGNED_ALERT, currency = account.currency, style = MaterialTheme.typography.titleMedium)
                }
                AccountLogo(account.name, account.bank, account.logoUrl, account.logoInvertDark, size = 28)
            }
        }
    }
}

@Composable
private fun AccountFormContent(
    account: Account?,
    accountTypes: List<AccountType>,
    displayCurrency: String,
    submitting: Boolean,
    onSave: (AccountSubmission) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var typeId by remember { mutableStateOf(account?.typeId ?: accountTypes.firstOrNull()?.id ?: "") }
    var currency by remember { mutableStateOf(account?.currency ?: displayCurrency) }
    var startingBalance by remember { mutableStateOf(account?.let { toDecimalString(it.startingBalance) } ?: "0.00") }
    // A preset bank's key, or null for Custom — only Custom takes a logo URL of its own.
    var bank by remember { mutableStateOf(account?.bank) }
    var logoUrl by remember { mutableStateOf(account?.logoUrl ?: "") }
    var invertDark by remember { mutableStateOf(account?.logoInvertDark ?: false) }
    // What the form opened with, so that closing it can tell an entry from an untouched form.
    val opened = remember { listOf(name, typeId, currency, startingBalance, bank, logoUrl, invertDark) }
    val custom = bank == null
    val customLabel = stringResource(R.string.bank_custom)
    // Every field's problem is worked out from what it holds now, and shown once its field has been left or a save tried.
    val form = rememberFormValidation()
    val startingBalanceMinor = parseMoney(startingBalance)
    val nameField = form.field("name", nameProblem(name))
    val typeField = form.field("type", if (accountTypes.none { it.id == typeId }) stringResource(R.string.error_choose_type) else null)
    val balanceField = form.field("balance", if (startingBalanceMinor == null) stringResource(R.string.error_starting_balance_number) else null)
    val currencyField = form.field(
        "currency",
        when {
            currency.isBlank() -> stringResource(R.string.error_currency_required)
            !isCurrencyCode(currency) -> stringResource(R.string.currency_hint_3letter)
            else -> null
        },
    )
    val logoField = form.field(
        "logo",
        when {
            // A preset bank brings its own logo, so the URL field is hidden and cannot hold the form back.
            !custom || logoUrl.isBlank() -> null
            logoUrl.trim().length > LOGO_URL_MAX -> stringResource(R.string.error_too_long, LOGO_URL_MAX)
            !isHttpUrl(logoUrl) -> stringResource(R.string.error_logo_url)
            else -> null
        },
    )

    FullScreenDialog(
        title = stringResource(if (account != null) R.string.edit_account else R.string.new_account),
        onDismiss = onCancel,
        onSave = submit@{
            val balance = startingBalanceMinor ?: return@submit
            // A preset bank's logo is its own, so a URL typed under Custom before switching is not kept.
            onSave(
                AccountSubmission(
                    name = name.trim(),
                    typeId = typeId,
                    currency = currency.trim().uppercase(),
                    startingBalance = balance,
                    logoUrl = if (custom) logoUrl.trim() else "",
                    logoInvertDark = custom && invertDark,
                    bank = bank,
                ),
            )
        },
        saveEnabled = form.valid(nameField, typeField, balanceField, currencyField, logoField),
        submitting = submitting,
        dirty = listOf(name, typeId, currency, startingBalance, bank, logoUrl, invertDark) != opened,
    ) {
        YuukaTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.label_name), singleLine = true, field = nameField)

        DropdownField(
            label = stringResource(R.string.label_type),
            value = accountTypes.firstOrNull { it.id == typeId }?.name ?: "",
            options = accountTypes.map { SelectOption(it.id, it.name) },
            onSelect = { typeId = it },
            field = typeField,
        )

        // The preset banks, then Custom, which is where a logo URL of the account's own is given.
        DropdownField(
            label = stringResource(R.string.label_bank),
            value = Banks.byKey(bank)?.name ?: customLabel,
            options = Banks.presets.map { SelectOption<String?>(it.key, it.name) } + SelectOption<String?>(null, customLabel),
            onSelect = { bank = it },
        )

        YuukaTextField(
            value = startingBalance,
            onValueChange = { startingBalance = it },
            label = stringResource(R.string.label_starting_balance),
            placeholder = stringResource(R.string.placeholder_amount_decimal),
            singleLine = true,
            field = balanceField,
        )

        YuukaTextField(
            value = currency,
            onValueChange = { currency = it.uppercase() },
            label = stringResource(R.string.label_currency),
            singleLine = true,
            field = currencyField,
        )

        // Only Custom takes a logo of the account's own; a preset bank brings its logo and its inversion.
        if (custom) {
            YuukaTextField(value = logoUrl, onValueChange = { logoUrl = it }, label = stringResource(R.string.label_logo_url), singleLine = true, field = logoField)

            // Shown under Custom whether or not a logo is entered; it has no effect until the account has one.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.invert_colours_dark_mode), modifier = Modifier.weight(1f))
                YuukaSwitch(checked = invertDark, onCheckedChange = { invertDark = it })
            }
        }

        // Only a new account: Yuuka at the foot of the form, ready to start counting.
        if (account == null) {
            YuukaSays(YuukaMood.Attentive, stringResource(R.string.account_form_line), Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun AccountAdjustDialog(account: Account, submitting: Boolean, onSave: (Long, String) -> Unit, onCancel: () -> Unit) {
    var balance by remember { mutableStateOf(toDecimalString(account.balance)) }
    var payee by remember { mutableStateOf("") }
    val form = rememberFormValidation()
    val targetMinor = parseMoney(balance)
    val difference = targetMinor?.let { it - account.balance }
    val balanceField = form.field(
        "balance",
        when (targetMinor) {
            null -> stringResource(R.string.error_balance_number)
            account.balance -> stringResource(R.string.error_balance_unchanged)
            else -> null
        },
    )
    val payeeField = form.field("payee", if (payee.trim().length > PAYEE_MAX) stringResource(R.string.error_too_long, PAYEE_MAX) else null)

    AlertDialog(
        onDismissRequest = { if (!submitting) onCancel() },
        title = { Text(stringResource(R.string.adjust_balance)) },
        text = {
            WithSnackbarOverlay {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.adjust_balance_description, account.name, formatMoney(account.balance, account.currency)),
                        style = MaterialTheme.typography.bodyMedium,
                    )

                    YuukaTextField(
                        value = balance,
                        onValueChange = { balance = it },
                        label = stringResource(R.string.label_new_balance),
                        placeholder = stringResource(R.string.placeholder_amount_decimal),
                        singleLine = true,
                        field = balanceField,
                    )

                    if (difference != null && difference != 0L) {
                        Text(
                            stringResource(
                                if (difference > 0) R.string.adjust_balance_logs_income else R.string.adjust_balance_logs_expense,
                                formatMoney(difference, account.currency),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    YuukaTextField(
                        value = payee,
                        onValueChange = { payee = it },
                        label = stringResource(R.string.label_payee_optional),
                        singleLine = true,
                        field = payeeField,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !submitting && form.valid(balanceField, payeeField),
                onClick = {
                    val target = targetMinor ?: return@Button
                    onSave(target, payee.trim())
                },
            ) {
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(stringResource(R.string.save_adjustment))
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !submitting) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountsScreenPreview() {
    ScreenPreview {
        AccountsScreenContent(
            AccountsUiState(accounts = PreviewData.accounts, accountTypes = PreviewData.accountTypes, netWorth = PreviewData.summary.netWorth),
        )
    }
}
