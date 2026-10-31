package dev.gavenda.yuuka.ui.accounttypes

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.data.model.AccountType
import dev.gavenda.yuuka.ui.common.*
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountTypesScreen(modifier: Modifier = Modifier, viewModel: AccountTypesViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.refresh() }

    AccountTypesScreenContent(
        state = state,
        modifier = modifier,
        onCreate = viewModel::create,
        onRename = viewModel::rename,
        onSetArchived = viewModel::setArchived,
        onDelete = viewModel::delete,
        onReorder = viewModel::reorder,
    )
}

/**
 * The account types, on a screen of their own reached from Settings: a list like any other in the app. Pressing
 * a type renames it, swiping it shows archive and delete, and the screen's leading action asks for a new one by
 * name in a basic dialog — the same one a rename opens. An active type is moved by the handle at its end, and the
 * order is saved the moment it is dropped; archived types are not ordered, and stay after the active ones.
 */
@Composable
internal fun AccountTypesScreenContent(
    state: AccountTypesUiState,
    modifier: Modifier = Modifier,
    onCreate: suspend (name: String) -> Unit = {},
    onRename: suspend (id: String, name: String) -> Unit = { _, _ -> },
    onSetArchived: suspend (id: String, archived: Boolean) -> Unit = { _, _ -> },
    onDelete: suspend (id: String) -> Unit = {},
    onReorder: suspend (activeIds: List<String>) -> Unit = {},
) {
    val busy = rememberBusyState()
    val snackbarHostState = LocalSnackbarHostState.current
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<AccountType?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    val typeAddedMessage = stringResource(R.string.type_added)
    val typeRenamedMessage = stringResource(R.string.type_renamed)
    val typeRestoredMessage = stringResource(R.string.type_restored)
    val typeArchivedMessage = stringResource(R.string.type_archived)
    val typeDeletedMessage = stringResource(R.string.type_deleted)

    val types = state.types
    val active = types.filter { !it.archived }
    val archivedTypes = types.filter { it.archived }
    val archivedCount = archivedTypes.size

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // The order on show: what is saved, except while a row is being dragged, when it is where the drag has put it.
    var order by remember { mutableStateOf(active) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val saved by rememberUpdatedState(active)
    LaunchedEffect(active) { if (draggingId == null) order = active }
    val visible = order + if (showArchived) archivedTypes else emptyList()

    /** Writes the order on show, if it differs from what is saved. */
    fun commitOrder() {
        val ids = order.map { it.id }
        if (ids != saved.map { it.id }) busy.run("type-order", snackbarHostState) { onReorder(ids) }
    }

    /** Moves the active type [from] one place to [to], as the keyboard and a screen reader do it. */
    fun move(from: Int, to: Int) {
        if (to !in order.indices) return
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        commitOrder()
    }

    /** Follows a drag by [delta]: the row moves with the finger, and takes a neighbour's place once past its middle. */
    fun dragBy(delta: Float) {
        val id = draggingId ?: return
        dragOffset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == id } ?: return
        val middle = current.offset + dragOffset + current.size / 2f
        val target = items.firstOrNull { info ->
            info.key != id && order.any { it.id == info.key } && middle >= info.offset && middle <= info.offset + info.size
        } ?: return
        val from = order.indexOfFirst { it.id == id }
        val to = order.indexOfFirst { it.id == target.key }
        // A list keeps its first row where it is, so a swap with it would scroll the list after the dragged row.
        val firstIndex = listState.firstVisibleItemIndex
        val firstOffset = listState.firstVisibleItemScrollOffset
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset += current.offset - target.offset
        if (from == firstIndex || to == firstIndex) scope.launch { listState.scrollToItem(firstIndex, firstOffset) }
        haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    fun endDrag() {
        if (draggingId == null) return
        draggingId = null
        dragOffset = 0f
        haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
        commitOrder()
    }

    val moveUpLabel = stringResource(R.string.action_move_up)
    val moveDownLabel = stringResource(R.string.action_move_down)

    Scaffold(
        // The shell's own Scaffold already keeps the page clear of the system bars and the bottom bar.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        modifier = modifier,
        topBar = { DetailTopBar(title = stringResource(R.string.account_types_title)) },
        floatingActionButton = {
            ScreenFab(
                label = stringResource(R.string.new_account_type),
                icon = R.drawable.add_48px,
                onClick = { adding = true },
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
            // Rows of the block sit a hair apart, the way a settings group is drawn.
            verticalArrangement = Arrangement.spacedBy(SegmentedGap),
        ) {
            // One row per type, its accounts counted at the end, drawn as one connected block.
            itemsIndexed(visible, key = { _, type -> type.id }) { index, type ->
                val archiveKey = "type-archive:${type.id}"
                val deleteKey = "type-delete:${type.id}"
                val dragging = type.id == draggingId
                SwipeToRevealActions(
                    // The dragged row follows the finger above the rest; the others slide aside as it passes.
                    modifier = if (dragging) {
                        Modifier.fillMaxWidth().zIndex(1f).graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.fillMaxWidth().animateItem()
                    },
                    actions = {
                        ActionIconButton(
                            if (type.archived) ActionIcon.RESTORE else ActionIcon.ARCHIVE,
                            stringResource(R.string.cd_archive_item, type.name),
                            {
                                busy.run(
                                    archiveKey,
                                    snackbarHostState,
                                    successMessage = if (type.archived) typeRestoredMessage else typeArchivedMessage,
                                ) { onSetArchived(type.id, !type.archived) }
                            },
                            loading = busy.isBusy(archiveKey),
                        )
                        // A type still in use cannot be deleted: the API reports how many accounts hold it.
                        ActionIconButton(
                            ActionIcon.DELETE,
                            stringResource(R.string.cd_delete_item, type.name),
                            { busy.run(deleteKey, snackbarHostState, successMessage = typeDeletedMessage) { onDelete(type.id) } },
                            danger = true,
                            enabled = type.accountCount == 0,
                            loading = busy.isBusy(deleteKey),
                        )
                    },
                ) {
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { renaming = type },
                        shapes = groupedListItemShapes(positionInGroup(index, visible.lastIndex)),
                        colors = ListItemDefaults.colors(containerColor = groupedItemColor),
                        content = { Text(if (type.archived) stringResource(R.string.name_archived, type.name) else type.name) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (type.accountCount == 0) {
                                        stringResource(R.string.account_type_not_in_use)
                                    } else {
                                        pluralStringResource(R.plurals.account_type_account_count, type.accountCount, type.accountCount)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (!type.archived) {
                                    val position = order.indexOfFirst { it.id == type.id }
                                    val handleLabel = stringResource(R.string.cd_reorder_item, type.name)
                                    // The handle is the only place a drag starts, so the row's own swipe stays sideways.
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .semantics {
                                                contentDescription = handleLabel
                                                customActions = buildList {
                                                    if (position > 0) add(CustomAccessibilityAction(moveUpLabel) { move(position, position - 1); true })
                                                    if (position < order.lastIndex) add(CustomAccessibilityAction(moveDownLabel) { move(position, position + 1); true })
                                                }
                                            }
                                            .pointerInput(type.id) {
                                                detectDragGestures(
                                                    onDragStart = {
                                                        draggingId = type.id
                                                        dragOffset = 0f
                                                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                                    },
                                                    onDragEnd = ::endDrag,
                                                    onDragCancel = ::endDrag,
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragBy(amount.y)
                                                    },
                                                )
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(painterResource(R.drawable.drag_indicator_48px), contentDescription = null)
                                    }
                                }
                            }
                        },
                    )
                }
            }

            if (archivedCount > 0) {
                item {
                    TextButton(onClick = { showArchived = !showArchived }) {
                        Text(
                            stringResource(
                                if (showArchived) R.string.archived_toggle_hide else R.string.archived_toggle_show,
                                archivedCount,
                            ),
                        )
                    }
                }
            }
        }
    }

    val editing = renaming
    if (adding || editing != null) {
        val nameKey = "type-name"
        AccountTypeNameDialog(
            type = editing,
            existing = types,
            submitting = busy.isBusy(nameKey),
            onSave = { name ->
                busy.run(
                    nameKey,
                    snackbarHostState,
                    successMessage = if (editing != null) typeRenamedMessage else typeAddedMessage,
                    onSuccess = { adding = false; renaming = null },
                ) {
                    if (editing != null) onRename(editing.id, name) else onCreate(name)
                }
            },
            onCancel = { adding = false; renaming = null },
        )
    }
}

