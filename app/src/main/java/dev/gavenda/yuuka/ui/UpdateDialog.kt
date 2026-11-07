package dev.gavenda.yuuka.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gavenda.yuuka.R
import dev.gavenda.yuuka.update.AppUpdater
import dev.gavenda.yuuka.update.UpdateState
import org.koin.compose.koinInject

/**
 * Offers a newer release on a fresh open (see [AppUpdater]): a question, as every confirmation is, answered
 * "Yes, update now" or "No, later". Updating downloads it in the dialog, then hands over to Android's installer.
 * If Android does not yet let Yuuka install apps, the first press opens that setting instead.
 */
@Composable
fun UpdateDialog(updater: AppUpdater = koinInject()) {
    LaunchedEffect(updater) { updater.checkOnce() }
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val release = when (val current = state) {
        UpdateState.Idle -> return
        is UpdateState.Available -> current.release
        is UpdateState.Downloading -> current.release
        is UpdateState.Failed -> current.release
    }
    val downloading = state as? UpdateState.Downloading
    // Asked again each time the screen resumes, so coming back from allowing installs changes the wording.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val canInstall = remember(lifecycleState) { updater.canInstall() }

    AlertDialog(
        // A tap outside does not put away a download under way; "No, later" does.
        onDismissRequest = { if (downloading == null) updater.dismiss() },
        icon = { Icon(painterResource(R.drawable.download_48px), contentDescription = null) },
        title = { Text(stringResource(R.string.update_confirm_title, release.version)) },
        text = {
            if (downloading != null) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.update_downloading))
                    val progress = downloading.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                Text(
                    stringResource(
                        when {
                            state is UpdateState.Failed -> R.string.update_failed_body
                            canInstall -> R.string.update_body
                            else -> R.string.update_permission_body
                        },
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = downloading == null,
                onClick = {
                    if (canInstall) updater.install(release)
                    else context.startActivity(updater.installPermissionIntent())
                },
            ) {
                Text(stringResource(if (state is UpdateState.Failed) R.string.action_yes_try_again else R.string.action_yes_update))
            }
        },
        dismissButton = {
            TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.action_no_later)) }
        },
    )
}
