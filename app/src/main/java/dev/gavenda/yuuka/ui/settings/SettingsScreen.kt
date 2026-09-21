package dev.gavenda.yuuka.ui.settings

import dev.gavenda.yuuka.ui.common.SelectField
import dev.gavenda.yuuka.ui.common.yuukaCardColors
import dev.gavenda.yuuka.ui.common.SelectOption
import android.os.Build
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
import dev.gavenda.yuuka.BuildConfig
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.BudgetMode
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.ThemeMode
import dev.gavenda.yuuka.domain.ThemePreference
import dev.gavenda.yuuka.domain.formatMoney
import dev.gavenda.yuuka.domain.isCurrencyCode
import dev.gavenda.yuuka.ui.common.ConnectedButtonGroup
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.YuukaSwitch
import dev.gavenda.yuuka.ui.common.YuukaTextField
import dev.gavenda.yuuka.ui.common.rememberFormValidation
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
    val isValid = isCurrencyCode(currencyDraft)
    val form = rememberFormValidation()
    val currencyField = form.field(
        "currency",
        when {
            currencyDraft.isBlank() -> stringResource(R.string.error_currency_required)
            !isValid -> stringResource(R.string.currency_hint_3letter)
            else -> null
        },
    )
    val changed = normalised != state.displayCurrency || budgetModeDraft != state.budgetMode || defaultAccountDraft != state.defaultAccountId

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            YuukaTextField(
                value = currencyDraft,
                onValueChange = { currencyDraft = it },
                label = stringResource(R.string.display_currency),
                singleLine = true,
                field = currencyField,
                hint = stringResource(R.string.display_currency_hint),
            )

            Card(colors = yuukaCardColors(), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(stringResource(R.string.preview_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (isValid) formatMoney(123_456, normalised) else "—", style = MaterialTheme.typography.titleMedium)
                }
            }

            Column {
                Text(stringResource(R.string.appearance), style = MaterialTheme.typography.labelMedium)
                ConnectedButtonGroup(
                    options = ThemeMode.entries,
                    selected = themeMode,
                    onSelect = { themePreference.setThemeMode(it) },
                    label = {
                        stringResource(
                            when (it) {
                                ThemeMode.system -> R.string.theme_system
                                ThemeMode.light -> R.string.theme_light
                                ThemeMode.dark -> R.string.theme_dark
                            },
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (dynamicColorAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.use_wallpaper_colors), style = MaterialTheme.typography.bodyMedium)
                        YuukaSwitch(checked = dynamicColor, onCheckedChange = { themePreference.setDynamicColor(it) })
                    }
                }
            }

            Column {
                Text(stringResource(R.string.budget_mode_label), style = MaterialTheme.typography.labelMedium)
                ConnectedButtonGroup(
                    options = BudgetMode.entries,
                    selected = budgetModeDraft,
                    onSelect = { budgetModeDraft = it },
                    label = { stringResource(if (it == BudgetMode.fixed) R.string.label_fixed else R.string.budget_mode_monthly) },
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    if (budgetModeDraft == BudgetMode.fixed) stringResource(R.string.budget_mode_fixed_hint) else stringResource(R.string.budget_mode_monthly_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SelectField(
            label = stringResource(R.string.default_account),
            value = state.accounts.firstOrNull { it.id == defaultAccountDraft }?.name ?: firstActiveAccountLabel,
            options = listOf(SelectOption<String?>(null, firstActiveAccountLabel)) + state.accounts.map { SelectOption<String?>(it.id, it.name) },
            selected = defaultAccountDraft,
            onSelect = { defaultAccountDraft = it },
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

            HorizontalDivider()

            Column {
                Text(stringResource(R.string.about), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(stringResource(R.string.version), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    SideEffect {
        onSaveStateChange(changed && form.valid(currencyField) && !saving, saving) {
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
