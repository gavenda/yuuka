package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Where a row sits in the group it is drawn in, which is all its corners need to know. */
enum class ItemPosition { Top, Middle, Bottom, Single }

/** The position of the row at [index] in a group whose last row is [lastIndex]. */
fun positionInGroup(index: Int, lastIndex: Int): ItemPosition = when {
    lastIndex <= 0 -> ItemPosition.Single
    index == 0 -> ItemPosition.Top
    index == lastIndex -> ItemPosition.Bottom
    else -> ItemPosition.Middle
}

/**
 * A grouped row's shape: round on the group's outer edges, all but square where it meets the row
 * next to it. Rows are laid out a few dp apart, so the near-square corners read as one block of
 * separate pieces rather than a single card with dividers — the shape the settings groups use.
 */
fun groupedItemShape(position: ItemPosition, corner: Dp = 28.dp, joint: Dp = 4.dp): RoundedCornerShape = when (position) {
    ItemPosition.Top -> RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = joint, bottomEnd = joint)
    ItemPosition.Middle -> RoundedCornerShape(joint)
    ItemPosition.Bottom -> RoundedCornerShape(topStart = joint, topEnd = joint, bottomStart = corner, bottomEnd = corner)
    ItemPosition.Single -> RoundedCornerShape(corner)
}
