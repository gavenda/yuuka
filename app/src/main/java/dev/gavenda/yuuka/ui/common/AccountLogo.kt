package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
 * image. A [bank] that names a preset brings its own logo — a dark-mode one
 * where it has one — and inversion, and [logoUrl] and [invertDark] are not
 * read. Mirrors `AccountLogo.vue`.
 */
@Composable
fun AccountLogo(name: String, bank: String?, logoUrl: String?, invertDark: Boolean, modifier: Modifier = Modifier, size: Int = 40) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val preset = Banks.byKey(bank)
    var failed by remember(logoUrl) { mutableStateOf(false) }

    if (preset != null) {
        // A drawable in the app, so it cannot fail to load. A bank's dark-mode logo is already drawn for a
        // dark surface, so only a single logo is inverted. As wide as the web lets a logo stretch (`maxAspect`).
        Image(
            painter = painterResource(preset.logoFor(dark)),
            contentDescription = "${preset.name} logo",
            modifier = modifier.heightIn(max = size.dp).widthIn(max = (size * 5).dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (dark && preset.invertDark && preset.logoDark == null) ColorFilter.colorMatrix(INVERT_MATRIX) else null,
        )
    } else if (logoUrl != null && !failed) {
        AsyncImage(
            model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current).data(logoUrl).crossfade(true).build(),
            contentDescription = "$name logo",
            modifier = modifier.heightIn(max = size.dp),
            contentScale = ContentScale.Fit,
            colorFilter = if (invertDark && dark) ColorFilter.colorMatrix(INVERT_MATRIX) else null,
            onError = { failed = true },
        )
    } else {
        Box(
            modifier = modifier
                .heightIn(max = size.dp)
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
