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
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
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
import dev.gavenda.yuuka.ui.common.FullScreenDialog
import dev.gavenda.yuuka.ui.common.ItemPosition
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.groupedItemShape
import dev.gavenda.yuuka.ui.common.positionInGroup
import dev.gavenda.yuuka.ui.theme.ShapeXl
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import java.util.*
import kotlin.text.contains
import dev.gavenda.yuuka.ui.common.PreviewData
import dev.gavenda.yuuka.ui.common.ScreenPreview
import androidx.compose.ui.tooling.preview.Preview


/** The default currency and nine of the most traded: what the display-currency list offers before a search. */
private val COMMON_CURRENCIES = listOf("PHP", "USD", "EUR", "JPY", "GBP", "CNY", "AUD", "CAD", "SGD", "HKD")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel(),
    themePreference: ThemePreference = koinInject(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreenContent(state = state, modifier = modifier, themePreference = themePreference, onSave = viewModel::save)
}

/** The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreenContent(
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    themePreference: ThemePreference = koinInject(),
    onSave: suspend (displayCurrency: String?, budgetMode: BudgetMode?, defaultAccountId: String?, clearDefaultAccount: Boolean) -> Unit = { _, _, _, _ -> },
) {
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
    var isCurrencySheetOpen by remember { mutableStateOf(false) }
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
                onSave(
                    currency.takeIf { it != state.displayCurrency },
                    budgetMode.takeIf { it != state.budgetMode },
                    defaultAccountId,
                    defaultAccountId == null && state.defaultAccountId != null,
                )
            } catch (e: ApiError) {
                snackbarHostState.showSnackbar(e.message ?: couldNotSaveSettingMessage)
            }
        }
    }

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
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
                        modifier = Modifier.fillMaxWidth().clip(ShapeXl)
                        .clickable { isCurrencySheetOpen = true }
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AttachMoney,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Display currency", style = MaterialTheme.typography.titleMedium)
                            Text(
                                currencyDraft,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            Currency.getInstance(currencyDraft).getSymbol(LocalLocale.current.platformLocale),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                }

//
//            Card(modifier = Modifier.fillMaxWidth()) {
//                Column(Modifier.padding(12.dp)) {
//                    Text(stringResource(R.string.preview_label), style = MaterialTheme.typography.labelSmall)
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
                        modifier = Modifier.fillMaxWidth().clip(ShapeXl)
                        .clickable { isAccountSheetOpen = true }.padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.SupervisorAccount,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.default_account), style = MaterialTheme.typography.titleMedium)
                            Text(
                                state.accounts.firstOrNull { it.id == defaultAccountDraft }?.name
                                    ?: firstActiveAccountLabel,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                // Section 5: About
                SettingsGroupHeader(title = "About")
                SettingsGroupContainer {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(ShapeXl)
                            .padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info, contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.version), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

        }
    }

    // Every currency there is, behind a search: a list that long, with the keyboard up, wants the whole screen.
    // Its first row is drawn like a search bar; it is a plain text field, not Material's SearchBar.
    if (isCurrencySheetOpen) {
        val closeCurrencySheet = {
            currencyFieldState.clearText()
            isCurrencySheetOpen = false
        }
        val searchFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) { searchFocus.requestFocus() }
        // Tapping a row only marks it. Changing the currency relabels every figure in the app, so it waits for Save.
        var pickedCurrency by remember { mutableStateOf(currencyDraft) }

        FullScreenDialog(
            title = stringResource(R.string.display_currency),
            onDismiss = closeCurrencySheet,
            onSave = {
                currencyDraft = pickedCurrency
                persist(pickedCurrency, budgetModeDraft, defaultAccountDraft)
                closeCurrencySheet()
            },
            saveEnabled = pickedCurrency != currencyDraft,
            dirty = pickedCurrency != currencyDraft,
            scrollable = false,
        ) {
            TextField(
                state = currencyFieldState,
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus),
                lineLimits = TextFieldLineLimits.SingleLine,
                shape = CircleShape,
                placeholder = { Text("Search currency name or code...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (currencyFieldState.text.isNotEmpty()) {
                        IconButton(onClick = { currencyFieldState.clearText() }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear))
                        }
                    }
                },
            )

            val query = currencyFieldState.text.toString()
            // Before anything is typed the list is the ten currencies most people would pick from, not the three
            // hundred the platform knows; a search still reaches every one of them. The currency in use leads
            // either list — and joins the short one if it is not among the ten — so it is the first thing seen.
            val filteredCurrencies = remember(query, currencyDraft) {
                val shown = if (query.isBlank()) {
                    (listOf(currencyDraft) + COMMON_CURRENCIES).distinct().mapNotNull { code -> currencies.firstOrNull { it.currencyCode == code } }
                } else {
                    currencies.filter {
                        it.displayName.contains(query, ignoreCase = true) || it.currencyCode.contains(query, ignoreCase = true)
                    }
                }
                shown.sortedByDescending { it.currencyCode == currencyDraft }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(filteredCurrencies, key = { _, currency -> currency.currencyCode }) { index, currency ->
                    ExpressiveCurrencyItemRow(
                        currency = currency,
                        isSelected = currency.currencyCode == pickedCurrency,
                        position = positionInGroup(index, filteredCurrencies.lastIndex),
                        onClick = { pickedCurrency = currency.currencyCode },
                    )
                }
            }
        }
    }

    // 2. High-Capacity Sheet Layer
    if (isAccountSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { isAccountSheetOpen = false },
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
                color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = ShapeXl
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
        )
    }
}

@Composable
private fun RowScope.SettingIconAndText(icon: ImageVector, title: String, subtitle: String) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(24.dp)
    )
    Spacer(modifier = Modifier.width(16.dp))
    Column(modifier = Modifier.weight(1f)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
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
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (subtitle != null) Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
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
    // The row's ink follows its fill, and the badge and the tick are that pair the other way round — left to
    // inherit, all three kept the page's ink and the symbol and the tick vanished into their own circles.
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val badgeContentColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier.fillMaxWidth().clip(itemShape).background(containerColor).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        // High-emphasis circular badge representing currency character
        Box(
            modifier = Modifier.size(40.dp).background(
                    // On a primaryContainer row the badge has to be the container's own ink, not `primary`
                    // — the two sit a shade apart and the badge disappears into the row.
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape
                ), contentAlignment = Alignment.Center
        ) {
            Text(
                text = currency.symbol,
                style = MaterialTheme.typography.titleMedium,
                color = badgeContentColor,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currency.currencyCode,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor,
            )
            Text(
                text = currency.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
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
                    modifier = Modifier.size(14.dp),
                    tint = badgeContentColor,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ScreenPreview {
        SettingsScreenContent(SettingsUiState(defaultAccountId = "acc_checking", accounts = PreviewData.accounts))
    }
}
