package dev.gavenda.yuuka.domain

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS_NAME = "yuuka.layout"
private const val KEY_CATEGORIES = "categories_collapsed"

/**
 * Which Categories sections the user has folded away, remembered across
 * visits to the screen, rotation and restarts.
 *
 * A local display preference for the same reason [AmountVisibility] is: how
 * this device lays a screen out is not part of the ledger, so it never touches
 * the API and does not follow the account onto another device. Keyed by the
 * section's own key, so a section that later disappears just leaves an
 * inert entry behind.
 */
class CollapsedSections(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Copied: the set getStringSet returns must not be modified.
    private val _categories = MutableStateFlow(prefs.getStringSet(KEY_CATEGORIES, emptySet()).orEmpty().toSet())
    val categories: StateFlow<Set<String>> = _categories.asStateFlow()

    fun toggleCategorySection(key: String) {
        val current = _categories.value
        val next = if (key in current) current - key else current + key
        _categories.value = next
        prefs.edit().putStringSet(KEY_CATEGORIES, next).apply()
    }
}
