package dev.gavenda.yuuka.ui.savethechange

import dev.gavenda.yuuka.ui.common.SelectField
import dev.gavenda.yuuka.ui.common.SelectOption
import dev.gavenda.yuuka.ui.common.categoryOptions
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
import dev.gavenda.yuuka.ui.common.ConnectedButtonGroup
import dev.gavenda.yuuka.ui.common.FieldError
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.YuukaSwitch
import dev.gavenda.yuuka.ui.common.rememberFormValidation
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
    // A destination is required once the rule is on; turning it on without one is the mistake, so it is said at once.
    val form = rememberFormValidation()
    val destinationField = form.field(
        "destination",
        if (enabledDraft && state.accounts.none { it.id == destinationDraft }) stringResource(R.string.error_choose_destination) else null,
    )

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    YuukaSwitch(
                        checked = enabledDraft,
                        onCheckedChange = {
                            enabledDraft = it
                            if (it) destinationField.touch()
                        },
                    )
                }

                Column {
                    Text(stringResource(R.string.round_to_label), style = MaterialTheme.typography.labelMedium)
                    ConnectedButtonGroup(
                        options = listOf(1000L, 10000L),
                        selected = roundToDraft,
                        onSelect = { roundToDraft = it },
                        label = { stringResource(if (it == 1000L) R.string.round_to_ten else R.string.round_to_hundred) },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            SelectField(
                label = stringResource(R.string.destination_account),
                value = state.accounts.firstOrNull { it.id == destinationDraft }?.name ?: chooseAnAccountLabel,
                options = state.accounts.map { SelectOption<String?>(it.id, it.name) },
                selected = destinationDraft,
                onSelect = { destinationDraft = it },
            )
            FieldError(destinationField.error, modifier = Modifier.padding(horizontal = 16.dp))

            val selectedCategoryLabel =
                state.categoryGroups.flatMap { listOf(it.parent) + it.children }.firstOrNull { it.id == categoryDraft }?.name
                    ?: uncategorizedLabel
            SelectField(
                label = stringResource(R.string.label_cashflow_category),
                value = selectedCategoryLabel,
                options = categoryOptions(state.categoryGroups, uncategorizedLabel),
                selected = categoryDraft,
                onSelect = { categoryDraft = it },
            )

            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

                // Which accounts take part is a per-account choice, made on the account's own form rather than listed here.
                Text(
                    stringResource(R.string.save_the_change_accounts_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    SideEffect {
        onSaveStateChange(changed && form.valid(destinationField) && !saving, saving) {
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
