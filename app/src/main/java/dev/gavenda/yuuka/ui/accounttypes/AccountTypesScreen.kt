package dev.gavenda.yuuka.ui.accounttypes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountTypesScreen(modifier: Modifier = Modifier, viewModel: AccountTypesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.refresh() }

    AccountTypesScreenContent(
        state = state,
        modifier = modifier,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onSetArchived = viewModel::setArchived,
        onDelete = viewModel::delete,
    )
}

/**
 * The account types, on a screen of their own reached from Settings: a list like any other in the app. Pressing
 * a type renames it, swiping it shows archive and delete, and the screen's leading action asks for a new one by
 * name in a basic dialog — the same one a rename opens.
 */
@Composable
internal fun AccountTypesScreenContent(
    state: AccountTypesUiState,
    modifier: Modifier = Modifier,
    onCreate: suspend (name: String) -> Unit = {},
    onRename: suspend (id: String, name: String) -> Unit = { _, _ -> },
    onSetArchived: suspend (id: String, archived: Boolean) -> Unit = { _, _ -> },
    onDelete: suspend (id: String) -> Unit = {},
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AccountType?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val typeAddedMessage = stringResource(R.string.type_added)
    val typeRenamedMessage = stringResource(R.string.type_renamed)
    val typeRestoredMessage = stringResource(R.string.type_restored)
    val typeArchivedMessage = stringResource(R.string.type_archived)
    val typeDeletedMessage = stringResource(R.string.type_deleted)

    val types = state.types
    val visible = types.filter { showArchived || !it.archived }
    val archivedCount = types.count { it.archived }

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        modifier = modifier,
        topBar = { DetailTopBar(title = stringResource(R.string.account_types_title)) },
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_account_type),
                icon = Icons.Filled.Add,
                onClick = { adding = true },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            // Rows of the block sit a hair apart, the way a settings group is drawn.
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.account_types_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
                )
            }

            // One row per type, its accounts counted at the end, drawn as one connected block.
            itemsIndexed(visible, key = { _, type -> type.id }) { index, type ->
                val archiveKey = "type-archive:${type.id}"
                val deleteKey = "type-delete:${type.id}"
                SwipeToRevealActions(
                    modifier = Modifier.fillMaxWidth(),
                    actions = {
                        ActionIconButton(
                            if (type.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE,
                            stringResource(R.string.cd_archive_item, type.name),
                            {
                                busy.run(
                                    archiveKey,
                                    snackbarHostState,
                                    successMessage = if (type.archived) typeRestoredMessage else typeArchivedMessage,
                                ) { onSetArchived(type.id, !type.archived) }
                            },
                            loading = busy.isBusy(archiveKey),
                        )
                        // A type still in use cannot be deleted: the API reports how many accounts hold it.
                        ActionIconButton(
                            ActionIcon.DELETE,
                            stringResource(R.string.cd_delete_item, type.name),
                            { busy.run(deleteKey, snackbarHostState, successMessage = typeDeletedMessage) { onDelete(type.id) } },
                            danger = true,
                            enabled = type.accountCount == 0,
                            loading = busy.isBusy(deleteKey),
                        )
                    },
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth().clip(groupedItemShape(positionInGroup(index, visible.lastIndex))),
                        onClick = { renaming = type },
                        colors = ListItemDefaults.colors(containerColor = groupedItemColor),
                        content = { Text(if (type.archived) stringResource(R.string.name_archived, type.name) else type.name) },
                        trailingContent = {
                            Text(
                                if (type.accountCount == 0) {
                                    stringResource(R.string.account_type_not_in_use)
                                } else {
                                    pluralStringResource(R.plurals.account_type_account_count, type.accountCount, type.accountCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                }
            }

            if (archivedCount > 0) {
                item {
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
    }

    val editing = renaming
    if (adding || editing != null) {
        val nameKey = "type-name"
        AccountTypeNameDialog(
            type = editing,
            existing = types,
            submitting = busy.isBusy(nameKey),
            onSave = { name ->
                busy.run(
                    nameKey,
                    snackbarHostState,
                    successMessage = if (editing != null) typeRenamedMessage else typeAddedMessage,
                    onSuccess = { adding = false; renaming = null },
                ) {
                    if (editing != null) onRename(editing.id, name) else onCreate(name)
                }
            },
            onCancel = { adding = false; renaming = null },
        )
    }
}

/** A type is only its name, so adding one and renaming one are the same single field in a basic dialog. */
@Composable
private fun AccountTypeNameDialog(
    type: AccountType?,
    existing: List<AccountType>,
    submitting: Boolean,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(type?.name ?: "") }
    // Types are unique by name, so a name is checked against the others (a rename may keep its own).
    val form = rememberFormValidation()
    val nameField = form.field(
        "name",
        nameProblem(name, R.string.error_name_taken_account_type) { candidate -> existing.any { it.id != type?.id && it.name == candidate } },
    )

    AlertDialog(
        onDismissRequest = { if (!submitting) onCancel() },
        title = { Text(stringResource(if (type != null) R.string.rename_account_type else R.string.new_account_type_title)) },
        text = {
            YuukaTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.label_name),
                singleLine = true,
                enabled = !submitting,
                field = nameField,
            )
        },
        confirmButton = {
            TextButton(enabled = !submitting && form.valid(nameField), onClick = { onSave(name.trim()) }) {
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(stringResource(if (type != null) R.string.action_save else R.string.action_add))
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !submitting) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountTypesScreenPreview() {
    ScreenPreview {
        AccountTypesScreenContent(AccountTypesUiState(PreviewData.accountTypes))
    }
}
