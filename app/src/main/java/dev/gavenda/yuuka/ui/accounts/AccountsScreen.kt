package dev.gavenda.yuuka.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.toDecimalString
import dev.gavenda.yuuka.ui.common.AccountLogo
import dev.gavenda.yuuka.ui.common.ActionIcon
import dev.gavenda.yuuka.ui.common.ActionIconButton
import dev.gavenda.yuuka.ui.common.DenseOutlinedTextField
import dev.gavenda.yuuka.ui.common.EmptyState
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.MoneyText
import dev.gavenda.yuuka.ui.common.MoneyTone
import dev.gavenda.yuuka.ui.common.MutationLoadingIndicator
import dev.gavenda.yuuka.ui.common.StatCard
import dev.gavenda.yuuka.ui.common.SwipeToRevealActions
import dev.gavenda.yuuka.ui.common.WithSnackbarOverlay
import dev.gavenda.yuuka.ui.common.rememberBusyState
import dev.gavenda.yuuka.domain.parseMoney
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(modifier: Modifier = Modifier, viewModel: AccountsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    var typesOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    val accountRestoredMessage = stringResource(R.string.account_restored)
    val accountArchivedMessage = stringResource(R.string.account_archived)
    val accountUpdatedMessage = stringResource(R.string.account_updated)
    val accountAddedMessage = stringResource(R.string.account_added)
    val accountDeletedMessage = stringResource(R.string.account_deleted)
    val couldNotDeleteAccountMessage = stringResource(R.string.could_not_delete_account)
    val deleteAccountTransactionsConfirmTemplate = stringResource(R.string.delete_account_transactions_confirm)

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_account)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { typesOpen = true }) { Text(stringResource(R.string.manage_types)) }
                }
            }

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
                    onSave = { name, typeId, currency, startingBalance, logoUrl, invertDark ->
                        busy.run(
                            formKey,
                            snackbarHostState,
                            successMessage = if (editing != null) accountUpdatedMessage else accountAddedMessage,
                            onSuccess = { creating = false; editing = null },
                        ) {
                            if (editing != null) viewModel.updateAccount(editing!!.id, name, typeId, currency, startingBalance, logoUrl, invertDark)
                            else viewModel.createAccount(name, typeId, currency, startingBalance, logoUrl, invertDark)
                        }
                    },
                    onCancel = { creating = false; editing = null },
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
private fun AccountCard(account: Account, archiving: Boolean, onEdit: () -> Unit, onToggleArchive: () -> Unit, onDelete: () -> Unit) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(
                if (account.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE,
                stringResource(R.string.cd_archive_item, account.name),
                onToggleArchive,
                loading = archiving,
            )
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, account.name), onDelete, danger = true)
        },
    ) {
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFormContent(
    account: Account?,
    accountTypes: List<AccountType>,
    displayCurrency: String,
    submitting: Boolean,
    onSave: (String, String, String, Long, String, Boolean) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(account?.name ?: "") }
    var typeId by remember { mutableStateOf(account?.typeId ?: accountTypes.firstOrNull()?.id ?: "") }
    var currency by remember { mutableStateOf(account?.currency ?: displayCurrency) }
    var startingBalance by remember { mutableStateOf(account?.let { toDecimalString(it.startingBalance) } ?: "0.00") }
    var logoUrl by remember { mutableStateOf(account?.logoUrl ?: "") }
    var invertDark by remember { mutableStateOf(account?.logoInvertDark ?: false) }
    var error by remember { mutableStateOf<String?>(null) }
    var typeMenuOpen by remember { mutableStateOf(false) }

    val startingBalanceErrorMessage = stringResource(R.string.error_starting_balance_number)
    val nameTypeRequiredErrorMessage = stringResource(R.string.error_name_type_required)

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (account != null) stringResource(R.string.edit_account) else stringResource(R.string.new_account), style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth())

        ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
            OutlinedTextField(
                value = accountTypes.firstOrNull { it.id == typeId }?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.label_type)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                accountTypes.forEach { type ->
                    DropdownMenuItem(text = { Text(type.name) }, onClick = { typeId = type.id; typeMenuOpen = false })
                }
            }
        }

        OutlinedTextField(
            value = startingBalance,
            onValueChange = { startingBalance = it },
            label = { Text(stringResource(R.string.label_starting_balance)) },
            placeholder = { Text(stringResource(R.string.placeholder_amount_decimal)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase() }, label = { Text(stringResource(R.string.label_currency)) }, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(value = logoUrl, onValueChange = { logoUrl = it }, label = { Text(stringResource(R.string.label_logo_url)) }, modifier = Modifier.fillMaxWidth())

        if (logoUrl.isNotBlank()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.invert_colours_dark_mode), modifier = Modifier.weight(1f))
                Switch(checked = invertDark, onCheckedChange = { invertDark = it })
            }
        }

        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, enabled = !submitting) { Text(stringResource(R.string.action_cancel)) }
            Button(
                enabled = !submitting,
                onClick = {
                    val balance = parseMoney(startingBalance)
                    if (balance == null) {
                        error = startingBalanceErrorMessage
                        return@Button
                    }
                    if (name.isBlank() || typeId.isBlank()) {
                        error = nameTypeRequiredErrorMessage
                        return@Button
                    }
                    onSave(name, typeId, currency, balance, logoUrl.trim(), invertDark)
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
private fun AccountTypeManagerContent(types: List<AccountType>, viewModel: AccountsViewModel) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    var newName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }

    val addKey = "type-add"
    val adding = busy.isBusy(addKey)

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

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            DenseOutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = stringResource(R.string.placeholder_add_a_type),
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = newName.isNotBlank() && !adding,
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
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    DenseOutlinedTextField(value = draftName, onValueChange = { draftName = it }, modifier = Modifier.weight(1f), enabled = !renaming)
                    TextButton(
                        enabled = !renaming,
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
