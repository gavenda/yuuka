package dev.gavenda.yuuka.ui.common

import dev.gavenda.yuuka.R
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme

enum class ActionIcon { EDIT, ARCHIVE, RESTORE, DELETE, ADJUST, PAUSE, RESUME }

/**
 * One of a row's actions behind its swipe, mirroring `ActionIcon.vue`: a filled icon button, primary, or error
 * when it destroys something.
 */
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
    val colors = if (danger) {
        IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        IconButtonDefaults.filledIconButtonColors()
    }

    FilledIconButton(onClick = onClick, modifier = modifier, enabled = enabled && !loading, colors = colors) {
        if (loading) {
            MutationLoadingIndicator()
        } else {
            Icon(painterResource(iconRes), contentDescription = label)
        }
    }
}
