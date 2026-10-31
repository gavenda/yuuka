package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.gavenda.yuuka.R

/**
 * Asks before deleting what cannot be had back from the list: the question as the headline under an
 * error-coloured icon, what goes with it as the body, and buttons worded as answers. While [deleting] it
 * spins and cannot be dismissed. It has no snackbar host, so a message never draws inside it: what the
 * action confirms is shown on the screen once the dialog has closed.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    body: String,
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.action_yes_delete),
) {
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        icon = { Icon(painterResource(R.drawable.delete_48px), contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !deleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) {
                if (deleting) MutationLoadingIndicator() else Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text(stringResource(R.string.action_no_go_back)) } },
    )
}
