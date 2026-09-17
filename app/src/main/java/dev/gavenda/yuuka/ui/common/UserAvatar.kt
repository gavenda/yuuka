package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

/**
 * A circular avatar for the signed-in user: the Auth0 profile picture when it loads,
 * otherwise the account name's initial. [onClick] is left null for a non-interactive
 * display, such as the small summary at the top of the navigation drawer.
 */
@Composable
fun UserAvatar(name: String?, pictureUrl: String?, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier, size: Int = 32) {
    var failed by remember(pictureUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .let { base ->
                if (onClick != null) {
                    base.semantics { role = Role.Button }.clickable(onClickLabel = "Account", onClick = onClick)
                } else {
                    base
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (pictureUrl != null && !failed) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(pictureUrl).crossfade(true).build(),
                contentDescription = "Account",
                modifier = Modifier.size(size.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
            )
        } else {
            Text(
                text = name?.trim()?.take(1)?.uppercase()?.ifEmpty { "?" } ?: "?",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
