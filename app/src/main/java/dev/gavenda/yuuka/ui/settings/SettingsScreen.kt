package dev.gavenda.yuuka.ui.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.BuildConfig
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.BudgetMode
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.ThemeMode
import dev.gavenda.yuuka.domain.ThemePreference
import dev.gavenda.yuuka.domain.isCurrencyCode
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.rememberFormValidation
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.*
import kotlin.text.contains


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
    val snackbarHostState = LocalSnackbarHostState.current

    val themeMode by themePreference.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by themePreference.dynamicColor.collectAsStateWithLifecycle()

    val currencies = remember {
        Currency.getAvailableCurrencies().toList()
    }

    var currencyDraft by remember { mutableStateOf(state.displayCurrency) }
    var budgetModeDraft by remember { mutableStateOf(state.budgetMode) }
    var defaultAccountDraft by remember { mutableStateOf(state.defaultAccountId) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var isCurrencySheetOpen by remember { mutableStateOf(false) }
    var isAccountSheetOpen by remember { mutableStateOf(false) }

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
    val changed =
        normalised != state.displayCurrency || budgetModeDraft != state.budgetMode || defaultAccountDraft != state.defaultAccountId

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Section 1: Currency
            SettingsGroupHeader(title = "Currency")
            SettingsGroupContainer {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .clickable { isCurrencySheetOpen = true }.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AttachMoney,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Display currency", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currencyDraft,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        Currency.getInstance(currencyDraft).getSymbol(LocalLocale.current.platformLocale),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

//
//            Card(modifier = Modifier.fillMaxWidth()) {
//                Column(Modifier.padding(12.dp)) {
//                    Text(stringResource(R.string.preview_label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//                    Text(if (isValid) formatMoney(123_456, normalised) else "—", style = MaterialTheme.typography.titleMedium)
//                }
//            }

            // Section 2: Appearance
            SettingsGroupHeader(title = "Appearance")
            SettingsGroupContainer {
                ExpressiveButtonGroupSettingItem(
                    icon = Icons.Default.DarkMode,
                    title = "App theme",
                    subtitle = "Choose how your app looks",
                    options = ThemeMode.entries,
                    selectedOption = themeMode,
                    onOptionSelected = { themePreference.setThemeMode(it) },
                    labelProvider = {
                        stringResource(
                            when (it) {
                                ThemeMode.system -> R.string.theme_system
                                ThemeMode.light -> R.string.theme_light
                                ThemeMode.dark -> R.string.theme_dark
                            },
                        )
                    },
                    position = ItemPosition.Top
                )
                ExpressiveSwitchSettingItem(
                    icon = Icons.Default.Palette,
                    title = "Dynamic theming",
                    subtitle = "Match system wallpaper colors",
                    checked = dynamicColor,
                    onCheckedChange = { themePreference.setDynamicColor(it) },
                    position = ItemPosition.Bottom
                )
            }

            // Section 3: Budgeting
            SettingsGroupHeader(title = "Budgeting")
            SettingsGroupContainer {
                ExpressiveButtonGroupSettingItem(
                    icon = Icons.Default.NoteAlt,
                    title = "Mode",
                    subtitle = "Choose whether you budget monthly or not",
                    options = BudgetMode.entries,
                    selectedOption = budgetModeDraft,
                    onOptionSelected = { budgetModeDraft = it },
                    labelProvider = { stringResource(if (it == BudgetMode.fixed) R.string.label_fixed else R.string.budget_mode_monthly) },
                    position = ItemPosition.Single
                )
            }

            // Section 4: Transactions
            SettingsGroupHeader(title = "Transactions")
            SettingsGroupContainer {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .clickable { isAccountSheetOpen = true }.padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SupervisorAccount,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.default_account), style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.accounts.firstOrNull { it.id == defaultAccountDraft }?.name
                                ?: firstActiveAccountLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Section 5: About
            SettingsGroupHeader(title = "About")
            SettingsGroupContainer {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.version), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

    }

    // Fullscreen Modal Layer for Search + Navigation Density
    if (isCurrencySheetOpen) {
        val textFieldState = rememberTextFieldState("")
        val searchBarState = rememberSearchBarState()


        ModalBottomSheet(
            onDismissRequest = { isCurrencySheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            dragHandle = { BottomSheetDefaults.DragHandle() }) {
            var searchQuery by remember { mutableStateOf("") }
            val filteredCurrencies = currencies.filter {
                it.displayName.contains(searchQuery, ignoreCase = true) || it.currencyCode.contains(
                    searchQuery, ignoreCase = true
                )
            }

            LaunchedEffect(textFieldState) {
                snapshotFlow { textFieldState.text.toString() }.collectLatest { query ->
                        searchQuery = query
                    }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Select currency",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )

                // 1. M3 Expressive Search Component Integration
                SearchBar(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ), state = searchBarState, inputField = {
                        SearchBarDefaults.InputField(
                            textFieldState = textFieldState,
                            searchBarState = searchBarState,
                            onSearch = {},
                            placeholder = { Text("Search currency name or code...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (textFieldState.text.isNotEmpty()) {
                                    IconButton(onClick = {
                                        // Clear text utilizing the modern TextFieldState API
                                        textFieldState.setTextAndSelectAll("")
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear")
                                    }
                                }
                            })
                    })

                // 2. High-Capacity Scroll Matrix
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(filteredCurrencies) { index, currency ->
                        val isSelected = currency.currencyCode == currencyDraft
                        val position = when {
                            filteredCurrencies.size == 1 -> ItemPosition.Single
                            index == 0 -> ItemPosition.Top
                            index == filteredCurrencies.lastIndex -> ItemPosition.Bottom
                            else -> ItemPosition.Middle
                        }

                        ExpressiveCurrencyItemRow(
                            currency = currency, isSelected = isSelected, position = position, onClick = {
                                currencyDraft = currency.currencyCode
                                isCurrencySheetOpen = false
                            })
                    }
                }
            }
        }
    }

    // 2. High-Capacity Sheet Layer
    if (isAccountSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isAccountSheetOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // Elevated depth background
            dragHandle = { BottomSheetDefaults.DragHandle() }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Select default account",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // LazyColumn isolates rendering to only items visible on screen for performance
                LazyColumn(
                    modifier = Modifier.fillMaxWidth()
                        .weight(1f, fill = false), // Allows sheet to snap size naturally up to a max threshold
                    verticalArrangement = Arrangement.spacedBy(4.dp) // Segmented Gap representation
                ) {
                    itemsIndexed(state.accounts.sortedBy { it.typeName }) { index, account ->
                        val isSelected = account.id == defaultAccountDraft

                        // Dynamic calculations formatting specific container positions
                        val position = when (index) {
                            0 -> ItemPosition.Top
                            state.accounts.lastIndex -> ItemPosition.Bottom
                            else -> ItemPosition.Middle
                        }

                        ExpressiveModalSelectionItem(
                            icon = Icons.Default.AccountCircle,
                            title = account.name,
                            subtitle = account.typeName,
                            isSelected = isSelected,
                            position = position,
                            onClick = {
                                defaultAccountDraft = account.id
                                isAccountSheetOpen = false // Clean automatic dismiss logic on pick
                            })
                    }
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

@Composable
fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsGroupContainer(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(28.dp)
            ), content = content
    )
}

@Composable
fun <T> ExpressiveButtonGroupSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    position: ItemPosition
) {
    // Outer setting block layout structured uniformly with other rows
    SettingItemRow(position = position, onClick = {}, enabled = false) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()
            ) {
                SettingIconAndText(icon, title, subtitle)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // The Segmented Button Row layout
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp).background(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(14.dp) // Subtle curve for modern sub-buttons
                    ).padding(4.dp), // Inner inset track accent
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                options.forEachIndexed { index, option ->
                    val isSelected = option == selectedOption

                    // Smooth structural coloring state shift
                    val containerColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "SelectedBackground"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "SelectedText"
                    )

                    // Individual Button Segment Shape Calculation
                    val segmentShape = when (index) {
                        0 -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 4.dp, bottomEnd = 4.dp)
                        options.lastIndex -> RoundedCornerShape(
                            topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp
                        )

                        else -> RoundedCornerShape(4.dp)
                    }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().clip(segmentShape).background(containerColor)
                            .clickable { onOptionSelected(option) }, contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = labelProvider(option),
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ExpressiveSwitchSettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    position: ItemPosition
) {
    SettingItemRow(position = position, onClick = { onCheckedChange(!checked) }) {
        SettingIconAndText(icon, title, subtitle)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
private fun RowScope.SettingIconAndText(icon: ImageVector, title: String, subtitle: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(24.dp)
    )
    Spacer(modifier = Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingItemRow(
    position: ItemPosition, onClick: () -> Unit, enabled: Boolean = true, content: @Composable RowScope.() -> Unit
) {
    val shape = when (position) {
        ItemPosition.Top -> RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
        ItemPosition.Middle -> RoundedCornerShape(4.dp)
        ItemPosition.Bottom -> RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp, bottomStart = 28.dp, bottomEnd = 28.dp
        )

        ItemPosition.Single -> RoundedCornerShape(28.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun ExpressiveModalSelectionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    position: ItemPosition,
    onClick: () -> Unit
) {
    // Expressive morphing calculations mapping pill sizes
    val cornerRounding = if (isSelected) 24.dp else when (position) {
        ItemPosition.Top, ItemPosition.Bottom -> 20.dp
        ItemPosition.Middle -> 4.dp
        ItemPosition.Single -> 24.dp
    }

    val itemShape = when (position) {
        ItemPosition.Top -> RoundedCornerShape(
            topStart = cornerRounding, topEnd = cornerRounding, bottomStart = 4.dp, bottomEnd = 4.dp
        )

        ItemPosition.Bottom -> RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp, bottomStart = cornerRounding, bottomEnd = cornerRounding
        )

        ItemPosition.Middle -> RoundedCornerShape(cornerRounding)
        ItemPosition.Single -> RoundedCornerShape(24.dp)
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "BgAnim"
    )

    Row(
        modifier = Modifier.fillMaxWidth().clip(itemShape).background(containerColor).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun ExpressiveCurrencyItemRow(
    currency: Currency, isSelected: Boolean, position: ItemPosition, onClick: () -> Unit
) {
    val cornerRounding = if (isSelected) 24.dp else when (position) {
        ItemPosition.Top, ItemPosition.Bottom -> 20.dp
        ItemPosition.Middle -> 4.dp
        ItemPosition.Single -> 24.dp
    }

    val itemShape = when (position) {
        ItemPosition.Top -> RoundedCornerShape(
            topStart = cornerRounding, topEnd = cornerRounding, bottomStart = 4.dp, bottomEnd = 4.dp
        )

        ItemPosition.Bottom -> RoundedCornerShape(
            topStart = 4.dp, topEnd = 4.dp, bottomStart = cornerRounding, bottomEnd = cornerRounding
        )

        ItemPosition.Middle -> RoundedCornerShape(cornerRounding)
        ItemPosition.Single -> RoundedCornerShape(24.dp)
    }

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "BgAnim"
    )

    Row(
        modifier = Modifier.fillMaxWidth().clip(itemShape).background(containerColor).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        // High-emphasis circular badge representing currency character
        Box(
            modifier = Modifier.size(40.dp).background(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape
                ), contentAlignment = Alignment.Center
        ) {
            Text(
                text = currency.symbol,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currency.currencyCode,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = currency.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

enum class ItemPosition { Top, Middle, Bottom, Single }
