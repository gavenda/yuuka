package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** A transfer's "From → To", with the arrow drawn as an icon that scales with [style] rather than a unicode character. */
@Composable
fun AccountFlow(
    from: String,
    to: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val iconSize = with(LocalDensity.current) { style.fontSize.toDp() } + 4.dp
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(from, style = style, color = color)
        Icon(Icons.AutoMirrored.Filled.ArrowRightAlt, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
        Text(to, style = style, color = color)
    }
}
