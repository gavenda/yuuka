package dev.gavenda.yuuka.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

private val Base = Typography()

/**
 * The Material 3 type scale, with the display and headline tiers emphasised. The app only
 * uses them for figures and the brand mark (net worth, budgets, planned income), and those
 * read better heavier than the scale's regular weight, so the weight lives here rather
 * than as an override at each call site.
 */
val Typography = Base.copy(
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
)

/**
 * The emphasised figure for a compact stat card, one step below [Typography.headlineMedium].
 * `titleLarge` itself stays regular because top app bars use it.
 */
val Typography.compactFigure: TextStyle
    get() = titleLarge.copy(fontWeight = FontWeight.SemiBold)
