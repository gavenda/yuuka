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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.data.model.Account
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.toDecimalString
import dev.gavenda.yuuka.ui.common.AccountLogo
import dev.gavenda.yuuka.ui.common.ActionIcon
import dev.gavenda.yuuka.ui.common.ActionIconButton
import dev.gavenda.yuuka.ui.common.DenseOutlinedTextField
import dev.gavenda.yuuka.ui.common.EmptyState
import dev.gavenda.yuuka.ui.common.MoneyText
import dev.gavenda.yuuka.ui.common.MoneyTone
import dev.gavenda.yuuka.ui.common.StatCard
import dev.gavenda.yuuka.ui.common.SwipeToRevealActions
import dev.gavenda.yuuka.domain.parseMoney
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(modifier: Modifier = Modifier, viewModel: AccountsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var typesOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New account") },
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
                    TextButton(onClick = { typesOpen = true }) { Text("Manage types") }
                }
            }

            item { StatCard("Net worth", state.netWorth, currency = state.displayCurrency, hero = true) }

            if (state.groups.isEmpty()) {
                item { EmptyState("No accounts yet", description = "Add the accounts you want to track.") }
            } else {
                state.groups.forEach { group ->
                    item(key = "type:${group.id}") {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${group.name} (${group.accounts.size})", style = MaterialTheme.typography.titleSmall)
                            MoneyText(group.total, tone = MoneyTone.SIGNED, currency = group.currency)
                        }
                    }
                    items(group.accounts, key = { it.id }) { account ->
                        AccountCard(
                            account = account,
                            onEdit = { editing = account },
                            onToggleArchive = { scope.launch { viewModel.setArchived(account.id, !account.archived) } },
                            onDelete = { pendingDelete = account },
                        )
                    }
                }
            }

            if (state.archivedCount > 0) {
                item {
                    TextButton(onClick = viewModel::toggleShowArchived) {
                        Text("${if (state.showArchived) "Hide" else "Show"} ${state.archivedCount} archived")
                    }
                }
            }
        }
    }

    if (typesOpen) {
        ModalBottomSheet(onDismissRequest = { typesOpen = false }) {
            AccountTypeManagerContent(state.accountTypes, viewModel)
        }
    }

    if (creating || editing != null) {
        ModalBottomSheet(onDismissRequest = { creating = false; editing = null }) {
            AccountFormContent(
                account = editing,
                accountTypes = state.accountTypes.filter { !it.archived },
                displayCurrency = state.displayCurrency,
                onSave = { name, typeId, currency, startingBalance, logoUrl, invertDark ->
                    scope.launch {
                        if (editing != null) viewModel.updateAccount(editing!!.id, name, typeId, currency, startingBalance, logoUrl, invertDark)
                        else viewModel.createAccount(name, typeId, currency, startingBalance, logoUrl, invertDark)
                        creating = false
                        editing = null
                    }
                },
                onCancel = { creating = false; editing = null },
            )
        }
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null; deleteError = null },
            title = { Text("Delete \"${toDelete.name}\"?") },
            text = { if (deleteError != null) Text(deleteError!!) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            viewModel.deleteAccount(toDelete.id, includeTransactions = deleteError != null)
                            pendingDelete = null
                            deleteError = null
                        } catch (e: ApiError) {
                            deleteError = "${e.message}\n\nDelete the account and its transactions?"
                        }
                    }
                }) { Text(if (deleteError != null) "Delete anyway" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null; deleteError = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AccountCard(account: Account, onEdit: () -> Unit, onToggleArchive: () -> Unit, onDelete: () -> Unit) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(if (account.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE, "Archive ${account.name}", onToggleArchive)
            ActionIconButton(ActionIcon.DELETE, "Delete ${account.name}", onDelete, danger = true)
        },
    ) {
        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(account.name + if (account.archived) " (Archived)" else "", style = MaterialTheme.typography.bodyLarge)
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

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (account != null) "Edit account" else "New account", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())

        ExposedDropdownMenuBox(expanded = typeMenuOpen, onExpandedChange = { typeMenuOpen = it }) {
            OutlinedTextField(
                value = accountTypes.firstOrNull { it.id == typeId }?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
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
            label = { Text("Starting balance") },
            placeholder = { Text("0.00") },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(value = currency, onValueChange = { currency = it.uppercase() }, label = { Text("Currency") }, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(value = logoUrl, onValueChange = { logoUrl = it }, label = { Text("Logo URL (optional)") }, modifier = Modifier.fillMaxWidth())

        if (logoUrl.isNotBlank()) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Invert colours in dark mode", modifier = Modifier.weight(1f))
                Switch(checked = invertDark, onCheckedChange = { invertDark = it })
            }
        }

        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = {
                val balance = parseMoney(startingBalance)
                if (balance == null) {
                    error = "Starting balance must be a number."
                    return@Button
                }
                if (name.isBlank() || typeId.isBlank()) {
                    error = "Name and type are required."
                    return@Button
                }
                onSave(name, typeId, currency, balance, logoUrl.trim(), invertDark)
            }) { Text(if (account != null) "Save changes" else "Add account") }
        }
    }
}

@Composable
private fun AccountTypeManagerContent(types: List<AccountType>, viewModel: AccountsViewModel) {
    val scope = rememberCoroutineScope()
    var newName by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Account types", style = MaterialTheme.typography.titleMedium)
        Text(
            "These are your own labels. Rename one and every account using it follows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            DenseOutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = "Add a type",
                modifier = Modifier.weight(1f),
            )
            Button(
                enabled = newName.isNotBlank(),
                onClick = {
                    scope.launch {
                        try {
                            viewModel.createAccountType(newName.trim())
                            newName = ""
                        } catch (e: ApiError) {
                            error = e.message
                        }
                    }
                },
            ) { Text("Add") }
        }

        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

        val visible = types.filter { showArchived || !it.archived }
        visible.forEach { type ->
            if (editingId == type.id) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    DenseOutlinedTextField(value = draftName, onValueChange = { draftName = it }, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch { viewModel.renameAccountType(type.id, draftName.trim()); editingId = null }
                    }) { Text("Save") }
                    TextButton(onClick = { editingId = null }) { Text("Cancel") }
                }
            } else {
                SwipeToRevealActions(
                    modifier = Modifier.fillMaxWidth(),
                    actions = {
                        ActionIconButton(ActionIcon.EDIT, "Rename ${type.name}", { editingId = type.id; draftName = type.name })
                        ActionIconButton(
                            if (type.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE,
                            "Archive ${type.name}",
                            { scope.launch { viewModel.setAccountTypeArchived(type.id, !type.archived) } },
                        )
                        ActionIconButton(
                            ActionIcon.DELETE,
                            "Delete ${type.name}",
                            { scope.launch { runCatching { viewModel.deleteAccountType(type.id) } } },
                            danger = true,
                            enabled = type.accountCount == 0,
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
                        Text(type.name + if (type.archived) " (Archived)" else "", modifier = Modifier.weight(1f))
                        Text("${type.accountCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val archivedCount = types.count { it.archived }
        if (archivedCount > 0) {
            TextButton(onClick = { showArchived = !showArchived }) { Text("${if (showArchived) "Hide" else "Show"} $archivedCount archived") }
        }
    }
}
