package dev.gavenda.yuuka.ui.common

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Cards sit on the tinted page background: white in light mode so they stand clear of it, the
 * default tonal step in dark mode, where a lighter card would glare against the dark page.
 */
@Composable
fun yuukaCardColors(): CardColors = CardDefaults.cardColors(containerColor = yuukaCardColor())

/** The card container colour itself, for something that has to sit flush on a card and hide what is behind it. */
@Composable
fun yuukaCardColor(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
}

/** The filled search pill's container: white in light mode like a card, a tonal step above the page in dark mode. */
@Composable
fun yuukaSearchContainer(): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.background.luminance() < 0.5f) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
}
