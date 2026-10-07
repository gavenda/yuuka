package dev.gavenda.yuuka.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.hypot

/*
 * The print on the pyjamas Yuuka wears in the logo, and the app's one piece of decoration: a round head with two
 * pointed ears, drawn as an outline with its eyes and the insides of its ears filled. It goes where a screen has
 * nothing else to show — signing in, loading, an empty list — and as a faint mark on the rail and the dashboard's
 * first card, never behind a list, a form or a chart. The paths are SVG path data in a 100 by 80 box, the same
 * strings as `lib/cat.ts`; `CatPattern.vue` and `CatMark.vue` are this file's twins.
 */
private const val HEAD = "M 14 36 L 13 14 Q 13 6 21 10 L 38 20 Q 50 16 62 20 L 79 10 Q 87 6 87 14 L 86 36 C 100 46 98 74 50 74 C 2 74 0 46 14 36 Z"
private const val EYES = "M 32 52 a 4 5 0 1 0 8 0 a 4 5 0 1 0 -8 0 Z M 60 52 a 4 5 0 1 0 8 0 a 4 5 0 1 0 -8 0 Z"
private const val EARS = "M 20 18 L 31 24 L 21 31 Z M 80 18 L 69 24 L 79 31 Z"

/** The left ear wound the other way, so that with [EYES] and the right ear it cuts a hole in a filled [HEAD]. */
private const val EAR_HOLES = "M 20 18 L 21 31 L 31 24 Z M 80 18 L 69 24 L 79 31 Z"

private fun parse(data: String): Path = PathParser().parsePathString(data).toPath()

private val headPath by lazy { parse(HEAD) }
private val eyesPath by lazy { parse(EYES) }
private val earsPath by lazy { parse(EARS) }
private val headStroke = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** How big the print is drawn: the motif's width and the cell each one has to itself. */
enum class CatPrint(val motifWidth: Dp, val cellWidth: Dp, val cellHeight: Dp) {
    /** A whole screen's backdrop. */
    Large(72.dp, 132.dp, 112.dp),

    /** A strip or a card. */
    Small(30.dp, 58.dp, 46.dp),
}

/** Where the print thins out. */
enum class CatFade {
    /** Towards the middle, so none sits behind what the screen says there. */
    Centre,

    /** From the left edge to nothing at the right. */
    Trailing,

    None,
}

/** The degrees a motif is turned by, taken in turn along a row and offset from one row to the next. */
private val MOTIF_TILTS = floatArrayOf(-14f, 9f, -6f, 13f)

private const val OUTLINE_ALPHA = 0.16f
private const val EARS_ALPHA = 0.28f

/**
 * The print as a backdrop, as it is over the fabric: the motif in staggered rows laid out from the middle of
 * [bounds] (the whole of what it is drawn behind, unless given), each tilted a little differently from its
 * neighbours. The colours are translucent, so it shows over whatever is already there. [alpha] fades the lot.
 */
@Composable
fun Modifier.catPattern(
    print: CatPrint = CatPrint.Large,
    fade: CatFade = CatFade.Centre,
    alpha: Float = 1f,
    bounds: (DrawScope.() -> Rect)? = null,
): Modifier {
    val outline = MaterialTheme.colorScheme.primary
    val ears = MaterialTheme.colorScheme.tertiary
    if (alpha <= 0f) return this
    return drawBehind {
        val area = bounds?.invoke(this) ?: size.toRect()
        val middle = area.center
        val cellWidth = print.cellWidth.toPx()
        val cellHeight = print.cellHeight.toPx()
        val unit = print.motifWidth.toPx() / 100f
        val rows = ceil(area.height / 2 / cellHeight).toInt() + 1
        val columns = ceil(area.width / 2 / cellWidth).toInt() + 1
        clipRect(area.left, area.top, area.right, area.bottom) {
            for (row in -rows..rows) {
                val shift = if (row % 2 == 0) 0f else 0.5f
                for (column in -columns..columns) {
                    val x = middle.x + (column + shift) * cellWidth
                    val y = middle.y + row * cellHeight
                    val strength = alpha * when (fade) {
                        // 0 in the middle, 1 at the nearer edges.
                        CatFade.Centre -> {
                            val distance = hypot((x - middle.x) / (area.width / 2), (y - middle.y) / (area.height / 2))
                            ((distance - 0.4f) / 0.5f).coerceIn(0f, 1f)
                        }
                        CatFade.Trailing -> (1f - (x - area.left) / area.width).coerceIn(0f, 1f)
                        CatFade.None -> 1f
                    }
                    if (strength == 0f) continue
                    withTransform({
                        translate(x, y)
                        rotate(MOTIF_TILTS[(column + 2 * row).mod(MOTIF_TILTS.size)], pivot = Offset.Zero)
                        scale(unit, unit, pivot = Offset.Zero)
                        translate(-50f, -40f)
                    }) {
                        drawPath(headPath, outline, OUTLINE_ALPHA * strength, headStroke)
                        drawPath(eyesPath, outline, OUTLINE_ALPHA * strength)
                        drawPath(earsPath, ears, EARS_ALPHA * strength)
                    }
                }
            }
        }
    }
}

/** One motif in the print's own colours, for a screen with nothing to list. Mirrors `CatMark.vue`. */
@Composable
fun CatMark(modifier: Modifier = Modifier, width: Dp = 64.dp) {
    val outline = MaterialTheme.colorScheme.primary
    val ears = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.size(width, width * 0.8f)) {
        scale(size.width / 100f, pivot = Offset.Zero) {
            drawPath(headPath, outline, style = headStroke)
            drawPath(eyesPath, outline)
            drawPath(earsPath, ears)
        }
    }
}

/**
 * One large motif let into the bottom corner of a card, in the card's own content [color] and faint enough to
 * read a figure over: a watermark, cut off by the card's edge.
 */
fun Modifier.catWatermark(color: Color): Modifier = drawBehind {
    val unit = WATERMARK_WIDTH.toPx() / 100f
    val inset = Offset(34.dp.toPx(), 22.dp.toPx())
    clipRect {
        withTransform({
            translate(size.width - inset.x, size.height - inset.y)
            rotate(-12f, pivot = Offset.Zero)
            scale(unit, unit, pivot = Offset.Zero)
            translate(-50f, -40f)
        }) {
            drawPath(headPath, color, WATERMARK_ALPHA, headStroke)
            drawPath(eyesPath, color, WATERMARK_ALPHA)
            drawPath(earsPath, color, WATERMARK_ALPHA)
        }
    }
}

private val WATERMARK_WIDTH = 128.dp
private const val WATERMARK_ALPHA = 0.14f

/** The motif as an icon: the head filled, with the eyes and ears cut out of it, so it takes a tint like any other. */
val CatIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Cat", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 100f, viewportHeight = 100f)
        .addGroup(translationY = 10f)
        .addPath(pathData = PathParser().parsePathString("$HEAD $EYES $EAR_HOLES").toNodes(), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
}
