package dev.gavenda.yuuka.ui.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material3.*
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.LOGO_URL_MAX
import dev.gavenda.yuuka.domain.PAYEE_MAX
import dev.gavenda.yuuka.domain.formatMoney
import dev.gavenda.yuuka.domain.isCurrencyCode
import dev.gavenda.yuuka.domain.isHttpUrl
import dev.gavenda.yuuka.domain.parseMoney
import dev.gavenda.yuuka.domain.toDecimalString
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountsScreen(modifier: Modifier = Modifier, viewModel: AccountsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var typesOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var adjusting by remember { mutableStateOf<Account?>(null) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val accountRestoredMessage = stringResource(R.string.account_restored)
    val accountArchivedMessage = stringResource(R.string.account_archived)
    val accountUpdatedMessage = stringResource(R.string.account_updated)
    val accountAddedMessage = stringResource(R.string.account_added)
    val accountDeletedMessage = stringResource(R.string.account_deleted)
    val balanceAdjustedMessage = stringResource(R.string.balance_adjusted)
    val couldNotDeleteAccountMessage = stringResource(R.string.could_not_delete_account)
    val deleteAccountTransactionsConfirmTemplate = stringResource(R.string.delete_account_transactions_confirm)

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            val newAccountLabel = stringResource(R.string.new_account)
            val editTypesLabel = stringResource(R.string.edit_account_types)
            ScreenFab(
                label = stringResource(R.string.account_actions),
                icon = Icons.Filled.Add,
                onClick = { fabMenuExpanded = true },
                // The navigation rail lists the same two actions in a dropdown from its button.
                actions = listOf(
                    FabAction(newAccountLabel, { Icon(Icons.Filled.LibraryAdd, contentDescription = null) }) { creating = true },
                    FabAction(editTypesLabel, { Icon(painterResource(R.drawable.ic_contract_edit), contentDescription = null) }) {
                        typesOpen = true
                    },
                ),
                phoneFab = {
                    BackHandler(fabMenuExpanded) { fabMenuExpanded = false }
                    FloatingActionButtonMenu(
                        // The menu pads its own button 16dp in from the end and 16dp up from the bottom, on top
                        // of the Scaffold's usual FAB inset — this cancels it so the FAB lines up with the
                        // ExtendedFloatingActionButton on the other screens.
                        modifier = Modifier.offset(x = 16.dp, y = 16.dp),
                        expanded = fabMenuExpanded,
                        button = {
                            val actionsLabel = stringResource(R.string.account_actions)
                            ToggleFloatingActionButton(
                                modifier = Modifier.semantics { contentDescription = actionsLabel },
                                checked = fabMenuExpanded,
                                onCheckedChange = { fabMenuExpanded = it },
                            ) {
                                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add } }
                                Icon(rememberVectorPainter(icon), contentDescription = null, modifier = Modifier.animateIcon({ checkedProgress }))
                            }
                        },
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = { fabMenuExpanded = false; creating = true },
                            icon = { Icon(Icons.Filled.LibraryAdd, contentDescription = null) },
                            text = { Text(newAccountLabel) },
                        )
                        FloatingActionButtonMenuItem(
                            onClick = { fabMenuExpanded = false; typesOpen = true },
                            icon = { Icon(painterResource(R.drawable.ic_contract_edit), contentDescription = null) },
                            text = { Text(editTypesLabel) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { StatCard(stringResource(R.string.net_worth), state.netWorth, currency = state.displayCurrency, hero = true) }

            if (state.groups.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_accounts_yet), description = stringResource(R.string.accounts_empty_description)) }
            } else {
                state.groups.forEach { group ->
                    item(key = "type:${group.id}") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${group.name} (${group.accounts.size})", style = MaterialTheme.typography.titleSmall)
                            MoneyText(group.total, tone = MoneyTone.SIGNED, currency = group.currency)
                        }
                    }
                    items(group.accounts, key = { it.id }) { account ->
                        val archiveKey = "archive:${account.id}"
                        AccountCard(
                            account = account,
                            archiving = busy.isBusy(archiveKey),
                            onEdit = { editing = account },
                            onAdjust = { adjusting = account },
                            onToggleArchive = {
                                busy.run(
                                    archiveKey,
                                    snackbarHostState,
                                    successMessage = if (account.archived) accountRestoredMessage else accountArchivedMessage,
                                ) { viewModel.setArchived(account.id, !account.archived) }
                            },
                            onDelete = { pendingDelete = account },
                        )
                    }
                }
            }

            if (state.archivedCount > 0) {
                item {
                    TextButton(onClick = viewModel::toggleShowArchived) {
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

    if (typesOpen) {
        ModalBottomSheet(onDismissRequest = { typesOpen = false }) {
            WithSnackbarOverlay {
                AccountTypeManagerContent(state.accountTypes, viewModel)
            }
        }
    }

    if (creating || editing != null) {
        val formKey = "account-form"
        val submitting = busy.isBusy(formKey)
        ModalBottomSheet(onDismissRequest = { if (!submitting) { creating = false; editing = null } }) {
            WithSnackbarOverlay {
                AccountFormContent(
                    account = editing,
                    accountTypes = state.accountTypes.filter { !it.archived },
                    displayCurrency = state.displayCurrency,
                    submitting = submitting,
                    onSave = { name, typeId, currency, startingBalance, logoUrl, invertDark, roundUpSource ->
                        busy.run(
                            formKey,
                            snackbarHostState,
                            successMessage = if (editing != null) accountUpdatedMessage else accountAddedMessage,
                            onSuccess = { creating = false; editing = null },
                        ) {
                            if (editing != null) {
                                viewModel.updateAccount(editing!!.id, name, typeId, currency, startingBalance, logoUrl, invertDark, roundUpSource)
                            } else {
                                viewModel.createAccount(name, typeId, currency, startingBalance, logoUrl, invertDark, roundUpSource)
                            }
                        }
                    },
                    onCancel = { creating = false; editing = null },
                )
            }
        }
    }

    val toAdjust = adjusting
    if (toAdjust != null) {
        val adjustKey = "account-adjust:${toAdjust.id}"
        val submitting = busy.isBusy(adjustKey)
        ModalBottomSheet(onDismissRequest = { if (!submitting) adjusting = null }) {
            WithSnackbarOverlay {
                AccountAdjustContent(
                    account = toAdjust,
                    submitting = submitting,
                    onSave = { balance, payee ->
                        busy.run(adjustKey, snackbarHostState, successMessage = balanceAdjustedMessage, onSuccess = { adjusting = null }) {
                            viewModel.adjustBalance(toAdjust.id, balance, payee)
                        }
                    },
                    onCancel = { adjusting = null },
                )
            }
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        val deleteKey = "delete:${toDelete.id}"
        val deleting = busy.isBusy(deleteKey)
        AlertDialog(
            onDismissRequest = { if (!deleting) { pendingDelete = null; deleteError = null } },
            title = { Text(stringResource(R.string.delete_confirm_title, toDelete.name)) },
            text = {
                WithSnackbarOverlay {
                    if (deleteError != null) Text(deleteError!!)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        busy.launch(deleteKey) {
                            try {
                                viewModel.deleteAccount(toDelete.id, includeTransactions = deleteError != null)
                                pendingDelete = null
                                deleteError = null
                                snackbarHostState.showSnackbar(accountDeletedMessage)
                            } catch (e: ApiError) {
                                if (e.status == 409) {
                                    deleteError = deleteAccountTransactionsConfirmTemplate.format(e.message)
                                } else {
                                    snackbarHostState.showSnackbar(e.message ?: couldNotDeleteAccountMessage)
                                }
                            }
                        }
                    },
                ) {
                    if (deleting) {
                        MutationLoadingIndicator()
                    } else {
                        Text(if (deleteError != null) stringResource(R.string.delete_anyway) else stringResource(R.string.action_delete))
                    }
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null; deleteError = null }, enabled = !deleting) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AccountCard(
    account: Account,
    archiving: Boolean,
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
        Card(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(if (account.archived) stringResource(R.string.name_archived, account.name) else account.name, style = MaterialTheme.typography.bodyLarge)
                    Text(account.currency, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(account.balance, tone = MoneyTone.SIGNED, currency = account.currency, modifier = Modifier.padding(top = 4.dp))
                }
                AccountLogo(account.name, account.logoUrl, account.logoInvertDark, size = 28)
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
    onSave: (String, String, String, Long, String, Boolean, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var typeId by remember { mutableStateOf(account?.typeId ?: accountTypes.firstOrNull()?.id ?: "") }
    var currency by remember { mutableStateOf(account?.currency ?: displayCurrency) }
    var startingBalance by remember { mutableStateOf(account?.let { toDecimalString(it.startingBalance) } ?: "0.00") }
    var logoUrl by remember { mutableStateOf(account?.logoUrl ?: "") }
    var invertDark by remember { mutableStateOf(account?.logoInvertDark ?: false) }
    var roundUpSource by remember { mutableStateOf(account?.roundUpSource ?: false) }
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
            logoUrl.isBlank() -> null
            logoUrl.trim().length > LOGO_URL_MAX -> stringResource(R.string.error_too_long, LOGO_URL_MAX)
            !isHttpUrl(logoUrl) -> stringResource(R.string.error_logo_url)
            else -> null
        },
    )

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (account != null) stringResource(R.string.edit_account) else stringResource(R.string.new_account), style = MaterialTheme.typography.titleMedium)

        YuukaTextField(value = name, onValueChange = { name = it }, label = stringResource(R.string.label_name), singleLine = true, field = nameField)

        DropdownField(
            label = stringResource(R.string.label_type),
            value = accountTypes.firstOrNull { it.id == typeId }?.name ?: "",
            options = accountTypes.map { SelectOption(it.id, it.name) },
            onSelect = { typeId = it },
            field = typeField,
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

        // Whether this account's own purchases round up under Save the Change. The rule itself (how much, and where it goes) lives on that screen.
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.round_up_purchases_account_label), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.round_up_purchases_account_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            YuukaSwitch(checked = roundUpSource, onCheckedChange = { roundUpSource = it })
        }

        YuukaTextField(value = logoUrl, onValueChange = { logoUrl = it }, label = stringResource(R.string.label_logo_url), singleLine = true, field = logoField)

        // Always shown, not only once a logo is entered; it has no effect until the account has one.
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(stringResource(R.string.invert_colours_dark_mode), modifier = Modifier.weight(1f))
            YuukaSwitch(checked = invertDark, onCheckedChange = { invertDark = it })
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(
                modifier = Modifier.weight(1f),
                enabled = !submitting && form.valid(nameField, typeField, balanceField, currencyField, logoField),
                onClick = {
                    val balance = startingBalanceMinor ?: return@Button
                    onSave(name.trim(), typeId, currency.trim().uppercase(), balance, logoUrl.trim(), invertDark, roundUpSource)
                },
            ) {
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(if (account != null) stringResource(R.string.save_changes) else stringResource(R.string.add_account))
                }
            }
        }
    }
}

