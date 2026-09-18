package dev.gavenda.yuuka.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic colours the Material 3 scheme has no role for. Negative and "critical"
 * use `colorScheme.error` and transfers use `colorScheme.primary`, so they follow
 * dynamic colour; these two don't have a scheme equivalent. Built as M3 custom colours:
 * the hue is harmonised towards the brand seed and picked at the light/dark tones
 * the spec assigns to a role.
 */
data class ExtendedColors(
    /** Inflows, and anything that reads as "gained". */
    val positive: Color,
    /** Budgets nearing their plan. Between the theme's primary (on track) and error (over). */
    val warning: Color,
)

val LightExtendedColors = ExtendedColors(
    positive = Color(0xFF006C50),
    warning = Color(0xFFB46200),
)

val DarkExtendedColors = ExtendedColors(
    positive = Color(0xFF61DCB0),
    warning = Color(0xFFFFB77C),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
