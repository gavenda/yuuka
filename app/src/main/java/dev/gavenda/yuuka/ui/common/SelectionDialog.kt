package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R

/**
 * A short list to pick from, as Material 3's basic dialog: the title, the rows (`ExpressiveModalSelectionItem`s,
 * a hair apart) and one text button at the foot. A list to pick one of closes itself on the pick, so its
 * button is Cancel; one that toggles several stays open and passes "Done" as [dismissLabel], since every
 * tap has already been written.
 */
@Composable
fun SelectionDialog(
    title: String,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.action_cancel),
    content: LazyListScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}
