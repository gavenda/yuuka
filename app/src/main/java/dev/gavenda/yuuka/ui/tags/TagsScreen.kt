package dev.gavenda.yuuka.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.domain.PALETTE
import dev.gavenda.yuuka.domain.isHexColour
import dev.gavenda.yuuka.domain.sameName
import dev.gavenda.yuuka.domain.formatCount
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel
import dev.gavenda.yuuka.domain.nextColor
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(modifier: Modifier = Modifier, viewModel: TagsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The counts move whenever a transaction is saved, so ask again each time the screen is shown.
    LaunchedEffect(viewModel) { viewModel.refresh() }

    TagsScreenContent(
        state = state,
        modifier = modifier,
        onSearchChange = viewModel::setSearch,
        onCreateTag = viewModel::createTag,
        onUpdateTag = viewModel::updateTag,
        onDeleteTag = viewModel::deleteTag,
    )
}

/** The screen itself, drawn from the state it is handed — which is what lets a preview show it without a view model. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagsScreenContent(
    state: TagsUiState,
    modifier: Modifier = Modifier,
    onSearchChange: (String) -> Unit = {},
    onCreateTag: suspend (name: String, color: String) -> Unit = { _, _ -> },
    onUpdateTag: suspend (id: String, name: String, color: String) -> Unit = { _, _, _ -> },
    onDeleteTag: suspend (id: String) -> Unit = {},
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    // `creating` opens the sheet, `editing` fills it.
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tag?>(null) }

    val tagAddedMessage = stringResource(R.string.tag_added)
    val tagUpdatedMessage = stringResource(R.string.tag_updated)
    val tagDeletedMessage = stringResource(R.string.tag_deleted)

    // The headline shrinks into the ordinary bar as the list moves and stays there until it is scrolled back.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        modifier = modifier.appBarScroll(scrollBehavior),
        topBar = { LargeScreenTopBar(stringResource(R.string.destination_tags), scrollBehavior, actions = {}) },
        
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_tag),
                icon = Icons.Filled.Add,
                onClick = { creating = true },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            // Rows of the block sit a hair apart, the way a settings group is drawn.
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (state.tags.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_tags_yet), description = stringResource(R.string.tags_empty_description)) }
            } else {
                if (state.showSearch) {
                    item {
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = onSearchChange,
                            label = { Text(stringResource(R.string.search_tags)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (state.visible.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.no_tag_matches, state.search.trim()),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                } else {
                    itemsIndexed(state.visible, key = { _, tag -> tag.id }) { index, tag ->
                        val deleteKey = "delete:${tag.id}"
                        TagRow(
                            tag,
                            // Tags are one list rather than groups of a few, so the whole list is the block.
                            position = positionInGroup(index, state.visible.lastIndex),
                            deleting = busy.isBusy(deleteKey),
                            onEdit = { editing = tag },
                            onDelete = { busy.run(deleteKey, snackbarHostState, successMessage = tagDeletedMessage) { onDeleteTag(tag.id) } },
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val formKey = "tag-form"
        val submitting = busy.isBusy(formKey)
        TagForm(
            editing = editing,
            existing = state.tags,
            initialColor = nextColor(state.tags.size),
            submitting = submitting,
            onSave = { name, color ->
                busy.run(
                    formKey,
                    snackbarHostState,
                    successMessage = if (editing != null) tagUpdatedMessage else tagAddedMessage,
                    onSuccess = { creating = false; editing = null },
                ) {
                    val tag = editing
                    if (tag != null) onUpdateTag(tag.id, name, color) else onCreateTag(name, color)
                }
            },
            onCancel = { creating = false; editing = null },
        )
    }
}

/** One row per tag — its colour leading, its count at the end — drawn as one of a connected block. */
@Composable
private fun TagRow(tag: Tag, position: ItemPosition, deleting: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, tag.name), onDelete, danger = true, loading = deleting)
        },
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth().clip(groupedItemShape(position)),
            onClick = onEdit,
            leadingContent = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(harmonisedColor(tag.color, MaterialTheme.colorScheme.onSurfaceVariant))) },
            content = { Text(tag.name, style = MaterialTheme.typography.bodyMedium) },
            trailingContent = {
                Text(
                    pluralStringResource(R.plurals.tag_transaction_count, tag.transactionCount, formatCount(tag.transactionCount.toLong())),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
        )
    }
}

private fun Color.toHexString(): String = String.format("#%06X", 0xFFFFFF and this.toArgb())

private val HEX_COLOR_REGEX = Regex("^#[0-9a-fA-F]{6}$")

@Composable
private fun colorFromHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)

/** A tag is a name and a colour: one of the palette's, or a hand-picked one, as for a category. */
@Composable
private fun TagForm(
    editing: Tag?,
    existing: List<Tag>,
    initialColor: String,
    submitting: Boolean,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var color by remember { mutableStateOf(editing?.color ?: initialColor) }
    // What the form opened with, so that closing it can tell an entry from an untouched form.
    val opened = remember { name to color }

    // Tags are unique per person whatever the case, so a name is checked against the others (an edit may keep its own).
    val form = rememberFormValidation()
    val nameField = form.field(
        "name",
        nameProblem(name, R.string.error_name_taken_tag) { taken -> existing.any { it.id != editing?.id && sameName(it.name, taken) } },
    )
    val colourField = form.field("colour", if (!isHexColour(color)) stringResource(R.string.hex_colour_hint) else null)

    FullScreenDialog(
        title = stringResource(if (editing != null) R.string.edit_tag else R.string.new_tag),
        onDismiss = onCancel,
        onSave = { onSave(name.trim(), color) },
        saveEnabled = form.valid(nameField, colourField),
        submitting = submitting,
        dirty = (name to color) != opened,
    ) {
        YuukaTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.label_name),
            singleLine = true,
            field = nameField,
        )

        val isCustomColor = PALETTE.none { it.light.equals(color, ignoreCase = true) }
        val isValidColor = HEX_COLOR_REGEX.matches(color)

        Text(stringResource(R.string.label_colour), style = MaterialTheme.typography.labelMedium)
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PALETTE.forEach { slot ->
                val selected = color.equals(slot.light, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorFromHex(slot.light))
                        .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                        .semantics { contentDescription = slot.name }
                        .selectable(selected = selected, role = Role.RadioButton) { color = slot.light },
                )
            }

            // A ninth, custom slot: picking it opts out of the validated palette's colour-vision-deficiency
            // guarantee, so it stays a deliberate extra step rather than a slot in the same row, as for a category.
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
                    .selectable(selected = isCustomColor, role = Role.RadioButton) { if (!isCustomColor) color = "#64748b" },
                contentAlignment = Alignment.Center,
            ) {
                if (!isCustomColor) {
                    Icon(
                        Icons.Filled.Add,
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
    }
}

@Preview(showBackground = true)
@Composable
private fun TagsScreenPreview() {
    ScreenPreview {
        TagsScreenContent(TagsUiState(tags = PreviewData.tags))
    }
}