@Composable
private fun AccountAdjustContent(account: Account, submitting: Boolean, onSave: (Long, String) -> Unit, onCancel: () -> Unit) {
    var balance by remember { mutableStateOf(toDecimalString(account.balance)) }
    var payee by remember { mutableStateOf("") }
    val form = rememberFormValidation()
    val targetMinor = parseMoney(balance)
    val difference = targetMinor?.let { it - account.balance }
    val balanceField = form.field(
        "balance",
        when {
            targetMinor == null -> stringResource(R.string.error_balance_number)
            targetMinor == account.balance -> stringResource(R.string.error_balance_unchanged)
            else -> null
        },
    )
    val payeeField = form.field("payee", if (payee.trim().length > PAYEE_MAX) stringResource(R.string.error_too_long, PAYEE_MAX) else null)

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.adjust_balance), style = MaterialTheme.typography.titleMedium)

        Text(
            stringResource(R.string.adjust_balance_description, account.name, formatMoney(account.balance, account.currency)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        YuukaTextField(
            value = payee,
            onValueChange = { payee = it },
            label = stringResource(R.string.label_payee_optional),
            singleLine = true,
            field = payeeField,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(
                modifier = Modifier.weight(1f),
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
        }
    }
}

@Composable
private fun AccountTypeManagerContent(types: List<AccountType>, viewModel: AccountsViewModel) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    var newName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }

    val addKey = "type-add"
    val adding = busy.isBusy(addKey)

    // Types are unique by name, so a name is checked against the others (a rename may keep its own).
    val addForm = rememberFormValidation()
    val addField = addForm.field("name", nameProblem(newName, R.string.error_name_taken_account_type) { name -> types.any { it.name == name } })
    val renameForm = remember(editingId) { FormValidation() }
    val renameField = renameForm.field(
        "name",
        nameProblem(draftName, R.string.error_name_taken_account_type) { name -> types.any { it.id != editingId && it.name == name } },
    )

    val typeAddedMessage = stringResource(R.string.type_added)
    val typeRenamedMessage = stringResource(R.string.type_renamed)
    val typeRestoredMessage = stringResource(R.string.type_restored)
    val typeArchivedMessage = stringResource(R.string.type_archived)
    val typeDeletedMessage = stringResource(R.string.type_deleted)

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.account_types_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.account_types_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Top-aligned: a field in error grows a line beneath itself, and the button stays beside the field, not the line.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
            DenseOutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = stringResource(R.string.placeholder_add_a_type),
                field = addField,
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = !adding && addForm.valid(addField),
                onClick = {
                    val name = newName.trim()
                    busy.run(addKey, snackbarHostState, successMessage = typeAddedMessage, onSuccess = { newName = "" }) {
                        viewModel.createAccountType(name)
                    }
                },
            ) {
                if (adding) {
                    MutationLoadingIndicator()
                } else {
                    Text(stringResource(R.string.action_add))
                }
            }
        }

        val visible = types.filter { showArchived || !it.archived }
        visible.forEach { type ->
            if (editingId == type.id) {
                val renameKey = "type-rename:${type.id}"
                val renaming = busy.isBusy(renameKey)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.Top) {
                    DenseOutlinedTextField(value = draftName, onValueChange = { draftName = it }, field = renameField, modifier = Modifier.weight(1f), enabled = !renaming)
                    TextButton(
                        enabled = !renaming && renameForm.valid(renameField),
                        onClick = {
                            val name = draftName.trim()
                            busy.run(renameKey, snackbarHostState, successMessage = typeRenamedMessage, onSuccess = { editingId = null }) {
                                viewModel.renameAccountType(type.id, name)
                            }
                        },
                    ) {
                        if (renaming) MutationLoadingIndicator() else Text(stringResource(R.string.action_save))
                    }
                    TextButton(onClick = { editingId = null }, enabled = !renaming) { Text(stringResource(R.string.action_cancel)) }
                }
            } else {
                val archiveKey = "type-archive:${type.id}"
                val deleteKey = "type-delete:${type.id}"
                SwipeToRevealActions(
                    modifier = Modifier.fillMaxWidth(),
                    actions = {
                        ActionIconButton(ActionIcon.EDIT, stringResource(R.string.cd_rename_item, type.name), { editingId = type.id; draftName = type.name })
                        ActionIconButton(
                            if (type.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE,
                            stringResource(R.string.cd_archive_item, type.name),
                            {
                                busy.run(
                                    archiveKey,
                                    snackbarHostState,
                                    successMessage = if (type.archived) typeRestoredMessage else typeArchivedMessage,
                                ) { viewModel.setAccountTypeArchived(type.id, !type.archived) }
                            },
                            loading = busy.isBusy(archiveKey),
                        )
                        ActionIconButton(
                            ActionIcon.DELETE,
                            stringResource(R.string.cd_delete_item, type.name),
                            {
                                busy.run(deleteKey, snackbarHostState, successMessage = typeDeletedMessage) {
                                    viewModel.deleteAccountType(type.id)
                                }
                            },
                            danger = true,
                            enabled = type.accountCount == 0,
                            loading = busy.isBusy(deleteKey),
                        )
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(if (type.archived) stringResource(R.string.name_archived, type.name) else type.name, modifier = Modifier.weight(1f))
                        Text("${type.accountCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val archivedCount = types.count { it.archived }
        if (archivedCount > 0) {
            TextButton(onClick = { showArchived = !showArchived }) {
                Text(
                    stringResource(
                        if (showArchived) R.string.archived_toggle_hide else R.string.archived_toggle_show,
                        archivedCount,
                    ),
                )
            }
        }
    }
}
