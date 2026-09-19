package dev.gavenda.yuuka.ui.tags

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.Tag
import dev.gavenda.yuuka.domain.PALETTE
import dev.gavenda.yuuka.ui.common.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(modifier: Modifier = Modifier, viewModel: TagsViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current

    // `creating` opens the sheet, `editing` fills it.
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Tag?>(null) }

    val tagAddedMessage = stringResource(R.string.tag_added)
    val tagUpdatedMessage = stringResource(R.string.tag_updated)
    val tagDeletedMessage = stringResource(R.string.tag_deleted)

    Scaffold(
        modifier = modifier,
        // The outer app bar's Scaffold already insets for system bars — an inset-aware
        // nested Scaffold here would add a second, phantom gap above the content.
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_tag)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tags_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.tags.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_tags_yet), description = stringResource(R.string.tags_empty_description)) }
            } else {
                if (state.showSearch) {
                    item {
                        OutlinedTextField(
                            value = state.search,
                            onValueChange = viewModel::setSearch,
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.visible, key = { it.id }) { tag ->
                        val deleteKey = "delete:${tag.id}"
                        TagRow(
                            tag,
                            deleting = busy.isBusy(deleteKey),
                            onEdit = { editing = tag },
                            onDelete = { busy.run(deleteKey, snackbarHostState, successMessage = tagDeletedMessage) { viewModel.deleteTag(tag.id) } },
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        val formKey = "tag-form"
        val submitting = busy.isBusy(formKey)
        ModalBottomSheet(onDismissRequest = { if (!submitting) { creating = false; editing = null } }) {
            WithSnackbarOverlay {
                TagForm(
                    editing = editing,
                    initialColor = viewModel.nextColor(),
                    submitting = submitting,
                    onSave = { name, color ->
                        busy.run(
                            formKey,
                            snackbarHostState,
                            successMessage = if (editing != null) tagUpdatedMessage else tagAddedMessage,
                            onSuccess = { creating = false; editing = null },
                        ) {
                            val tag = editing
                            if (tag != null) viewModel.updateTag(tag.id, name, color) else viewModel.createTag(name, color)
                        }
                    },
                    onCancel = { creating = false; editing = null },
                )
            }
        }
    }
}

@Composable
private fun TagRow(tag: Tag, deleting: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    SwipeToRevealActions(
        modifier = Modifier.fillMaxWidth(),
        actions = {
            ActionIconButton(ActionIcon.DELETE, stringResource(R.string.cd_delete_item, tag.name), onDelete, danger = true, loading = deleting)
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onEdit)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(colorFromHex(tag.color)))
            Text(tag.name, modifier = Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun colorFromHex(hex: String): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(MaterialTheme.colorScheme.onSurfaceVariant)

/** A tag is a name and one of the palette's colours — the same validated set a category takes. */
@Composable
private fun TagForm(
    editing: Tag?,
    initialColor: String,
    submitting: Boolean,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var color by remember { mutableStateOf(editing?.color ?: initialColor) }

    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (editing != null) stringResource(R.string.edit_tag) else stringResource(R.string.new_tag), style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text(stringResource(R.string.label_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

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
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onCancel, enabled = !submitting, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_cancel)) }
            Button(
                modifier = Modifier.weight(1f),
                onClick = { if (name.isNotBlank()) onSave(name.trim(), color) },
                enabled = name.isNotBlank() && !submitting,
            ) {
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(if (editing != null) stringResource(R.string.save_changes) else stringResource(R.string.add_tag))
                }
            }
        }
    }
}
