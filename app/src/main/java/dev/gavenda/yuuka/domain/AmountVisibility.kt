package dev.gavenda.yuuka.domain

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "yuuka.privacy"
private const val KEY_HIDDEN = "amounts_hidden"

/** What stands in for a figure while amounts are hidden. */
const val MASK = "••••"

private val NUMERIC_RUN = Regex("[0-9][0-9.,]*")

/**
 * Whether money is masked across the whole app — the "someone is looking over
 * my shoulder" switch.
 *
 * It is deliberately a local display preference rather than a server-side
 * setting: it protects the current screen, not the data, so it should take
 * effect instantly and not follow the account onto someone else's device.
 * Mirrors `src/lib/privacy.ts`. Provided as a Koin singleton so every screen
 * shares the same toggle.
 */
class AmountVisibility(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hidden = MutableStateFlow(prefs.getBoolean(KEY_HIDDEN, false))
    val hidden: StateFlow<Boolean> = _hidden.asStateFlow()

    fun toggle() {
        val next = !_hidden.value
        _hidden.value = next
        prefs.edit().putBoolean(KEY_HIDDEN, next).apply()
    }

    /**
     * `formatMoney`, with the figure masked while amounts are hidden.
     *
     * Every amount the user sees should render through here rather than
     * `formatMoney` directly, so the toggle cannot miss one. The currency stays
     * visible: hiding it as well would leave a column of anonymous dots with no
     * indication of what is even being measured.
     *
     * `@Composable` so every call site collects [hidden] as state itself —
     * otherwise toggling it wouldn't recompose callers that only ever read
     * [_hidden]'s snapshot value in passing.
     */
    @Composable
    fun displayMoney(minor: Long, currency: String = DEFAULT_CURRENCY): String {
        val hidden by _hidden.collectAsStateWithLifecycle()
        if (!hidden) return formatMoney(minor, currency)

        return try {
            val formatted = formatMoney(minor, currency)
            val match = NUMERIC_RUN.find(formatted)
            if (match == null) formatted else formatted.replaceRange(match.range, MASK)
        } catch (_: Exception) {
            "$MASK $currency".trim()
        }
    }
}
