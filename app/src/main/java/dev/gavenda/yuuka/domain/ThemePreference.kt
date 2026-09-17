package dev.gavenda.yuuka.domain

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "yuuka.theme"
private const val KEY_MODE = "mode"
private const val KEY_DYNAMIC_COLOR = "dynamic_color"

enum class ThemeMode {
    system,
    light,
    dark,
}

/**
 * How the app should be themed — a local display preference, like
 * [AmountVisibility], rather than a server-side setting: it depends on this
 * device's display and Android version, so it shouldn't follow the account
 * onto someone else's device. Provided as a Koin singleton so every screen
 * shares the same values.
 */
class ThemePreference(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        runCatching { ThemeMode.valueOf(prefs.getString(KEY_MODE, null) ?: ThemeMode.system.name) }
            .getOrDefault(ThemeMode.system),
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }
}
