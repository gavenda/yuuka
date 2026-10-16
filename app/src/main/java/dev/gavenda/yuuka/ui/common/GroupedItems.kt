package dev.gavenda.yuuka.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.ui.theme.Radius

/** Where a row sits in the group it is drawn in, which is all its corners need to know. */
enum class ItemPosition { Top, Middle, Bottom, Single }

/** The position of the row at [index] in a group whose last row is [lastIndex]. */
fun positionInGroup(index: Int, lastIndex: Int): ItemPosition = when {
    lastIndex <= 0 -> ItemPosition.Single
    index == 0 -> ItemPosition.Top
    index == lastIndex -> ItemPosition.Bottom
    else -> ItemPosition.Middle
}

/** The position of a row that is or is not the [first] and the [last] of its group. */
fun positionInGroup(first: Boolean, last: Boolean): ItemPosition = when {
    first && last -> ItemPosition.Single
    first -> ItemPosition.Top
    last -> ItemPosition.Bottom
    else -> ItemPosition.Middle
}

/** The space between two rows of a group: Material's segmented list gap (`ListItemDefaults.SegmentedGap`). */
val SegmentedGap: Dp = 2.dp

/**
 * A grouped row's shape, as Material's segmented list draws it: large corners on the group's outer edges,
 * extra-small where it meets the row next to it. Rows lie [SegmentedGap] apart, so the block reads as one
 * block of separate pieces rather than a single card with dividers.
 */
private fun groupedItemShape(position: ItemPosition, corner: Dp = Radius.lg, joint: Dp = Radius.xs): RoundedCornerShape = when (position) {
    ItemPosition.Top -> RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = joint, bottomEnd = joint)
    ItemPosition.Middle -> RoundedCornerShape(joint)
    ItemPosition.Bottom -> RoundedCornerShape(topStart = joint, topEnd = joint, bottomStart = corner, bottomEnd = corner)
    ItemPosition.Single -> RoundedCornerShape(corner)
}

/**
 * [groupedItemShape] for a row drawn by hand, rounding off as Material's segmented list item does: every
 * corner large while [selected], pressed or focused, medium while hovered, back to its place in the group after.
 */
@Composable
fun animatedGroupedItemShape(
    position: ItemPosition,
    interactionSource: MutableInteractionSource,
    selected: Boolean = false,
): Shape {
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val hovered by interactionSource.collectIsHoveredAsState()
    val whole = when {
        selected || pressed || focused -> Radius.lg
        hovered -> Radius.md
        else -> null
    }
    val outerTop = position == ItemPosition.Top || position == ItemPosition.Single
    val outerBottom = position == ItemPosition.Bottom || position == ItemPosition.Single
    val spec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
    val top by animateDpAsState(whole ?: if (outerTop) Radius.lg else Radius.xs, spec)
    val bottom by animateDpAsState(whole ?: if (outerBottom) Radius.lg else Radius.xs, spec)
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * [groupedItemShape] for Material's own `ListItem`, which morphs between them itself. Material sizes a segmented
 * list by index and count; a three-row stand-in gives each position its corners. A [selected] row rests in
 * Material's selected shape instead, every corner large: the clickable `ListItem` only morphs on interaction,
 * so the selection is given to it as the shape it rests in.
 */
@Composable
fun groupedListItemShapes(position: ItemPosition, selected: Boolean = false): ListItemShapes {
    val shapes = when (position) {
        ItemPosition.Single -> ListItemDefaults.segmentedShapes(index = 0, count = 1)
        ItemPosition.Top -> ListItemDefaults.segmentedShapes(index = 0, count = 3)
        ItemPosition.Middle -> ListItemDefaults.segmentedShapes(index = 1, count = 3)
        ItemPosition.Bottom -> ListItemDefaults.segmentedShapes(index = 2, count = 3)
    }
    return if (selected) shapes.copy(shape = shapes.selectedShape) else shapes
}

/**
 * What a grouped row is painted: a step up from the page it lies on, so the block reads as a block,
 * and a step below the dialogs that open over it. Neutral on purpose — a row's own colour is its
 * category dot, its tags and its amount. (Material's segmented list paints rows `surface` on a
 * container-coloured page; the page here stays `surface`, so the rows take the step instead.)
 */
val groupedItemColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainer

/**
 * [ListItem][androidx.compose.material3.ListItem] colours for a grouped row: [groupedItemColor], or Material's
 * selected item's while it is [selected] — the row whose form stands in the supporting pane beside its list.
 */
@Composable
fun groupedListItemColors(selected: Boolean = false): ListItemColors = if (selected) {
    ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        leadingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        trailingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        supportingContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
} else {
    ListItemDefaults.colors(containerColor = groupedItemColor)
}

/**
 * One row of a segmented list drawn by hand: [groupedItemColor] in its [position]'s shape, which rounds off
 * while pressed. With an [onClick] the whole row takes the tap. [selected] paints and shapes it as Material marks
 * a list's selected item, for the row whose form stands in the supporting pane beside it.
 */
@Composable
fun GroupedItem(
    position: ItemPosition,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val target = if (selected) MaterialTheme.colorScheme.secondaryContainer else groupedItemColor
    val color by animateColorAsState(target, MaterialTheme.motionScheme.defaultEffectsSpec())
    val contentColor = MaterialTheme.colorScheme.contentColorFor(target)
    if (onClick == null) {
        val shape = if (selected) RoundedCornerShape(Radius.lg) else groupedItemShape(position)
        Surface(modifier = modifier, shape = shape, color = color, contentColor = contentColor, content = content)
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = animatedGroupedItemShape(position, interactionSource, selected),
            color = color,
            contentColor = contentColor,
            interactionSource = interactionSource,
            content = content,
        )
    }
}
