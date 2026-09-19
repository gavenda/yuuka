package dev.gavenda.yuuka.domain

import android.content.Context

private const val PREFS_NAME = "yuuka.rail"
private const val KEY_EXPANDED = "expanded"

/**
 * Whether the navigation rail was left open into its drawer form — a remembered layout choice, not
 * part of the ledger, so it stays on this device like [AmountVisibility]. Mirrors `src/lib/rail.ts`.
 *
 * Only the rail's own state reads it, and only where an open rail sits beside the page: a narrow
 * window never starts with it floating over the content.
 */
class RailPreference(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var expanded: Boolean
        get() = prefs.getBoolean(KEY_EXPANDED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_EXPANDED, value).apply()
        }
}
