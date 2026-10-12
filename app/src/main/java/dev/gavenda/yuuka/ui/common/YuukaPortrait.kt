package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.gavenda.yuuka.R

/**
 * The faces Yuuka makes, named for what she is feeling rather than for the art they are cut from. Each is a
 * portrait written by `scripts/brand_assets.py`. `lib/yuuka.ts` lists the same ones for the web, and one more
 * (worried) for a screen only the web has.
 */
enum class YuukaMood(@DrawableRes internal val art: Int) {
    Neutral(R.drawable.yuuka_neutral),

    /** Listening, ready to start counting. */
    Attentive(R.drawable.yuuka_attentive),

    /** About to hold someone to a budget. */
    Stern(R.drawable.yuuka_stern),

    Gentle(R.drawable.yuuka_gentle),
    Pleased(R.drawable.yuuka_pleased),

    /** She has looked, and it is not there. */
    Uneasy(R.drawable.yuuka_uneasy),

    /** The day's books are closed. */
    Asleep(R.drawable.yuuka_asleep),
}

/**
 * Yuuka herself, where a screen has nothing else to show — an empty list, or the end of one — and in About. A scalloped badge —
 * the hem of her pyjamas — in the secondary container, which is their blue, masks her: from its middle down she is
 * cut off at its edge, and her head, bow and halo stand clear above it. She is decoration, like the cat print, so
 * she is not described to a screen reader: the words beside her say what the screen has to. Never beside a
 * figure, in any other form, a chart or a dialog. Mirrors `YuukaPortrait.vue`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YuukaPortrait(mood: YuukaMood, modifier: Modifier = Modifier, width: Dp = 192.dp) {
    val badge = MaterialShapes.Cookie12Sided.toShape()
    val mask = remember(badge) { RisingFrom(badge) }
    Box(modifier = modifier.size(width, width * PORTRAIT_HEIGHT), contentAlignment = Alignment.BottomCenter) {
        // The badge is as wide as the art and sits on its foot.
        Box(modifier = Modifier.size(width).background(MaterialTheme.colorScheme.secondaryContainer, badge))
        Image(
            painter = painterResource(mood.art),
            contentDescription = null,
            modifier = Modifier.matchParentSize().clip(mask),
        )
    }
}

/** The art's height for a width of one: it is 620 by 740. */
private const val PORTRAIT_HEIGHT = 740f / 620f

/**
 * Something Yuuka says, in a bubble with a tail on its start side: it goes after her [YuukaPortrait] in a row,
 * where the tail, halfway up the bubble, points back at her. About and the end of the transaction list have the only ones. Mirrors `SpeechBubble.vue`.
 */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, SpeechBubbleShape)
            .padding(start = BUBBLE_TAIL + 16.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
    )
}

/**
 * Yuuka saying a line: her [YuukaPortrait] with a [SpeechBubble] beside it, as tall as the badge she is in so the
 * tail is level with her face. Mirrors `YuukaSays.vue`.
 */
@Composable
fun YuukaSays(mood: YuukaMood, text: String, modifier: Modifier = Modifier, width: Dp = 96.dp) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Bottom,
    ) {
        YuukaPortrait(mood, width = width)
        Box(modifier = Modifier.weight(1f, fill = false).heightIn(min = width), contentAlignment = Alignment.CenterStart) {
            SpeechBubble(text)
        }
    }
}

/** A rounded box, less a strip on its start side that only the tail crosses. */
private object SpeechBubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tail = with(density) { BUBBLE_TAIL.toPx() }
        val corner = with(density) { BUBBLE_CORNER.toPx() }
        val rtl = layoutDirection == LayoutDirection.Rtl
        // Where the tail meets the box, and where it comes to its point.
        val root = if (rtl) size.width - tail else tail
        val tip = if (rtl) size.width else 0f
        val middle = size.height / 2
        val box = Path().apply {
            val bounds = if (rtl) Rect(0f, 0f, root, size.height) else Rect(root, 0f, size.width, size.height)
            addRoundRect(RoundRect(bounds, CornerRadius(corner)))
        }
        val point = Path().apply {
            moveTo(root, middle - tail)
            lineTo(tip, middle)
            lineTo(root, middle + tail)
            close()
        }
        return Outline.Generic(Path.combine(PathOperation.Union, box, point))
    }
}

private val BUBBLE_TAIL = 8.dp
private val BUBBLE_CORNER = 20.dp

/**
 * What the art is cut to: everything above the middle of a [badge] that is as wide as the art and sits on its
 * foot, and beneath that only what is inside the badge.
 */
private class RisingFrom(private val badge: Shape) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = size.width
        val top = size.height - side
        val inside = Path().apply {
            addOutline(badge.createOutline(Size(side, side), layoutDirection, density))
            translate(Offset(0f, top))
        }
        val above = Path().apply { addRect(Rect(0f, 0f, side, top + side / 2)) }
        return Outline.Generic(Path.combine(PathOperation.Union, above, inside))
    }
}
