package dev.gavenda.yuuka.ui.savethechange

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.ui.common.DetailTopBar
import dev.gavenda.yuuka.ui.common.FieldError
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.rememberFormValidation
import dev.gavenda.yuuka.ui.settings.ExpressiveButtonGroupSettingItem
import dev.gavenda.yuuka.ui.settings.ExpressiveModalSelectionItem
import dev.gavenda.yuuka.ui.settings.ExpressiveSwitchSettingItem
import dev.gavenda.yuuka.ui.common.ItemPosition
import dev.gavenda.yuuka.ui.common.groupedItemShape
import dev.gavenda.yuuka.ui.common.positionInGroup
import dev.gavenda.yuuka.ui.settings.SettingsGroupContainer
import dev.gavenda.yuuka.ui.settings.SettingsGroupHeader
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveTheChangeScreen(
    modifier: Modifier = Modifier,
    viewModel: SaveTheChangeViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    var enabledDraft by remember { mutableStateOf(state.enabled) }
    var roundToDraft by remember { mutableLongStateOf(state.roundTo) }
    var destinationDraft by remember { mutableStateOf(state.destinationAccountId) }
    var categoryDraft by remember { mutableStateOf(state.categoryId) }
    var isAccountSheetOpen by remember { mutableStateOf(false) }
    var isSourceSheetOpen by remember { mutableStateOf(false) }
    var isCategorySheetOpen by remember { mutableStateOf(false) }

    val couldNotSaveMessage = stringResource(R.string.could_not_save_round_up_rule)
    val couldNotSaveAccountMessage = stringResource(R.string.could_not_save_account)
    val chooseAnAccountLabel = stringResource(R.string.choose_an_account)
    val uncategorizedLabel = stringResource(R.string.category_uncategorized)

    LaunchedEffect(state.enabled, state.roundTo, state.destinationAccountId, state.categoryId) {
        enabledDraft = state.enabled
        roundToDraft = state.roundTo
        destinationDraft = state.destinationAccountId
        categoryDraft = state.categoryId
    }

    // A destination is required once the rule is on; turning it on without one is the mistake, so it is said at once.
    val form = rememberFormValidation()
    val destinationField = form.field(
        "destination",
        if (enabledDraft && state.accounts.none { it.id == destinationDraft }) stringResource(R.string.error_choose_destination) else null,
    )

    // There is no Save: every choice is written as it is made. The one thing the rule cannot do without
    // is somewhere to put the change, so a rule switched on before a destination is picked waits on the
    // screen — the error beside the field — and is written the moment an account is chosen.
    val persist: (Boolean, Long, String?, String?) -> Unit = { enabled, roundTo, destination, category ->
        if (!enabled || state.accounts.any { it.id == destination }) {
            scope.launch {
                try {
                    viewModel.save(
                        enabled = enabled,
                        roundTo = roundTo,
                        destinationAccountId = destination,
                        clearDestination = destination == null && state.destinationAccountId != null,
                        categoryId = category,
                        clearCategory = category == null && state.categoryId != null,
                    )
                } catch (e: ApiError) {
                    snackbarHostState.showSnackbar(e.message ?: couldNotSaveMessage)
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { DetailTopBar(title = stringResource(R.string.destination_save_the_change)) },
        
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Section 1: Round-ups
                SettingsGroupHeader(title = "Round-ups")
                SettingsGroupContainer {
                    ExpressiveSwitchSettingItem(
                        icon = Icons.Default.Savings,
                        title = stringResource(R.string.save_the_change_enabled),
                        subtitle = stringResource(R.string.save_the_change_description),
                        checked = enabledDraft,
                        onCheckedChange = {
                            enabledDraft = it
                            if (it) destinationField.touch()
                            persist(it, roundToDraft, destinationDraft, categoryDraft)
                        },
                        position = ItemPosition.Top,
                    )
                    ExpressiveButtonGroupSettingItem(
                        icon = Icons.Default.Calculate,
                        title = stringResource(R.string.round_to_label),
                        subtitle = "How far each purchase is rounded up",
                        options = listOf(1000L, 10000L),
                        selectedOption = roundToDraft,
                        onOptionSelected = {
                            roundToDraft = it
                            persist(enabledDraft, it, destinationDraft, categoryDraft)
                        },
                        labelProvider = { stringResource(if (it == 1000L) R.string.round_to_ten else R.string.round_to_hundred) },
                        position = ItemPosition.Bottom,
                    )
                }

                // Section 2: Accounts — which ones round up, and the one the change lands in.
                val sourceAccounts = state.accounts.filter { it.roundUpSource }
                SettingsGroupHeader(title = "Accounts")
                SettingsGroupContainer {
                    SelectionSettingRow(
                        icon = Icons.Default.Wallet,
                        title = stringResource(R.string.round_up_purchases_account_label),
                        // A couple of names read better than a count; a longer list would be a wall of text, so it is counted instead.
                        value = when {
                            sourceAccounts.isEmpty() -> stringResource(R.string.save_the_change_no_accounts)
                            sourceAccounts.size <= 2 -> sourceAccounts.joinToString { it.name }
                            else -> pluralStringResource(R.plurals.round_up_accounts_count, sourceAccounts.size, sourceAccounts.size)
                        },
                        position = ItemPosition.Top,
                        onClick = { isSourceSheetOpen = true },
                    )
                    SelectionSettingRow(
                        icon = Icons.Default.AccountBalanceWallet,
                        title = stringResource(R.string.destination_account),
                        value = state.accounts.firstOrNull { it.id == destinationDraft }?.name ?: chooseAnAccountLabel,
                        position = ItemPosition.Bottom,
                        onClick = {
                            destinationField.touch()
                            isAccountSheetOpen = true
                        },
                    )
                }
                FieldError(destinationField.error, modifier = Modifier.padding(horizontal = 8.dp))

                // Section 3: Category
                val selectedCategoryLabel =
                    state.categoryGroups.flatMap { listOf(it.parent) + it.children }.firstOrNull { it.id == categoryDraft }?.name
                        ?: uncategorizedLabel
                SettingsGroupHeader(title = "Category")
                SettingsGroupContainer {
                    SelectionSettingRow(
                        icon = Icons.Default.Category,
                        title = stringResource(R.string.label_cashflow_category),
                        value = selectedCategoryLabel,
                        position = ItemPosition.Single,
                        onClick = { isCategorySheetOpen = true },
                    )
                }
            }
        }
    }

    if (isAccountSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isAccountSheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.destination_account),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(state.accounts) { index, account ->
                        ExpressiveModalSelectionItem(
                            icon = Icons.Default.AccountCircle,
                            title = account.name,
                            subtitle = account.typeName,
                            isSelected = account.id == destinationDraft,
                            position = positionInGroup(index, state.accounts.lastIndex),
                            onClick = {
                                destinationDraft = account.id
                                isAccountSheetOpen = false
                                persist(enabledDraft, roundToDraft, account.id, categoryDraft)
                            },
                        )
                    }
                }
            }
        }
    }

    // Several accounts can round up at once, so this sheet toggles rather than picks: each tap writes that
    // one account's opt-in and the sheet stays open for the next.
    if (isSourceSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isSourceSheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.save_the_change_choose_accounts),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(state.accounts) { index, account ->
                        ExpressiveModalSelectionItem(
                            icon = Icons.Default.AccountCircle,
                            title = account.name,
                            subtitle = account.typeName,
                            isSelected = account.roundUpSource,
                            position = positionInGroup(index, state.accounts.lastIndex),
                            onClick = {
                                scope.launch {
                                    try {
                                        viewModel.setRoundUpSource(account.id, !account.roundUpSource)
                                    } catch (e: ApiError) {
                                        snackbarHostState.showSnackbar(e.message ?: couldNotSaveAccountMessage)
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (isCategorySheetOpen) {
        // Uncategorised is an option of its own, ahead of the cashflow tree a transfer offers.
        val categoryRows = remember(state.categoryGroups, uncategorizedLabel) {
            listOf(CategoryRow(null, uncategorizedLabel, null)) +
                state.categoryGroups.flatMap { group ->
                    listOf(CategoryRow(group.parent.id, group.parent.name, null)) +
                        group.children.map { CategoryRow(it.id, it.name, group.parent.name) }
                }
        }

        ModalBottomSheet(
            onDismissRequest = { isCategorySheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle() },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_cashflow_category),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(categoryRows) { index, row ->
                        ExpressiveModalSelectionItem(
                            icon = if (row.id == null) Icons.Default.Block else Icons.Default.Category,
                            title = row.name,
                            subtitle = row.parentName,
                            isSelected = row.id == categoryDraft,
                            position = positionInGroup(index, categoryRows.lastIndex),
                            onClick = {
                                categoryDraft = row.id
                                isCategorySheetOpen = false
                                persist(enabledDraft, roundToDraft, destinationDraft, row.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

/** A settings row that says what is chosen and opens a sheet to change it — the shape Settings uses for its currency and default account. */
@Composable
private fun SelectionSettingRow(
    icon: ImageVector,
    title: String,
    value: String,
    position: ItemPosition,
    onClick: () -> Unit,
) {
    val shape = groupedItemShape(position)

    Row(
        modifier = Modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** One pickable category in the sheet — a child carries its parent's name as its subtitle, standing in for the indent a list gave it. */
private data class CategoryRow(val id: String?, val name: String, val parentName: String?)

