package dev.gavenda.yuuka.ui.categories

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
import dev.gavenda.yuuka.domain.CollapsedSections
import dev.gavenda.yuuka.domain.PALETTE
import dev.gavenda.yuuka.domain.isHexColour
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(modifier: Modifier = Modifier, viewModel: CategoriesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    var creatingIn by remember { mutableStateOf<CategorySection?>(null) }
    var creatingParentId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Category?>(null) }
    val sectionState = koinInject<CollapsedSections>()
    val collapsedSections by sectionState.categories.collectAsStateWithLifecycle()

    val categoryRestoredMessage = stringResource(R.string.category_restored)
    val categoryArchivedMessage = stringResource(R.string.category_archived)
    val categoryDeletedMessage = stringResource(R.string.category_deleted)
    val categoryUpdatedMessage = stringResource(R.string.category_updated)
    val categoryAddedMessage = stringResource(R.string.category_added)

    Scaffold(
        modifier = modifier,
        topBar = { ScreenTopBar(stringResource(R.string.destination_categories)) },
        
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_category),
                icon = Icons.Filled.Add,
                onClick = { creatingIn = state.sections.first(); creatingParentId = null },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
        ) {
            if (state.categories.isEmpty()) {
                item {
                    EmptyState(
                        stringResource(R.string.no_categories_yet),
                        description = stringResource(R.string.categories_empty_description)
                    )
                }
            }

            state.sections.forEach { section ->
                val expanded = section.key !in collapsedSections
                item {
                    Column(
                        modifier = Modifier
                            .clickable { sectionState.toggleCategorySection(section.key) }
                            .animateContentSize(),
                    ) {
                        ListItem(
                            content = { Text(section.title) },
                            supportingContent = { Text(section.description) },
                            trailingContent = {
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) stringResource(
                                        R.string.cd_collapse_section,
                                        section.title
                                    ) else stringResource(R.string.cd_expand_section, section.title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        )

                        if (expanded && section.families.isEmpty()) {
                            Text(
                                stringResource(R.string.no_section_categories_yet, section.title.lowercase()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (expanded) {
                            Column {
                                section.families.forEach { family ->
                                    val archiveKey = "archive:${family.parent.id}"
                                    val deleteKey = "delete:${family.parent.id}"
                                    CategoryRow(
                                        family.parent,
                                        archiving = busy.isBusy(archiveKey),
                                        deleting = busy.isBusy(deleteKey),
                                        onEdit = { editing = family.parent },
                                        onArchive = {
                                            busy.run(
                                                archiveKey,
                                                snackbarHostState,
                                                successMessage = if (family.parent.archived) categoryRestoredMessage else categoryArchivedMessage,
                                            ) { viewModel.setArchived(family.parent.id, !family.parent.archived) }
                                        },
                                        onDelete = {
                                            busy.run(
                                                deleteKey,
                                                snackbarHostState,
                                                successMessage = categoryDeletedMessage
                                            ) {
                                                viewModel.deleteCategory(family.parent.id)
                                            }
                                        },
                                        onAddSub = { creatingIn = section; creatingParentId = family.parent.id },
                                    )
                                    family.children.forEach { child ->
                                        val childArchiveKey = "archive:${child.id}"
                                        val childDeleteKey = "delete:${child.id}"
                                        CategoryRow(
                                            child,
                                            indent = true,
                                            archiving = busy.isBusy(childArchiveKey),
                                            deleting = busy.isBusy(childDeleteKey),
                                            onEdit = { editing = child },
                                            onArchive = {
                                                busy.run(
                                                    childArchiveKey,
                                                    snackbarHostState,
                                                    successMessage = if (child.archived) categoryRestoredMessage else categoryArchivedMessage,
                                                ) { viewModel.setArchived(child.id, !child.archived) }
                                            },
                                            onDelete = {
                                                busy.run(
                                                    childDeleteKey,
                                                    snackbarHostState,
                                                    successMessage = categoryDeletedMessage
                                                ) {
                                                    viewModel.deleteCategory(child.id)
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.archivedCount > 0) {
                item {
                    TextButton(onClick = viewModel::toggleShowArchived) {
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
        ModalBottomSheet(onDismissRequest = {
            if (!submitting) {
                creatingIn = null; editing = null
            }
        }) {
            WithSnackbarOverlay {
                CategoryFormContent(
                    sections = state.sections,
                    initialSection = section ?: state.sections.first(),
                    parentId = creatingParentId,
                    editing = editing,
                    existing = state.categories,
                    submitting = submitting,
                    parentOptionsFor = { viewModel.parentOptionsFor(it.kind) },
                    nextColorFor = { viewModel.nextColorFor(it.kind) },
                    onSave = { name, kind, color ->
                        busy.run(
                            formKey,
                            snackbarHostState,
                            successMessage = if (editing != null) categoryUpdatedMessage else categoryAddedMessage,
                            onSuccess = { creatingIn = null; editing = null },
                        ) {
                            if (editing != null) viewModel.updateCategory(editing!!.id, name, kind, color)
                            else viewModel.createCategory(name, kind, color, creatingParentId)
                        }
                    },
                    onCancel = { creatingIn = null; editing = null },
                )
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    indent: Boolean = false,
    archiving: Boolean = false,
    deleting: Boolean = false,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onAddSub: (() -> Unit)? = null,
) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
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
            modifier = if (indent) Modifier.padding(start = 16.dp) else Modifier.padding(start = 0.dp),
            onClick = onEdit,
            leadingContent = {
                Box(
                    modifier = Modifier.size(12.dp).clip(CircleShape).background(colorFromHex(category.color)),
                )
            },
            content = {
                Text(
                    if (category.archived) stringResource(R.string.name_archived, category.name) else category.name,
                )
            },
            trailingContent = {
                if (onAddSub != null) TextButton(onClick = onAddSub) { Text(stringResource(R.string.add_sub)) }
            }
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

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            if (editing != null) stringResource(R.string.edit_category) else stringResource(R.string.new_category),
            style = MaterialTheme.typography.titleMedium
        )

        YuukaTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.label_name),
            singleLine = true,
            field = nameField
        )

        if (editing == null && parentId == null) {
            Text(stringResource(R.string.label_kind), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEach { option ->
                    FilterChip(
                        selected = selectedSection.key == option.key,
                        onClick = {
                            selectedSection = option
                            color = nextColorFor(option)
                        },
                        label = { Text(option.title) },
                    )
                }
            }
        }

        if (editing == null && parentId != null) {
            val parentName = parentOptionsFor(selectedSection).firstOrNull { it.id == parentId }?.name
            Text(
                stringResource(R.string.nested_under, parentName ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (!isCustomColor) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.custom_colour),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        R.string.action_cancel
                    )
                )
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { onSave(name.trim(), kind, color) },
                enabled = !submitting && form.valid(nameField, colourField),
            ) {
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(if (editing != null) stringResource(R.string.save_changes) else stringResource(R.string.add_category))
                }
            }
        }
    }
}
