package dev.gavenda.yuuka.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceBright = LightSurfaceBright,
    surfaceDim = LightSurfaceDim,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainerLowest = LightSurfaceContainerLowest,
)

@Composable
fun YuukaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            (if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).withTintedSurfaces(darkTheme)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides if (darkTheme) DarkExtendedColors else LightExtendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

// The surfaces lean towards the primary colour, so the page reads as a tinted field the cards sit on
// and the rail, cards and controls read as one colour rather than a blue page holding neutral-grey
// pieces. Both modes rebuild the surface ramp on the primary's hue, stepped in lightness: light mode
// keeps the cards white (`surfaceContainerLowest`) on a tinted page with the rail a shade lighter than
// it; dark mode goes rail < page < container < card < raised. Applied to the dynamic scheme, so it
// follows whichever primary is active; the static scheme spells the same ramps out in `Color.kt`.
// The web app mirrors that in `webapp/src/style.css`; change them together.
private fun ColorScheme.withTintedSurfaces(dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(primary.toArgb(), hsv)
    fun tone(lightness: Float, saturation: Float) = Color.hsl(hsv[0], saturation, lightness)

    return if (dark) {
        copy(
            surface = tone(0.08f, 0.31f),
            surfaceDim = tone(0.08f, 0.31f),
            background = tone(0.116f, 0.31f),
            surfaceContainerLowest = tone(0.063f, 0.31f),
            surfaceContainerLow = tone(0.135f, 0.31f),
            surfaceContainer = tone(0.169f, 0.31f),
            surfaceContainerHigh = tone(0.239f, 0.31f),
            surfaceContainerHighest = tone(0.302f, 0.25f),
            surfaceBright = tone(0.302f, 0.25f),
            outlineVariant = tone(0.28f, 0.19f),
        )
    } else {
        copy(
            surface = tone(0.975f, 0.5f),
            surfaceBright = tone(0.975f, 0.5f),
            background = tone(0.94f, 0.5f),
            surfaceDim = tone(0.88f, 0.35f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(0.965f, 0.5f),
            surfaceContainer = tone(0.925f, 0.45f),
            surfaceContainerHigh = tone(0.895f, 0.42f),
            surfaceContainerHighest = tone(0.86f, 0.4f),
            outlineVariant = tone(0.8f, 0.25f),
        )
    }
}
