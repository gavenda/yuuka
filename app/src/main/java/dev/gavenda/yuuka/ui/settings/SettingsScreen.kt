package dev.gavenda.yuuka.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.BudgetMode
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.ThemeMode
import dev.gavenda.yuuka.domain.ThemePreference
import dev.gavenda.yuuka.domain.formatMoney
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    themePreference: ThemePreference = koinInject(),
    onSaveStateChange: (enabled: Boolean, saving: Boolean, save: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = LocalSnackbarHostState.current

    val themeMode by themePreference.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by themePreference.dynamicColor.collectAsStateWithLifecycle()
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var currencyDraft by remember { mutableStateOf(state.displayCurrency) }
    var budgetModeDraft by remember { mutableStateOf(state.budgetMode) }
    var defaultAccountDraft by remember { mutableStateOf(state.defaultAccountId) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val settingsSavedMessage = stringResource(R.string.settings_saved)
    val couldNotSaveSettingMessage = stringResource(R.string.could_not_save_setting)
    val firstActiveAccountLabel = stringResource(R.string.first_active_account)

    LaunchedEffect(state.displayCurrency, state.budgetMode, state.defaultAccountId) {
        currencyDraft = state.displayCurrency
        budgetModeDraft = state.budgetMode
        defaultAccountDraft = state.defaultAccountId
    }

    val normalised = currencyDraft.trim().uppercase()
    val isValid = Regex("^[A-Za-z]{3}$").matches(currencyDraft.trim())
    val changed = normalised != state.displayCurrency || budgetModeDraft != state.budgetMode || defaultAccountDraft != state.defaultAccountId

    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column {
            OutlinedTextField(
                value = currencyDraft,
                onValueChange = { currencyDraft = it },
                label = { Text(stringResource(R.string.display_currency)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.display_currency_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(stringResource(R.string.preview_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (isValid) formatMoney(123_456, normalised) else "—", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (currencyDraft.isNotBlank() && !isValid) {
            Text(stringResource(R.string.currency_hint_3letter), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Column {
            Text(stringResource(R.string.appearance), style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    themeMode == ThemeMode.system,
                    onClick = { themePreference.setThemeMode(ThemeMode.system) },
                    label = { Text(stringResource(R.string.theme_system), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    themeMode == ThemeMode.light,
                    onClick = { themePreference.setThemeMode(ThemeMode.light) },
                    label = { Text(stringResource(R.string.theme_light), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    themeMode == ThemeMode.dark,
                    onClick = { themePreference.setThemeMode(ThemeMode.dark) },
                    label = { Text(stringResource(R.string.theme_dark), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (dynamicColorAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.use_wallpaper_colors), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = dynamicColor, onCheckedChange = { themePreference.setDynamicColor(it) })
                }
            }
        }

        Column {
            Text(stringResource(R.string.budget_mode_label), style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    budgetModeDraft == BudgetMode.fixed,
                    onClick = { budgetModeDraft = BudgetMode.fixed },
                    label = { Text(stringResource(R.string.label_fixed), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    budgetModeDraft == BudgetMode.monthly,
                    onClick = { budgetModeDraft = BudgetMode.monthly },
                    label = { Text(stringResource(R.string.budget_mode_monthly), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (budgetModeDraft == BudgetMode.fixed) stringResource(R.string.budget_mode_fixed_hint) else stringResource(R.string.budget_mode_monthly_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ExposedDropdownMenuBox(expanded = accountMenuOpen, onExpandedChange = { accountMenuOpen = it }) {
            OutlinedTextField(
                value = state.accounts.firstOrNull { it.id == defaultAccountDraft }?.name ?: firstActiveAccountLabel,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.default_account)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                DropdownMenuItem(text = { Text(firstActiveAccountLabel) }, onClick = { defaultAccountDraft = null; accountMenuOpen = false })
                state.accounts.forEach { account ->
                    DropdownMenuItem(text = { Text(account.name) }, onClick = { defaultAccountDraft = account.id; accountMenuOpen = false })
                }
            }
        }

        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
    }

    SideEffect {
        onSaveStateChange(isValid && changed && !saving, saving) {
            scope.launch {
                saving = true
                error = null
                try {
                    viewModel.save(
                        displayCurrency = normalised.takeIf { it != state.displayCurrency },
                        budgetMode = budgetModeDraft.takeIf { it != state.budgetMode },
                        defaultAccountId = defaultAccountDraft,
                        clearDefaultAccount = defaultAccountDraft == null && state.defaultAccountId != null,
                    )
                    snackbarHostState.showSnackbar(settingsSavedMessage)
                } catch (e: ApiError) {
                    error = e.message ?: couldNotSaveSettingMessage
                    snackbarHostState.showSnackbar(error!!)
                } finally {
                    saving = false
                }
            }
        }
    }
}
