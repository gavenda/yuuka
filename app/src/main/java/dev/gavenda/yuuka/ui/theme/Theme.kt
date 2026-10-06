package dev.gavenda.yuuka.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.material3.DynamicMaterialExpressiveTheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.material3.rememberDynamicMaterialThemeState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YuukaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the scheme matches the webapp's brand seed; a user can still opt into the
    // wallpaper's accent from Settings (always available: minSdk is past Android 12).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // The system's own accent, taken as the seed so MaterialKolor still builds the whole expressive scheme from it.
    val context = LocalContext.current
    val dynamicThemeState = rememberDynamicMaterialThemeState(
        isDark = darkTheme,
        style = PaletteStyle.TonalSpot,
        specVersion = ColorSpec.SpecVersion.SPEC_2026,
        seedColor = SeedColor,
        modifyColorScheme = { colorScheme ->
            if (dynamicColor) {
                if (darkTheme)
                    dynamicDarkColorScheme(context) else
                    dynamicLightColorScheme(context)
            } else colorScheme
        }
    )

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
        state = dynamicThemeState,
        motionScheme = MotionScheme.expressive(),
        animate = true,
        content = content,
    )
}
