package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade

private val INVERT_MATRIX = ColorMatrix(
    floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/**
 * A remote URL can 404, expire, or simply not be an image — falling back to
 * the account's initial keeps the row intact rather than showing a broken
 * image. Mirrors `AccountLogo.vue`.
 */
@Composable
fun AccountLogo(name: String, logoUrl: String?, invertDark: Boolean, modifier: Modifier = Modifier, size: Int = 40) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    if (logoUrl != null && !failed) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(logoUrl).crossfade(true).build(),
            contentDescription = "$name logo",
            modifier = modifier.height(size.dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (invertDark && dark) ColorFilter.colorMatrix(INVERT_MATRIX) else null,
            onError = { failed = true },
        )
    } else {
        Box(
            modifier = modifier
                .size(size.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A faint, decorative mark in the corner of an account card. No fallback: absent or broken, the card simply has none. Mirrors `AccountWatermark.vue`. */
@Composable
fun AccountWatermark(logoUrl: String?, invertDark: Boolean, modifier: Modifier = Modifier) {
    var failed by remember(logoUrl) { mutableStateOf(false) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    if (logoUrl != null && !failed) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(logoUrl).crossfade(true).build(),
            contentDescription = null,
            modifier = modifier.height(20.dp).alpha(0.7f),
            contentScale = ContentScale.Fit,
            colorFilter = if (invertDark && dark) ColorFilter.colorMatrix(INVERT_MATRIX) else null,
            onError = { failed = true },
        )
    }
}
