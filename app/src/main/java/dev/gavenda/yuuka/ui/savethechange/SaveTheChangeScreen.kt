package dev.gavenda.yuuka.ui.savethechange

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SaveTheChangeScreen(
    modifier: Modifier = Modifier,
    viewModel: SaveTheChangeViewModel = koinViewModel(),
    onSaveStateChange: (enabled: Boolean, saving: Boolean, save: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    var enabledDraft by remember { mutableStateOf(state.enabled) }
    var roundToDraft by remember { mutableLongStateOf(state.roundTo) }
    var destinationDraft by remember { mutableStateOf(state.destinationAccountId) }
    var categoryDraft by remember { mutableStateOf(state.categoryId) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var categoryMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val savedMessage = stringResource(R.string.save_the_change_saved)
    val couldNotSaveMessage = stringResource(R.string.could_not_save_round_up_rule)
    val chooseAnAccountLabel = stringResource(R.string.choose_an_account)
    val uncategorizedLabel = stringResource(R.string.category_uncategorized)

    LaunchedEffect(state.enabled, state.roundTo, state.destinationAccountId, state.categoryId) {
        enabledDraft = state.enabled
        roundToDraft = state.roundTo
        destinationDraft = state.destinationAccountId
        categoryDraft = state.categoryId
    }

    val changed = enabledDraft != state.enabled ||
        roundToDraft != state.roundTo ||
        destinationDraft != state.destinationAccountId ||
        categoryDraft != state.categoryId
    val isValid = !enabledDraft || destinationDraft != null

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.save_the_change_enabled), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.save_the_change_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = enabledDraft, onCheckedChange = { enabledDraft = it })
            }

            Column {
                Text(stringResource(R.string.round_to_label), style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    SegmentedButton(
                        selected = roundToDraft == 1000L,
                        onClick = { roundToDraft = 1000L },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        label = { Text(stringResource(R.string.round_to_ten)) },
                    )
                    SegmentedButton(
                        selected = roundToDraft == 10000L,
                        onClick = { roundToDraft = 10000L },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        label = { Text(stringResource(R.string.round_to_hundred)) },
                    )
                }
            }

            ExposedDropdownMenuBox(expanded = accountMenuOpen, onExpandedChange = { accountMenuOpen = it }) {
                OutlinedTextField(
                    value = state.accounts.firstOrNull { it.id == destinationDraft }?.name ?: chooseAnAccountLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.destination_account)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                    state.accounts.forEach { account ->
                        DropdownMenuItem(text = { Text(account.name) }, onClick = { destinationDraft = account.id; accountMenuOpen = false })
                    }
                }
            }

            val selectedCategoryLabel =
                state.categoryGroups.flatMap { listOf(it.parent) + it.children }.firstOrNull { it.id == categoryDraft }?.name
                    ?: uncategorizedLabel
            ExposedDropdownMenuBox(expanded = categoryMenuOpen, onExpandedChange = { categoryMenuOpen = it }) {
                OutlinedTextField(
                    value = selectedCategoryLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.label_cashflow_category)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = categoryMenuOpen, onDismissRequest = { categoryMenuOpen = false }) {
                    DropdownMenuItem(text = { Text(uncategorizedLabel) }, onClick = { categoryDraft = null; categoryMenuOpen = false })
                    state.categoryGroups.forEach { group ->
                        DropdownMenuItem(text = { Text(group.parent.name) }, onClick = { categoryDraft = group.parent.id; categoryMenuOpen = false })
                        group.children.forEach { child ->
                            DropdownMenuItem(
                                text = { Text("    ${child.name}") },
                                onClick = { categoryDraft = child.id; categoryMenuOpen = false },
                            )
                        }
                    }
                }
            }

            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.save_the_change_accounts_title), style = MaterialTheme.typography.labelMedium)
            state.accounts.forEach { account ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(account.name, modifier = Modifier.weight(1f))
                    Switch(
                        checked = account.roundUpSource,
                        onCheckedChange = { checked ->
                            scope.launch {
                                try {
                                    viewModel.setAccountParticipation(account, checked)
                                } catch (e: ApiError) {
                                    snackbarHostState.showSnackbar(e.message ?: couldNotSaveMessage)
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    SideEffect {
        onSaveStateChange(isValid && changed && !saving, saving) {
            scope.launch {
                saving = true
                error = null
                try {
                    viewModel.save(
                        enabled = enabledDraft,
                        roundTo = roundToDraft,
                        destinationAccountId = destinationDraft,
                        clearDestination = destinationDraft == null && state.destinationAccountId != null,
                        categoryId = categoryDraft,
                        clearCategory = categoryDraft == null && state.categoryId != null,
                    )
                    snackbarHostState.showSnackbar(savedMessage)
                } catch (e: ApiError) {
                    error = e.message ?: couldNotSaveMessage
                    snackbarHostState.showSnackbar(error!!)
                } finally {
                    saving = false
                }
            }
        }
    }
}
