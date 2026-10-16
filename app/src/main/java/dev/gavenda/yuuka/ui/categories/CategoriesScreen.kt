package dev.gavenda.yuuka.ui.categories

import androidx.compose.ui.res.painterResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.domain.PALETTE
import dev.gavenda.yuuka.domain.isHexColour
import dev.gavenda.yuuka.ui.common.*
import dev.gavenda.yuuka.ui.settings.SettingsGroupHeader
import org.koin.compose.viewmodel.koinViewModel
import androidx.core.graphics.toColorInt
import dev.gavenda.yuuka.domain.nextColor
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun CategoriesScreen(modifier: Modifier = Modifier, viewModel: CategoriesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CategoriesScreenContent(
        state = state,
        modifier = modifier,
        onToggleShowArchived = viewModel::toggleShowArchived,
        onCreateCategory = viewModel::createCategory,
        onUpdateCategory = viewModel::updateCategory,
        onSetArchived = viewModel::setArchived,
        onDeleteCategory = viewModel::deleteCategory,
    )
}

/** The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model. */
@Composable
internal fun CategoriesScreenContent(
    state: CategoriesUiState,
    modifier: Modifier = Modifier,
    onToggleShowArchived: () -> Unit = {},
    onCreateCategory: suspend (name: String, kind: CategoryKind, color: String, parentId: String?) -> Unit = { _, _, _, _ -> },
    onUpdateCategory: suspend (id: String, name: String, kind: CategoryKind, color: String) -> Unit = { _, _, _, _ -> },
    onSetArchived: suspend (id: String, archived: Boolean) -> Unit = { _, _ -> },
    onDeleteCategory: suspend (id: String) -> Unit = {},
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    var creatingIn by remember { mutableStateOf<CategorySection?>(null) }
    var creatingParentId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Category?>(null) }
    // One family shows its subcategories at a time; opening another closes it, and every family starts closed.
    var expandedFamily by rememberSaveable { mutableStateOf<String?>(null) }

    val categoryRestoredMessage = stringResource(R.string.category_restored)
    val categoryArchivedMessage = stringResource(R.string.category_archived)
    val categoryDeletedMessage = stringResource(R.string.category_deleted)
    val categoryUpdatedMessage = stringResource(R.string.category_updated)
    val categoryAddedMessage = stringResource(R.string.category_added)


    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = { ScreenTopBar(stringResource(R.string.destination_categories), actions = {}) },
        
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_category),
                icon = R.drawable.add_48px,
                onClick = { creatingIn = state.sections.first(); creatingParentId = null },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            // Sections sit apart the way the settings groups do: a heading, then one card of rows.
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            // Rows a hair apart, the way a settings group is drawn; the space between sections is the heading's own.
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            if (state.categories.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(R.string.categories_empty_title),
                        YuukaMood.Neutral,
                        description = stringResource(R.string.categories_empty_description)
                    )
                }
            }

            state.sections.forEachIndexed { sectionIndex, section ->
                // Drawn as the settings screen draws its groups: the heading in the accent colour, then the section's
                // rows as one segmented list. Each row is an item of its own, so the list only composes what is on show.
                item(key = "section:${section.key}") {
                    // The list spaces rows a hair apart; a section stands 16dp from the one above and from its heading.
                    Box(
                        modifier = Modifier.animateItem().padding(
                            top = if (sectionIndex > 0 || state.categories.isEmpty()) SectionGap else 0.dp,
                            bottom = SectionGap,
                        ),
                    ) {
                        SettingsGroupHeader(title = "${section.title} (${section.families.size})")
                    }
                }
                if (section.families.isEmpty()) {
                    item(key = "empty:${section.key}") {
                        GroupedItem(position = ItemPosition.Single, modifier = Modifier.animateItem().fillMaxWidth()) {
                            Text(
                                stringResource(R.string.no_section_categories_yet, section.title.lowercase()),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
                section.families.forEachIndexed { familyIndex, family ->
                    val familyExpanded = expandedFamily == family.parent.id
                    val lastFamily = familyIndex == section.families.lastIndex
                    item(key = family.parent.id) {
                        val archiveKey = "archive:${family.parent.id}"
                        val deleteKey = "delete:${family.parent.id}"
                        CategoryRow(
                            family.parent,
                            // The list runs on through a family's open subcategories, so its parent ends it only while they are shut.
                            position = positionInGroup(
                                first = familyIndex == 0,
                                last = lastFamily && !(familyExpanded && family.children.isNotEmpty()),
                            ),
                            modifier = Modifier.animateItem(),
                            subCount = family.children.size,
                            expanded = familyExpanded,
                            archiving = busy.isBusy(archiveKey),
                            deleting = busy.isBusy(deleteKey),
                            onClick = {
                                if (family.children.isNotEmpty()) {
                                    expandedFamily = if (familyExpanded) null else family.parent.id
                                }
                            },
                            onEdit = { editing = family.parent },
                            onArchive = {
                                busy.run(
                                    archiveKey,
                                    snackbarHostState,
                                    successMessage = if (family.parent.archived) categoryRestoredMessage else categoryArchivedMessage,
                                ) { onSetArchived(family.parent.id, !family.parent.archived) }
                            },
                            onDelete = {
                                busy.run(
                                    deleteKey,
                                    snackbarHostState,
                                    successMessage = categoryDeletedMessage
                                ) {
                                    onDeleteCategory(family.parent.id)
                                }
                            },
                            onAddSub = { creatingIn = section; creatingParentId = family.parent.id },
                        )
                    }
                    // An open family's subcategories are rows of the list too; they come and go with the item animation.
                    if (familyExpanded) {
                        itemsIndexed(family.children, key = { _, child -> child.id }) { childIndex, child ->
                            val childArchiveKey = "archive:${child.id}"
                            val childDeleteKey = "delete:${child.id}"
                            CategoryRow(
                                child,
                                position = positionInGroup(
                                    first = false,
                                    last = lastFamily && childIndex == family.children.lastIndex,
                                ),
                                modifier = Modifier.animateItem(),
                                indent = true,
                                archiving = busy.isBusy(childArchiveKey),
                                deleting = busy.isBusy(childDeleteKey),
                                onClick = { editing = child },
                                onEdit = { editing = child },
                                onArchive = {
                                    busy.run(
                                        childArchiveKey,
                                        snackbarHostState,
                                        successMessage = if (child.archived) categoryRestoredMessage else categoryArchivedMessage,
                                    ) { onSetArchived(child.id, !child.archived) }
                                },
                                onDelete = {
                                    busy.run(
                                        childDeleteKey,
                                        snackbarHostState,
                                        successMessage = categoryDeletedMessage
                                    ) {
                                        onDeleteCategory(child.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (state.archivedCount > 0) {
                item(key = "archived-toggle") {
                    TextButton(onClick = onToggleShowArchived, modifier = Modifier.animateItem().padding(top = SectionGap)) {
                        Text(
                            stringResource(
                                if (state.showArchived) R.string.archived_toggle_hide else R.string.archived_toggle_show,
                                state.archivedCount,
                            ),
                        )
                    }
                }
            }
        }
    }

    val section = creatingIn
    if (section != null || editing != null) {
        val formKey = "category-form"
        val submitting = busy.isBusy(formKey)
        CategoryFormContent(
            sections = state.sections,
            initialSection = section ?: state.sections.first(),
            parentId = creatingParentId,
            editing = editing,
            existing = state.categories,
            submitting = submitting,
            parentOptionsFor = { section -> state.categories.filter { it.parentId == null && !it.archived && it.kind == section.kind } },
            nextColorFor = { section -> nextColor(state.categories.count { it.kind == section.kind }) },
            onSave = { name, kind, color ->
                busy.run(
                    formKey,
                    snackbarHostState,
                    successMessage = if (editing != null) categoryUpdatedMessage else categoryAddedMessage,
                    onSuccess = { creatingIn = null; editing = null },
                ) {
                    if (editing != null) onUpdateCategory(editing!!.id, name, kind, color)
                    else onCreateCategory(name, kind, color, creatingParentId)
                }
            },
            onCancel = { creatingIn = null; editing = null },
        )
    }
}

/**
 * One row of a section's card. A parent with subcategories opens and closes them on tap; editing is on the
 * swipe, beside archive and delete. A subcategory's tap edits it. The row paints the card's own colour, which is
 * what keeps the swipe's actions hidden behind it until it is dragged.
 */
/** What a section stands apart by: from the one above, and from its own heading, on top of the list's hair gap. */
private val SectionGap = 16.dp - SegmentedGap

@Composable
private fun CategoryRow(
    category: Category,
    position: ItemPosition,
    onClick: () -> Unit,
    subCount: Int = 0,
    expanded: Boolean = false,
    indent: Boolean = false,
    archiving: Boolean = false,
    deleting: Boolean = false,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onAddSub: (() -> Unit)? = null,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "family-chevron",
    )
    SwipeToRevealActions(
        modifier = modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(ActionIcon.EDIT, stringResource(R.string.cd_rename_item, category.name), onEdit)
            ActionIconButton(
                if (category.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE,
                stringResource(R.string.cd_archive_item, category.name),
                onArchive,
                loading = archiving,
            )
            ActionIconButton(
                ActionIcon.DELETE,
                stringResource(R.string.cd_delete_item, category.name),
                onDelete,
                danger = true,
                loading = deleting
            )
        },
    ) {
        ListItem(
            // A subcategory stands as tall as the parent above it, which carries a count beneath its name.
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            shapes = groupedListItemShapes(position),
            // An open family, parent and subcategories alike, is told apart by its colour rather than by indenting.
            colors = if (expanded || indent) {
                ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    leadingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    supportingContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                ListItemDefaults.colors(containerColor = groupedItemColor)
            },
            leadingContent = {
                Icon(
                    painterResource(R.drawable.label_fill_48px),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = harmonisedColor(category.color, MaterialTheme.colorScheme.onSurfaceVariant),
                )
            },
            supportingContent = if (subCount > 0) {
                { Text(pluralStringResource(R.plurals.subcategories_count, subCount, subCount)) }
            } else {
                null
            },
            trailingContent = if (onAddSub != null || subCount > 0) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onAddSub != null) IconButton(onClick = onAddSub) {
                            Icon(painterResource(R.drawable.add_48px), contentDescription = stringResource(R.string.add_sub))
                        }
                        if (subCount > 0) Icon(painterResource(R.drawable.expand_more_48px),
                            contentDescription = if (expanded) {
                                stringResource(R.string.cd_collapse_section, category.name)
                            } else {
                                stringResource(R.string.cd_expand_section, category.name)
                            },
                            modifier = Modifier.graphicsLayer { rotationZ = chevron },
                        )
                    }
                }
            } else {
                null
            },
            content = {
                Text(
                    if (category.archived) stringResource(R.string.name_archived, category.name) else category.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
        )
    }
}

@Composable
private fun colorFromHex(hex: String): Color =
    runCatching { Color(hex.toColorInt()) }.getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)

private fun Color.toHexString(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

private val HEX_COLOR_REGEX = Regex("^#[0-9a-fA-F]{6}$")

@Composable
private fun CategoryFormContent(
    sections: List<CategorySection>,
    initialSection: CategorySection,
    parentId: String?,
    editing: Category?,
    /** Every category there is, archived ones included: a name is unique among its siblings whatever their state. */
    existing: List<Category>,
    submitting: Boolean,
    parentOptionsFor: (CategorySection) -> List<Category>,
    nextColorFor: (CategorySection) -> String,
    onSave: (String, CategoryKind, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var selectedSection by remember { mutableStateOf(initialSection) }
    var color by remember { mutableStateOf(editing?.color ?: nextColorFor(initialSection)) }
    val openedName = remember { name }

    val kind = editing?.kind ?: selectedSection.kind

    // A subcategory takes its parent's kind, and its name has to be unique among its siblings of that kind.
    val siblingsParentId = editing?.parentId ?: parentId
    val effectiveKind = siblingsParentId?.let { id -> existing.firstOrNull { it.id == id }?.kind } ?: kind
    val form = rememberFormValidation()
    val nameField = form.field(
        "name",
        nameProblem(name, R.string.error_name_taken_category) { taken ->
            existing.any { it.id != editing?.id && it.name == taken && it.kind == effectiveKind && it.parentId == siblingsParentId }
        },
    )
    val colourField = form.field("colour", if (!isHexColour(color)) stringResource(R.string.hex_colour_hint) else null)

    FullScreenDialog(
        title = stringResource(if (editing != null) R.string.edit_category else R.string.new_category),
        onDismiss = onCancel,
        onSave = { onSave(name.trim(), kind, color) },
        saveEnabled = form.valid(nameField, colourField),
        submitting = submitting,
        // A new category's colour follows its kind until one is picked, so choosing a kind alone is not an entry to lose.
        dirty = name != openedName || color != (editing?.color ?: nextColorFor(selectedSection)),
    ) {
        // What kind it is leads the form, as the shape of a transaction leads its own. A section is told by its key:
        // the sections are rebuilt as the ledger changes, and the one held here would stop matching.
        if (editing == null && parentId == null) {
            ConnectedButtonGroup(
                options = sections.map { it.key },
                selected = selectedSection.key,
                onSelect = { key ->
                    val option = sections.first { it.key == key }
                    selectedSection = option
                    color = nextColorFor(option)
                },
                label = { key -> sections.first { it.key == key }.title },
            )
        }

        YuukaTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.label_name),
            singleLine = true,
            field = nameField
        )

        if (editing == null && parentId != null) {
            val parentName = parentOptionsFor(selectedSection).firstOrNull { it.id == parentId }?.name
            Text(
                stringResource(R.string.nested_under, parentName ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        val isCustomColor = PALETTE.none { it.light.equals(color, ignoreCase = true) }
        val isValidColor = HEX_COLOR_REGEX.matches(color)

        Text(stringResource(R.string.label_colour), style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PALETTE.forEach { slot ->
                val selected = color == slot.light
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorFromHex(slot.light))
                        .then(
                            if (selected) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.onSurface,
                                CircleShape
                            ) else Modifier
                        )
                        .semantics { contentDescription = slot.name }
                        .selectable(selected = selected, role = Role.RadioButton) { color = slot.light },
                )
            }

            // A ninth, custom slot: picking it opts out of the validated palette's
            // colour-vision-deficiency guarantee, so it stays a deliberate extra
            // step rather than a slot in the same row. Mirrors the web app's
            // custom-colour swatch in CategoriesView.vue.
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isCustomColor && isValidColor) colorFromHex(color) else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        width = if (isCustomColor) 2.dp else 1.dp,
                        color = if (isCustomColor) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    )
                    .selectable(selected = isCustomColor, role = Role.RadioButton) {
                        if (!isCustomColor) color = "#64748b"
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (!isCustomColor) {
                    Icon(painterResource(R.drawable.add_48px),
                        contentDescription = stringResource(R.string.custom_colour),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        if (isCustomColor) {
            ColorWheelPicker(
                color = if (isValidColor) colorFromHex(color) else colorFromHex("#64748b"),
                onColorChange = { picked -> color = picked.toHexString() },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            YuukaTextField(
                value = color,
                onValueChange = { color = it },
                label = stringResource(R.string.custom_colour_hex),
                placeholder = stringResource(R.string.placeholder_hex_sample),
                singleLine = true,
                field = colourField,
            )
        }

        // Only a new one: Yuuka at the foot of the form.
        if (editing == null) {
            YuukaSays(YuukaMood.Neutral, stringResource(R.string.categories_form_line), Modifier.padding(top = 8.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CategoriesScreenPreview() {
    ScreenPreview {
        CategoriesScreenContent(CategoriesUiState(categories = PreviewData.categories))
    }
}
