package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.carousel.HorizontalUncontainedCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.domain.DEFAULT_CURRENCY

/** One card in a [StatCarousel]; the [StatCard] parameters that differ from stat to stat. */
data class StatItem(
    val label: String,
    val amount: Long,
    val currency: String = DEFAULT_CURRENCY,
    val caption: String? = null,
    val signed: Boolean = false,
    val compact: Boolean = false,
)

/**
 * A Material 3 uncontained carousel of [StatCard]s. Every card keeps its full width and the next
 * one peeks in from the edge, so a related group of figures shares one row instead of a stack.
 *
 * The uncontained layout, rather than multi-browse, because the cards are text: multi-browse
 * would shrink the trailing ones to a small size that can't hold a figure.
 *
 * [edgeBleed] is the horizontal padding of the list this sits in. The carousel is widened by that
 * much on both sides so its items scroll to the screen edge as Material intends, while the first
 * one still lines up with the rest of the content.
 */
@Composable
fun StatCarousel(stats: List<StatItem>, modifier: Modifier = Modifier, edgeBleed: Dp = 16.dp) {
    val state = rememberCarouselState { stats.size }
    HorizontalUncontainedCarousel(
        state = state,
        itemWidth = StatItemWidth,
        modifier = modifier.bleed(edgeBleed),
        itemSpacing = 12.dp,
        contentPadding = PaddingValues(horizontal = edgeBleed),
    ) { index ->
        val stat = stats[index]
        val itemInfo = carouselItemDrawInfo
        // The carousel crops a partly visible item symmetrically around its centre, which is right for
        // an image but would slice the padding and first letters off a card. So the card keeps its full
        // layout and is instead slid to sit against the visible edge: its start for an item arriving
        // from the end, its end for one leaving at the start.
        Box(Modifier.maskClip(CardDefaults.shape)) {
            StatCard(
                label = stat.label,
                amount = stat.amount,
                modifier = Modifier.graphicsLayer {
                    val mask = itemInfo.maskRect
                    translationX = if (index < state.currentItem) mask.right - size.width else mask.left
                },
                currency = stat.currency,
                caption = stat.caption,
                signed = stat.signed,
                compact = stat.compact,
                onClick = {},
            )
        }
    }
}

/** Wide enough for a large figure and a one-line caption, narrow enough that the next card peeks in on a phone. */
private val StatItemWidth = 232.dp

internal fun Modifier.bleed(amount: Dp) = layout { measurable, constraints ->
    val bleed = amount.roundToPx()
    val width = constraints.maxWidth + bleed * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.placeRelative(-bleed, 0) }
}