/** A type is only its name, so adding one and renaming one are the same single field in a basic dialog. */
@Composable
private fun AccountTypeNameDialog(
    type: AccountType?,
    existing: List<AccountType>,
    submitting: Boolean,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(type?.name ?: "") }
    // Types are unique by name, so a name is checked against the others (a rename may keep its own).
    val form = rememberFormValidation()
    val nameField = form.field(
        "name",
        nameProblem(name, R.string.error_name_taken_account_type) { candidate -> existing.any { it.id != type?.id && it.name == candidate } },
    )

    AlertDialog(
        onDismissRequest = { if (!submitting) onCancel() },
        title = { Text(stringResource(if (type != null) R.string.rename_account_type else R.string.new_account_type_title)) },
        text = {
            YuukaTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.label_name),
                singleLine = true,
                enabled = !submitting,
                field = nameField,
            )
        },
        confirmButton = {
            Button(enabled = !submitting && form.valid(nameField), onClick = { onSave(name.trim()) }) {
                if (submitting) {
                    MutationLoadingIndicator()
                } else {
                    Text(stringResource(if (type != null) R.string.action_save else R.string.action_add))
                }
            }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !submitting) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Preview(showBackground = true)
@Composable
private fun AccountTypesScreenPreview() {
    ScreenPreview {
        AccountTypesScreenContent(AccountTypesUiState(PreviewData.accountTypes))
    }
}
