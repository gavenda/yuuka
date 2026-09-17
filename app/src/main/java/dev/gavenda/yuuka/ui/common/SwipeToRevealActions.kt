package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class RevealAnchor { CLOSED, OPEN }

/**
 * Wraps a list row so its trailing [actions] stay hidden until the user swipes [content] left,
 * instead of always being visible. Swiping right, or tapping the row while open, closes it again.
 *
 * [content] is measured first and its height is applied to [actions] directly (rather than via
 * `IntrinsicSize.Min` + `fillMaxHeight`), since intrinsic height doesn't reliably reflect the real
 * measured height of a row containing a weighted child, occasionally producing a much-too-tall row.
 */
@Composable
fun SwipeToRevealActions(
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    var actionsWidthPx by remember { mutableFloatStateOf(0f) }
    val state = remember { AnchoredDraggableState(initialValue = RevealAnchor.CLOSED) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(actionsWidthPx) {
        state.updateAnchors(
            DraggableAnchors {
                RevealAnchor.CLOSED at 0f
                RevealAnchor.OPEN at -actionsWidthPx
            },
        )
    }

    Layout(
        modifier = modifier,
        content = {
            Row(modifier = Modifier.layoutId("actions"), verticalAlignment = Alignment.CenterVertically, content = actions)

            Box(
                modifier = Modifier
                    .layoutId("content")
                    .offset { IntOffset(x = if (state.offset.isNaN()) 0 else state.offset.roundToInt(), y = 0) }
                    .anchoredDraggable(state, Orientation.Horizontal),
            ) {
                content()

                if (state.currentValue == RevealAnchor.OPEN || state.targetValue == RevealAnchor.OPEN) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { scope.launch { state.animateTo(RevealAnchor.CLOSED) } },
                    )
                }
            }
        },
    ) { measurables, constraints ->
        val contentPlaceable = measurables.first { it.layoutId == "content" }.measure(constraints.copy(minHeight = 0))
        val actionsPlaceable = measurables.first { it.layoutId == "actions" }.measure(
            Constraints(minHeight = contentPlaceable.height, maxHeight = contentPlaceable.height),
        )
        actionsWidthPx = actionsPlaceable.width.toFloat()

        layout(contentPlaceable.width, contentPlaceable.height) {
            actionsPlaceable.placeRelative(contentPlaceable.width - actionsPlaceable.width, 0)
            contentPlaceable.placeRelative(0, 0)
        }
    }
}
