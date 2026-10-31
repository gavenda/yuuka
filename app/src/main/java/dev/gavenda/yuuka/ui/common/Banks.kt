package dev.gavenda.yuuka.ui.common

import androidx.annotation.DrawableRes
import dev.gavenda.yuuka.R

/**
 * A bank an account can be filed under without a logo URL of its own. Only [key] is stored on the account;
 * the name, the logo and how it shows in dark mode are here, so changing one reaches every account that
 * names it. Logos are vector drawables (`res/drawable/bank_*.xml`) converted from the web's SVGs.
 */
data class Bank(
    /** What the account stores: lowercase letters, digits and hyphens, at most 40. */
    val key: String,
    val name: String,
    /** The logo; the light-mode one when there is a [logoDark]. */
    @param:DrawableRes val logo: Int,
    /** A logo drawn for dark mode, where the bank has one. */
    @param:DrawableRes val logoDark: Int? = null,
    /** Whether [logo] inverts in dark mode, for a dark mark with no dark-mode logo of its own. */
    val invertDark: Boolean = false,
) {
    @DrawableRes
    fun logoFor(dark: Boolean): Int = if (dark) logoDark ?: logo else logo
}

/**
 * The preset banks, in the order the account form lists them; Custom always follows. The server checks
 * only a key's shape, so adding one is a release, not a migration — but the same key must go into
 * `webapp/src/lib/banks.ts`, or the web shows the account's initial for it. Mirrors `lib/banks.ts`.
 */
object Banks {
    val presets: List<Bank> = listOf(
        Bank("bpi", "BPI", R.drawable.bank_bpi),
        Bank("dragonfi", "DragonFi", R.drawable.bank_dragonfi),
        Bank("gcash", "GCash", R.drawable.bank_gcash),
        Bank("gotyme", "GoTyme", R.drawable.bank_gotyme_light, logoDark = R.drawable.bank_gotyme_dark),
        Bank("maya", "Maya", R.drawable.bank_maya_light, logoDark = R.drawable.bank_maya_dark),
        Bank("metrobank", "Metrobank", R.drawable.bank_metrobank),
        Bank("tonik", "Tonik", R.drawable.bank_tonik),
    )

    fun byKey(key: String?): Bank? = key?.let { wanted -> presets.firstOrNull { it.key == wanted } }
}
