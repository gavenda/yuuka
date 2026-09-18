package dev.gavenda.yuuka.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

enum class ActionIcon { EDIT, ARCHIVE, RESTORE, DELETE }

/** A row of icon-only actions sharing one visual language, mirroring `ActionIcon.vue`. */
@Composable
fun ActionIconButton(
    icon: ActionIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val imageVector = when (icon) {
        ActionIcon.EDIT -> Icons.Filled.Edit
        ActionIcon.ARCHIVE -> Icons.Filled.Archive
        ActionIcon.RESTORE -> Icons.Filled.Unarchive
        ActionIcon.DELETE -> Icons.Filled.Delete
    }
    val tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant

    IconButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading) {
        if (loading) {
            MutationLoadingIndicator(color = tint)
        } else {
            Icon(imageVector, contentDescription = label, tint = if (enabled) tint else MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
