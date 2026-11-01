package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R

/**
 * The end of a list that pages itself, and only once there is no more of it to load: Yuuka, asleep over closed
 * books, saying so, so the last row is not mistaken for a page that stopped short. Transactions and the
 * investment activity end with it. Mirrors `ListEnd.vue`.
 */
@Composable
fun ListEnd(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        YuukaPortrait(YuukaMood.Asleep, width = 96.dp)
        // As tall as the badge she is in, so the tail is level with her face.
        Box(
            modifier = Modifier.weight(1f, fill = false).heightIn(min = 96.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            SpeechBubble(stringResource(R.string.transactions_end))
        }
    }
}

/**
 * The foot of a list that pages itself for as long as there is more of it to load. Reaching it has already asked for the next
 * page, so it only says one is on its way — or, when it could not be had, why, with a way to ask again.
 */
@Composable
fun NextPageFooter(error: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
        } else {
            Text(
                stringResource(R.string.loading_ellipsis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
