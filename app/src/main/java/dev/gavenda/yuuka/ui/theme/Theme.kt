package dev.gavenda.yuuka.ui.theme

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.material3.DynamicMaterialExpressiveTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YuukaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the scheme is the brand seed's; a user can still opt into the wallpaper's
    // accent from Settings (always available: minSdk is past Android 12).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

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

    // The two schemes come from different theme composables, so the content is called from two
    // places. Moved rather than recomposed from scratch, it keeps its state across the switch:
    // otherwise flipping the setting rebuilds the whole app, back stack included, and the Settings
    // screen that holds the switch is replaced by the start destination.
    val currentContent by rememberUpdatedState(content)
    val movableContent = remember { movableContentOf { currentContent() } }

    if (dynamicColor) {
        MaterialExpressiveTheme(
            colorScheme = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            motionScheme = MotionScheme.expressive(),
            content = movableContent,
        )
    } else {
        DynamicMaterialExpressiveTheme(
            seedColor = SeedColor,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2026,
            motionScheme = MotionScheme.expressive(),
            isDark = darkTheme,
            animate = true,
            animationSpec = tween(durationMillis = 300),
            content = movableContent,
        )
    }
}
