package dev.gavenda.yuuka.domain

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Categorical palette for category colours.
 *
 * The eight slots are assigned in fixed order and never cycled: the ordering is
 * what keeps adjacent series apart under colour-vision deficiency, so a ninth
 * category reuses a slot rather than inventing a hue. Each slot carries its own
 * dark-mode step — the dark column is these hues re-stepped for a dark surface,
 * not an automatic lightening of the light one. Mirrors the validated palette
 * in the web app's `src/lib/palette.ts`.
 */
data class PaletteSlot(val name: String, val light: String, val dark: String)

val PALETTE = listOf(
    PaletteSlot("Blue", "#2a78d6", "#3987e5"),
    PaletteSlot("Orange", "#eb6834", "#d95926"),
    PaletteSlot("Aqua", "#1baf7a", "#199e70"),
    PaletteSlot("Yellow", "#eda100", "#c98500"),
    PaletteSlot("Magenta", "#e87ba4", "#d55181"),
    PaletteSlot("Green", "#008300", "#008300"),
    PaletteSlot("Violet", "#4a3aa7", "#9085e9"),
    PaletteSlot("Red", "#e34948", "#e66767"),
)

private val DARK_BY_LIGHT: Map<String, String> = PALETTE.associate { it.light.lowercase() to it.dark }

/** Maps a stored (light) colour onto its dark-mode step, leaving custom hexes alone. */
fun paletteForMode(color: String, dark: Boolean): String {
    if (!dark) return color
    return DARK_BY_LIGHT[color.lowercase()] ?: color
}

/** The slot a new category should take, so defaults spread across the palette. */
fun nextColor(existingCount: Int): String = PALETTE[existingCount % PALETTE.size].light

enum class BudgetHealth { GOOD, WARNING, CRITICAL }

/**
 * Status colours are reserved for state and never used as a series colour.
 * Each is paired with a visible label wherever it appears. They come from the
 * theme, so they follow dynamic colour: GOOD just means "on track" and borrows
 * the primary colour, WARNING is `tertiary` and CRITICAL is `error`.
 */
@Composable
fun statusColor(health: BudgetHealth): Color = when (health) {
    BudgetHealth.GOOD -> MaterialTheme.colorScheme.primary
    BudgetHealth.WARNING -> MaterialTheme.colorScheme.tertiary
    BudgetHealth.CRITICAL -> MaterialTheme.colorScheme.error
}

/**
 * Budget health from the share of a planned amount already spent. An unbudgeted
 * category reads as neutral, not a warning — there is no plan to be off track from.
 */
fun budgetStatus(actual: Long, planned: Long): BudgetHealth {
    if (planned <= 0) return BudgetHealth.GOOD
    val ratio = actual.toDouble() / planned.toDouble()
    return when {
        ratio > 1 -> BudgetHealth.CRITICAL
        ratio >= 0.85 -> BudgetHealth.WARNING
        else -> BudgetHealth.GOOD
    }
}
