package dev.gavenda.yuuka.ui.common

import dev.gavenda.yuuka.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme

enum class ActionIcon { EDIT, ARCHIVE, RESTORE, DELETE, ADJUST, PAUSE, RESUME }

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
    val iconRes = when (icon) {
        ActionIcon.EDIT -> R.drawable.edit_48px
        ActionIcon.ARCHIVE -> R.drawable.archive_48px
        ActionIcon.RESTORE -> R.drawable.unarchive_48px
        ActionIcon.DELETE -> R.drawable.delete_48px
        ActionIcon.ADJUST -> R.drawable.balance_48px
        ActionIcon.PAUSE -> R.drawable.pause_48px
        ActionIcon.RESUME -> R.drawable.play_arrow_48px
    }

    IconButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading) {
        if (loading) {
            MutationLoadingIndicator()
        } else {
            Icon(painterResource(iconRes), contentDescription = label, tint = if (danger && enabled) MaterialTheme.colorScheme.error else LocalContentColor.current)
        }
    }
}
