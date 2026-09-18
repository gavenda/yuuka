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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.data.model.Category
import dev.gavenda.yuuka.data.model.CategoryKind
import dev.gavenda.yuuka.data.model.CategoryScope
import dev.gavenda.yuuka.data.remote.ApiError
import dev.gavenda.yuuka.domain.PALETTE
import dev.gavenda.yuuka.ui.common.ActionIcon
import dev.gavenda.yuuka.ui.common.ActionIconButton
import dev.gavenda.yuuka.ui.common.ColorWheelPicker
import dev.gavenda.yuuka.ui.common.EmptyState
import dev.gavenda.yuuka.ui.common.SwipeToRevealActions
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(modifier: Modifier = Modifier, viewModel: CategoriesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var creatingIn by remember { mutableStateOf<CategorySection?>(null) }
    var creatingParentId by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creatingIn = state.sections.first(); creatingParentId = null },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New category") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.categories.isEmpty()) {
                item { EmptyState("No categories yet", description = "Categories are how spending gets grouped and budgeted.") }
            }

            state.sections.forEach { section ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(section.title, style = MaterialTheme.typography.titleSmall)
                            Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            if (section.families.isEmpty()) {
                                Text(
                                    "No ${section.title.lowercase()} categories yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            } else {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    section.families.forEach { family ->
                                        CategoryRow(
                                            family.parent,
                                            onEdit = { editing = family.parent },
                                            onArchive = { scope.launch { viewModel.setArchived(family.parent.id, !family.parent.archived) } },
                                            onDelete = { scope.launch { runCatching { viewModel.deleteCategory(family.parent.id) } } },
                                            onAddSub = { creatingIn = section; creatingParentId = family.parent.id },
                                        )
                                        family.children.forEach { child ->
                                            CategoryRow(
                                                child,
                                                indent = true,
                                                onEdit = { editing = child },
                                                onArchive = { scope.launch { viewModel.setArchived(child.id, !child.archived) } },
                                                onDelete = { scope.launch { runCatching { viewModel.deleteCategory(child.id) } } },
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
                        Text("${if (state.showArchived) "Hide" else "Show"} ${state.archivedCount} archived")
                    }
                }
            }
        }
    }

    val section = creatingIn
    if (section != null || editing != null) {
        ModalBottomSheet(onDismissRequest = { creatingIn = null; editing = null }) {
            CategoryFormContent(
                sections = state.sections,
                initialSection = section ?: state.sections.first(),
                parentId = creatingParentId,
                editing = editing,
                parentOptionsFor = { viewModel.parentOptionsFor(it.kind, it.appliesTo) },
                nextColorFor = { viewModel.nextColorFor(it.kind, it.appliesTo) },
                onSave = { name, kind, appliesTo, color ->
                    scope.launch {
                        try {
                            if (editing != null) viewModel.updateCategory(editing!!.id, name, kind, color)
                            else viewModel.createCategory(name, kind, appliesTo, color, creatingParentId)
                            creatingIn = null
                            editing = null
                        } catch (_: ApiError) {
                            // The form stays open; a real app would surface this inline.
                        }
                    }
                },
                onCancel = { creatingIn = null; editing = null },
            )
        }
    }
}

@Composable
private fun CategoryRow(category: Category, indent: Boolean = false, onEdit: () -> Unit, onArchive: () -> Unit, onDelete: () -> Unit, onAddSub: (() -> Unit)? = null) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(if (category.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE, "Archive ${category.name}", onArchive)
            ActionIconButton(ActionIcon.DELETE, "Delete ${category.name}", onDelete, danger = true)
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
                category.name + if (category.archived) " (Archived)" else "",
                modifier = Modifier.padding(start = 8.dp).weight(1f),
                style = if (indent) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
            if (onAddSub != null) TextButton(onClick = onAddSub) { Text("+ Sub") }
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
        Text(if (editing != null) "Edit category" else "New category", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())

        if (editing == null && parentId == null) {
            Text("Kind", style = MaterialTheme.typography.labelMedium)
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
            Text("Nested under: ${parentName ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val isCustomColor = PALETTE.none { it.light.equals(color, ignoreCase = true) }
        val isValidColor = HEX_COLOR_REGEX.matches(color)

        Text("Colour", style = MaterialTheme.typography.labelMedium)
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
                        contentDescription = "Custom colour",
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
                label = { Text("Custom colour (hex)") },
                placeholder = { Text("#64748b") },
                singleLine = true,
                isError = !isValidColor,
                supportingText = if (!isValidColor) {
                    { Text("Enter a 6-digit hex colour, e.g. #64748b") }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(
                onClick = { if (name.isNotBlank() && isValidColor) onSave(name, kind, appliesTo, color) },
                enabled = name.isNotBlank() && isValidColor,
            ) { Text(if (editing != null) "Save changes" else "Add category") }
        }
    }
}
