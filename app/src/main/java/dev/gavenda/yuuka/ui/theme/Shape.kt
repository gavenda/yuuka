package dev.gavenda.yuuka.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The M3 corner scale, named to match the webapp's `--radius-*` tokens (`webapp/src/style.css`)
 * role for role, so a card, chip or joint reads the same size on both. Compose's own `Shapes()`
 * defaults already sit on this scale (`extraSmall`..`extraLarge` = 4/8/12/16/28dp) — these are for
 * call sites that clip or draw a shape directly rather than through a component's `shape` slot.
 */
object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 28.dp
}

val ShapeXl = RoundedCornerShape(Radius.xl)
