package dev.gavenda.yuuka.ui.theme

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.material3.DynamicMaterialExpressiveTheme

/**
 * The brand's scheme and nothing else: the wallpaper's colours are never used, so the app looks the same
 * on every device and the same as the web app, whose roles in `style.css` are this theme's output.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YuukaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
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

    DynamicMaterialExpressiveTheme(
        seedColor = SeedColor,
        // Given a colour of their own, these two palettes keep its hue and chroma instead of the seed's.
        secondary = SecondarySeed,
        tertiary = TertiarySeed,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2026,
        motionScheme = MotionScheme.expressive(),
        isDark = darkTheme,
        animate = true,
        animationSpec = tween(durationMillis = 300),
        content = content,
    )
}
