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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import dev.gavenda.yuuka.ui.common.ConnectedButtonGroup
import dev.gavenda.yuuka.ui.common.DetailTopBar
import dev.gavenda.yuuka.ui.common.ItemPosition
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.groupedItemShape
import dev.gavenda.yuuka.ui.common.positionInGroup
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
    var isAccountSheetOpen by remember { mutableStateOf(false) }
    val currencySearchState = rememberSearchBarState()
    val currencyFieldState = rememberTextFieldState("")

    val couldNotSaveSettingMessage = stringResource(R.string.could_not_save_setting)
    val firstActiveAccountLabel = stringResource(R.string.first_active_account)

    LaunchedEffect(state.displayCurrency, state.budgetMode, state.defaultAccountId) {
        currencyDraft = state.displayCurrency
        budgetModeDraft = state.budgetMode
        defaultAccountDraft = state.defaultAccountId
    }

    // Every choice here is made from a list, so there is nothing to get wrong and nothing to confirm:
    // a pick is written as it is made, the way the theme switches always behaved. The write is local
    // first and cannot fail for want of a network, so only a rejection has anything to say.
    val persist: (String, BudgetMode, String?) -> Unit = { currency, budgetMode, defaultAccountId ->
        scope.launch {
            try {
                viewModel.save(
                    displayCurrency = currency.takeIf { it != state.displayCurrency },
                    budgetMode = budgetMode.takeIf { it != state.budgetMode },
                    defaultAccountId = defaultAccountId,
                    clearDefaultAccount = defaultAccountId == null && state.defaultAccountId != null,
                )
            } catch (e: ApiError) {
                snackbarHostState.showSnackbar(e.message ?: couldNotSaveSettingMessage)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { DetailTopBar(title = stringResource(R.string.destination_settings)) },
        
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Section 1: Currency
                SettingsGroupHeader(title = "Currency")
                SettingsGroupContainer {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                        .clickable { scope.launch { currencySearchState.animateToExpanded() } }
                            .padding(horizontal = 20.dp, vertical = 20.dp),
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
                        onOptionSelected = {
                            budgetModeDraft = it
                            persist(currencyDraft, it, defaultAccountDraft)
                        },
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
    }

    // The currency picker IS the search: it opens already expanded, which is the only state where
    // Material's input field keeps focus (in touch mode the field's focus and the bar's expansion are
    // one and the same — a collapsed bar clears focus, which is why a bar with nothing to expand into
    // never raises the keyboard).
    ExpandedFullScreenSearchBar(
        state = currencySearchState,
        inputField = {
            SearchBarDefaults.InputField(
                textFieldState = currencyFieldState,
                searchBarState = currencySearchState,
                onSearch = {},
                placeholder = { Text("Search currency name or code...") },
                leadingIcon = {
                    IconButton(onClick = { scope.launch { currencySearchState.animateToCollapsed() } }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                trailingIcon = {
                    if (currencyFieldState.text.isNotEmpty()) {
                        IconButton(onClick = { currencyFieldState.clearText() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
            )
        },
    ) {
        val query = currencyFieldState.text.toString()
        val filteredCurrencies = currencies.filter {
            it.displayName.contains(query, ignoreCase = true) || it.currencyCode.contains(query, ignoreCase = true)
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(filteredCurrencies) { index, currency ->
                ExpressiveCurrencyItemRow(
                    currency = currency,
                    isSelected = currency.currencyCode == currencyDraft,
                    position = positionInGroup(index, filteredCurrencies.lastIndex),
                    onClick = {
                        currencyDraft = currency.currencyCode
                        persist(currency.currencyCode, budgetModeDraft, defaultAccountDraft)
                        currencyFieldState.clearText()
                        scope.launch { currencySearchState.animateToCollapsed() }
                    },
                )
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
                                persist(currencyDraft, budgetModeDraft, account.id)
                            })
                    }
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

            // The Material 3 Expressive connected button group, as every other one-of-several choice in
            // the app draws it — segmented buttons are deprecated in its favour.
            ConnectedButtonGroup(
                options = options,
                selected = selectedOption,
                onSelect = onOptionSelected,
                label = { labelProvider(it) },
            )
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
    val shape = groupedItemShape(position)

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
            // Same pairing as the title: `primary` sits a shade from `primaryContainer` and washes out on it.
            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
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
                // A selected row is filled with primaryContainer, so its subtitle has to be read against
                // that: onSurfaceVariant is a light tint meant for the page and all but vanishes here. It
                // is the title's own colour, softened, so it still reads as secondary to it.
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SubtitleAlpha)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.onPrimaryContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
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
                    // On a primaryContainer row the badge has to be the container's own ink, not `primary`
                    // — the two sit a shade apart and the symbol disappears into the fill.
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape
                ), contentAlignment = Alignment.Center
        ) {
            Text(
                text = currency.symbol,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
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
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SubtitleAlpha)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier.size(20.dp).background(MaterialTheme.colorScheme.onPrimaryContainer, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** A selected row's subtitle: the title's own ink, softened enough to read as secondary without losing contrast. */
private const val SubtitleAlpha = 0.75f
