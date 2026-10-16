package dev.gavenda.yuuka.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.lerp
import com.materialkolor.MaterialKolors
import com.materialkolor.dynamiccolor.DynamicScheme

/**
 * Material's scheme read off MaterialKolor's, role for role. MaterialKolor is only its core here, which knows
 * nothing of Material 3, so the Material 3 version can move without waiting on it.
 */
fun DynamicScheme.toColorScheme(): ColorScheme {
    val colors = MaterialKolors(this)
    return ColorScheme(
        primary = colors.primary(),
        onPrimary = colors.onPrimary(),
        primaryContainer = colors.primaryContainer(),
        onPrimaryContainer = colors.onPrimaryContainer(),
        inversePrimary = colors.inversePrimary(),
        secondary = colors.secondary(),
        onSecondary = colors.onSecondary(),
        secondaryContainer = colors.secondaryContainer(),
        onSecondaryContainer = colors.onSecondaryContainer(),
        tertiary = colors.tertiary(),
        onTertiary = colors.onTertiary(),
        tertiaryContainer = colors.tertiaryContainer(),
        onTertiaryContainer = colors.onTertiaryContainer(),
        background = colors.background(),
        onBackground = colors.onBackground(),
        surface = colors.surface(),
        onSurface = colors.onSurface(),
        surfaceVariant = colors.surfaceVariant(),
        onSurfaceVariant = colors.onSurfaceVariant(),
        surfaceTint = colors.surfaceTint(),
        inverseSurface = colors.inverseSurface(),
        inverseOnSurface = colors.inverseOnSurface(),
        error = colors.error(),
        onError = colors.onError(),
        errorContainer = colors.errorContainer(),
        onErrorContainer = colors.onErrorContainer(),
        outline = colors.outline(),
        outlineVariant = colors.outlineVariant(),
        scrim = colors.scrim(),
        surfaceBright = colors.surfaceBright(),
        surfaceDim = colors.surfaceDim(),
        surfaceContainer = colors.surfaceContainer(),
        surfaceContainerHigh = colors.surfaceContainerHigh(),
        surfaceContainerHighest = colors.surfaceContainerHighest(),
        surfaceContainerLow = colors.surfaceContainerLow(),
        surfaceContainerLowest = colors.surfaceContainerLowest(),
        primaryFixed = colors.primaryFixed(),
        primaryFixedDim = colors.primaryFixedDim(),
        onPrimaryFixed = colors.onPrimaryFixed(),
        onPrimaryFixedVariant = colors.onPrimaryFixedVariant(),
        secondaryFixed = colors.secondaryFixed(),
        secondaryFixedDim = colors.secondaryFixedDim(),
        onSecondaryFixed = colors.onSecondaryFixed(),
        onSecondaryFixedVariant = colors.onSecondaryFixedVariant(),
        tertiaryFixed = colors.tertiaryFixed(),
        tertiaryFixedDim = colors.tertiaryFixedDim(),
        onTertiaryFixed = colors.onTertiaryFixed(),
        onTertiaryFixedVariant = colors.onTertiaryFixedVariant(),
    )
}

/**
 * [target], faded into from whatever was on screen when it changed — light to dark and back. One animation
 * drives every role; a change mid-fade starts the next one from the colours showing at that moment.
 */
@Composable
fun animateColorScheme(target: ColorScheme, animationSpec: AnimationSpec<Float>): ColorScheme {
    val onScreen = remember { OnScreen(target) }
    val from = remember(target) { onScreen.scheme }
    val fraction = remember(target) { Animatable(if (from == target) 1f else 0f) }
    LaunchedEffect(fraction) { fraction.animateTo(1f, animationSpec) }

    val scheme = if (fraction.value >= 1f) target else lerp(from, target, fraction.value)
    SideEffect { onScreen.scheme = scheme }
    return scheme
}

private class OnScreen(var scheme: ColorScheme)

private fun lerp(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme = b.copy(
    primary = lerp(a.primary, b.primary, t),
    onPrimary = lerp(a.onPrimary, b.onPrimary, t),
    primaryContainer = lerp(a.primaryContainer, b.primaryContainer, t),
    onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, t),
    inversePrimary = lerp(a.inversePrimary, b.inversePrimary, t),
    secondary = lerp(a.secondary, b.secondary, t),
    onSecondary = lerp(a.onSecondary, b.onSecondary, t),
    secondaryContainer = lerp(a.secondaryContainer, b.secondaryContainer, t),
    onSecondaryContainer = lerp(a.onSecondaryContainer, b.onSecondaryContainer, t),
    tertiary = lerp(a.tertiary, b.tertiary, t),
    onTertiary = lerp(a.onTertiary, b.onTertiary, t),
    tertiaryContainer = lerp(a.tertiaryContainer, b.tertiaryContainer, t),
    onTertiaryContainer = lerp(a.onTertiaryContainer, b.onTertiaryContainer, t),
    background = lerp(a.background, b.background, t),
    onBackground = lerp(a.onBackground, b.onBackground, t),
    surface = lerp(a.surface, b.surface, t),
    onSurface = lerp(a.onSurface, b.onSurface, t),
    surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, t),
    onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, t),
    surfaceTint = lerp(a.surfaceTint, b.surfaceTint, t),
    inverseSurface = lerp(a.inverseSurface, b.inverseSurface, t),
    inverseOnSurface = lerp(a.inverseOnSurface, b.inverseOnSurface, t),
    error = lerp(a.error, b.error, t),
    onError = lerp(a.onError, b.onError, t),
    errorContainer = lerp(a.errorContainer, b.errorContainer, t),
    onErrorContainer = lerp(a.onErrorContainer, b.onErrorContainer, t),
    outline = lerp(a.outline, b.outline, t),
    outlineVariant = lerp(a.outlineVariant, b.outlineVariant, t),
    scrim = lerp(a.scrim, b.scrim, t),
    surfaceBright = lerp(a.surfaceBright, b.surfaceBright, t),
    surfaceDim = lerp(a.surfaceDim, b.surfaceDim, t),
    surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, t),
    surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, t),
    surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, t),
    surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, t),
    primaryFixed = lerp(a.primaryFixed, b.primaryFixed, t),
    primaryFixedDim = lerp(a.primaryFixedDim, b.primaryFixedDim, t),
    onPrimaryFixed = lerp(a.onPrimaryFixed, b.onPrimaryFixed, t),
    onPrimaryFixedVariant = lerp(a.onPrimaryFixedVariant, b.onPrimaryFixedVariant, t),
    secondaryFixed = lerp(a.secondaryFixed, b.secondaryFixed, t),
    secondaryFixedDim = lerp(a.secondaryFixedDim, b.secondaryFixedDim, t),
    onSecondaryFixed = lerp(a.onSecondaryFixed, b.onSecondaryFixed, t),
    onSecondaryFixedVariant = lerp(a.onSecondaryFixedVariant, b.onSecondaryFixedVariant, t),
    tertiaryFixed = lerp(a.tertiaryFixed, b.tertiaryFixed, t),
    tertiaryFixedDim = lerp(a.tertiaryFixedDim, b.tertiaryFixedDim, t),
    onTertiaryFixed = lerp(a.onTertiaryFixed, b.onTertiaryFixed, t),
    onTertiaryFixedVariant = lerp(a.onTertiaryFixedVariant, b.onTertiaryFixedVariant, t),
)
