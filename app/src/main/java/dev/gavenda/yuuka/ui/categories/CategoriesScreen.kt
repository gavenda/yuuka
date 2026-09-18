package dev.gavenda.yuuka.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.CategoryScope
import dev.gavenda.yuuka.domain.PALETTE
import dev.gavenda.yuuka.ui.common.ActionIcon
import dev.gavenda.yuuka.ui.common.ActionIconButton
import dev.gavenda.yuuka.ui.common.ColorWheelPicker
import dev.gavenda.yuuka.ui.common.EmptyState
import dev.gavenda.yuuka.ui.common.LocalSnackbarHostState
import dev.gavenda.yuuka.ui.common.MutationLoadingIndicator
import dev.gavenda.yuuka.ui.common.SwipeToRevealActions
import dev.gavenda.yuuka.ui.common.WithSnackbarOverlay
import dev.gavenda.yuuka.ui.common.rememberBusyState
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(modifier: Modifier = Modifier, viewModel: CategoriesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    var creatingIn by remember { mutableStateOf<CategorySection?>(null) }
    var creatingParentId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Category?>(null) }
    var collapsedSections by remember { mutableStateOf(emptySet<String>()) }

    val categoryRestoredMessage = stringResource(R.string.category_restored)
    val categoryArchivedMessage = stringResource(R.string.category_archived)
    val categoryDeletedMessage = stringResource(R.string.category_deleted)
    val categoryUpdatedMessage = stringResource(R.string.category_updated)
    val categoryAddedMessage = stringResource(R.string.category_added)

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creatingIn = state.sections.first(); creatingParentId = null },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_category)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.categories.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_categories_yet), description = stringResource(R.string.categories_empty_description)) }
            }

            state.sections.forEach { section ->
                val expanded = section.key !in collapsedSections
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        collapsedSections = if (expanded) {
                                            collapsedSections + section.key
                                        } else {
                                            collapsedSections - section.key
                                        }
                                    },
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(section.title, style = MaterialTheme.typography.titleSmall)
                                    Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(
                                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) stringResource(R.string.cd_collapse_section, section.title) else stringResource(R.string.cd_expand_section, section.title),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            if (expanded && section.families.isEmpty()) {
                                Text(
                                    stringResource(R.string.no_section_categories_yet, section.title.lowercase()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            } else if (expanded) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
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
                                                busy.run(deleteKey, snackbarHostState, successMessage = categoryDeletedMessage) {
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
                                                    busy.run(childDeleteKey, snackbarHostState, successMessage = categoryDeletedMessage) {
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
        ModalBottomSheet(onDismissRequest = { if (!submitting) { creatingIn = null; editing = null } }) {
            WithSnackbarOverlay {
                CategoryFormContent(
                    sections = state.sections,
                    initialSection = section ?: state.sections.first(),
                    parentId = creatingParentId,
                    editing = editing,
                    submitting = submitting,
                    parentOptionsFor = { viewModel.parentOptionsFor(it.kind, it.appliesTo) },
                    nextColorFor = { viewModel.nextColorFor(it.kind, it.appliesTo) },
                    onSave = { name, kind, appliesTo, color ->
                        busy.run(
                            formKey,
                            snackbarHostState,
                            successMessage = if (editing != null) categoryUpdatedMessage else categoryAddedMessage,
                            onSuccess = { creatingIn = null; editing = null },
                        ) {
                            if (editing != null) viewModel.updateCategory(editing!!.id, name, kind, color)
                            else viewModel.createCategory(name, kind, appliesTo, color, creatingParentId)
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
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, category.name), onDelete, danger = true, loading = deleting)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onEdit)
                .padding(start = if (indent) 24.dp else 0.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(if (indent) 8.dp else 12.dp).clip(CircleShape).background(colorFromHex(category.color)),
            )
            Text(
                if (category.archived) stringResource(R.string.name_archived, category.name) else category.name,
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                style = if (indent) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
            if (onAddSub != null) TextButton(onClick = onAddSub) { Text(stringResource(R.string.add_sub)) }
        }
    }
}

private fun colorFromHex(hex: String): Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color.Gray)

private fun Color.toHexString(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

private val HEX_COLOR_REGEX = Regex("^#[0-9a-fA-F]{6}$")

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFormContent(
    sections: List<CategorySection>,
    initialSection: CategorySection,
    parentId: String?,
    editing: Category?,
    submitting: Boolean,
    parentOptionsFor: (CategorySection) -> List<Category>,
    nextColorFor: (CategorySection) -> String,
    onSave: (String, CategoryKind, CategoryScope, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var selectedSection by remember { mutableStateOf(initialSection) }
    var color by remember { mutableStateOf(editing?.color ?: nextColorFor(initialSection)) }

    val kind = editing?.kind ?: selectedSection.kind
    val appliesTo = editing?.appliesTo ?: selectedSection.appliesTo

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (editing != null) stringResource(R.string.edit_category) else stringResource(R.string.new_category), style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.label_name)) }, modifier = Modifier.fillMaxWidth())

        if (editing == null && parentId == null) {
            Text(stringResource(R.string.label_kind), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEach { option ->
                    androidx.compose.material3.FilterChip(
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
            Text(stringResource(R.string.nested_under, parentName ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val isCustomColor = PALETTE.none { it.light.equals(color, ignoreCase = true) }
        val isValidColor = HEX_COLOR_REGEX.matches(color)

        Text(stringResource(R.string.label_colour), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PALETTE.forEach { slot ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorFromHex(slot.light))
                        .border(
                            width = if (color == slot.light) 2.dp else 0.dp,
                            color = if (color == slot.light) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                            shape = CircleShape,
                        )
                        .clickable { color = slot.light },
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
                    .clickable { if (!isCustomColor) color = "#64748b" },
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

            OutlinedTextField(
                value = color,
                onValueChange = { color = it },
                label = { Text(stringResource(R.string.custom_colour_hex)) },
                placeholder = { Text(stringResource(R.string.placeholder_hex_sample)) },
                singleLine = true,
                isError = !isValidColor,
                supportingText = if (!isValidColor) {
                    { Text(stringResource(R.string.hex_colour_hint)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, enabled = !submitting) { Text(stringResource(R.string.action_cancel)) }
            Button(
                onClick = { if (name.isNotBlank() && isValidColor) onSave(name, kind, appliesTo, color) },
                enabled = name.isNotBlank() && isValidColor && !submitting,
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
