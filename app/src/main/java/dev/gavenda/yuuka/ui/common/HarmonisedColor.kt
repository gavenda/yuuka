package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.blend.Blend

/**
 * A colour the user picked for a category or tag, nudged toward the theme's primary hue — at most 15° — so a
 * mark of it sits in the palette instead of fighting it (Material's "harmonize"). Only marks that are drawn
 * beside the theme go through this; the picker itself shows the value as it was chosen. Null when [hex] is
 * not a colour.
 */
@Composable
fun harmonisedColorOrNull(hex: String?): Color? {
    val primary = MaterialTheme.colorScheme.primary
    return remember(hex, primary) {
        hex?.let { runCatching { Color(Blend.harmonize(android.graphics.Color.parseColor(it), primary.toArgb())) }.getOrNull() }
    }
}

@Composable
fun harmonisedColor(hex: String?, fallback: Color): Color = harmonisedColorOrNull(hex) ?: fallback
