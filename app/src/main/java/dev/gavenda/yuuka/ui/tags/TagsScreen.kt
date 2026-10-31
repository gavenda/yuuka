package dev.gavenda.yuuka.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.domain.isHexColour
import dev.gavenda.yuuka.domain.sameName
import dev.gavenda.yuuka.ui.common.rememberFormSwitch
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel
import dev.gavenda.yuuka.domain.nextColor
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun TagsScreen(modifier: Modifier = Modifier, viewModel: TagsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The totals move whenever a transaction is saved, so ask again each time the screen is shown.
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
    // Beside a supporting pane the list stays usable, so opening another form asks first if this one has changes.
    val switchForm = rememberFormSwitch()
    // A form standing beside the list closes with the row it was editing, wherever that was deleted.
    LaunchedEffect(state.tags) { if (editing?.let { open -> state.tags.none { it.id == open.id } } == true) editing = null }

    val tagAddedMessage = stringResource(R.string.tag_added)
    val tagUpdatedMessage = stringResource(R.string.tag_updated)
    val tagDeletedMessage = stringResource(R.string.tag_deleted)


    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = WindowInsets(0),
        modifier = modifier,
        topBar = { ScreenTopBar(stringResource(R.string.destination_tags), actions = {}) },
        
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_tag),
                icon = R.drawable.add_48px,
                onClick = { switchForm { editing = null; creating = true } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            // Rows of the block sit a hair apart, the way a settings group is drawn.
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            if (state.tags.isEmpty()) {
                item { EmptyState(stringResource(R.string.tags_empty_title), YuukaMood.Pleased, modifier = Modifier.fillParentMaxHeight(), description = stringResource(R.string.tags_empty_description)) }
            } else {
                if (state.showSearch) {
                    item {
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = onSearchChange,
                            label = { Text(stringResource(R.string.search_tags)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                            currency = state.displayCurrency,
                            // Tags are one list rather than groups of a few, so the whole list is the block.
                            position = positionInGroup(index, state.visible.lastIndex),
                            deleting = busy.isBusy(deleteKey),
                            // The tag whose form stands beside the list.
                            selected = tag.id == editing?.id,
                            onEdit = { switchForm { creating = false; editing = tag } },
                            onDelete = { busy.run(deleteKey, snackbarHostState, successMessage = tagDeletedMessage) { onDeleteTag(tag.id) } },
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) key(editing?.id) {
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

/** One row per tag — its colour leading, what its transactions come to at the end — drawn as one of a connected block. */
@Composable
private fun TagRow(tag: Tag, currency: String, position: ItemPosition, deleting: Boolean, selected: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, tag.name), onDelete, danger = true, loading = deleting)
        },
    ) {
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            onClick = onEdit,
            shapes = groupedListItemShapes(position, selected),
            colors = groupedListItemColors(selected),
            leadingContent = { Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(harmonisedColor(tag.color, MaterialTheme.colorScheme.onSurfaceVariant))) },
            content = { Text(tag.name, style = MaterialTheme.typography.bodyMedium) },
            trailingContent = {
                MoneyText(tag.total, currency = currency, style = MaterialTheme.typography.bodySmall)
            },
        )
    }
}




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

        ColourField(color = color, onColorChange = { color = it }, field = colourField)

        // Only a new one: Yuuka at the foot of the form.
        if (editing == null) {
            YuukaSays(YuukaMood.Pleased, stringResource(R.string.tags_form_line), Modifier.padding(top = 8.dp))
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
