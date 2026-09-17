package dev.gavenda.yuuka.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.data.model.BudgetMode
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.ThemeMode
import dev.gavenda.yuuka.domain.ThemePreference
import dev.gavenda.yuuka.domain.formatMoney
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    themePreference: ThemePreference = koinInject(),
    onSaveStateChange: (enabled: Boolean, saving: Boolean, save: () -> Unit) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val themeMode by themePreference.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by themePreference.dynamicColor.collectAsStateWithLifecycle()
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var currencyDraft by remember { mutableStateOf(state.displayCurrency) }
    var budgetModeDraft by remember { mutableStateOf(state.budgetMode) }
    var defaultAccountDraft by remember { mutableStateOf(state.defaultAccountId) }
    var accountMenuOpen by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

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
                label = { Text("Display currency") },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Used for net worth, the monthly summary and budgets.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Preview", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (isValid) formatMoney(123_456, normalised) else "—", style = MaterialTheme.typography.titleMedium)
            }
        }

        if (currencyDraft.isNotBlank() && !isValid) {
            Text("Use a 3-letter currency code, such as PHP.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Column {
            Text("Appearance", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    themeMode == ThemeMode.system,
                    onClick = { themePreference.setThemeMode(ThemeMode.system) },
                    label = { Text("System", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    themeMode == ThemeMode.light,
                    onClick = { themePreference.setThemeMode(ThemeMode.light) },
                    label = { Text("Light", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    themeMode == ThemeMode.dark,
                    onClick = { themePreference.setThemeMode(ThemeMode.dark) },
                    label = { Text("Dark", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (dynamicColorAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Use wallpaper colors", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = dynamicColor, onCheckedChange = { themePreference.setDynamicColor(it) })
                }
            }
        }

        Column {
            Text("Budget mode", style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    budgetModeDraft == BudgetMode.fixed,
                    onClick = { budgetModeDraft = BudgetMode.fixed },
                    label = { Text("Fixed", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    budgetModeDraft == BudgetMode.monthly,
                    onClick = { budgetModeDraft = BudgetMode.monthly },
                    label = { Text("Monthly", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                if (budgetModeDraft == BudgetMode.fixed) "A category's planned amount applies to every month, until changed again." else "Each month keeps its own planned amount, set separately.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        ExposedDropdownMenuBox(expanded = accountMenuOpen, onExpandedChange = { accountMenuOpen = it }) {
            OutlinedTextField(
                value = state.accounts.firstOrNull { it.id == defaultAccountDraft }?.name ?: "First active account",
                onValueChange = {},
                readOnly = true,
                label = { Text("Default account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountMenuOpen) },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = accountMenuOpen, onDismissRequest = { accountMenuOpen = false }) {
                DropdownMenuItem(text = { Text("First active account") }, onClick = { defaultAccountDraft = null; accountMenuOpen = false })
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
                } catch (e: ApiError) {
                    error = e.message ?: "Could not save the setting."
                } finally {
                    saving = false
                }
            }
        }
    }
}
