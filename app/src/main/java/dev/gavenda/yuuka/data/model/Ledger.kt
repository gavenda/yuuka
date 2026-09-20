package dev.gavenda.yuuka.data.model

import kotlinx.serialization.Serializable

/** Shapes returned by the yuuka API. Amounts are integer minor units (cents). Mirrors `src/types.ts`. */

@Serializable
enum class BudgetMode {
    fixed,
    monthly,
}

/** Per-user preferences. */
@Serializable
data class Settings(
    /** The currency every aggregate figure is displayed in. */
    val displayCurrency: String,
    /** Whether a category's planned amount applies to every month, or is set separately per month. Fixed is the default. */
    val budgetMode: BudgetMode,
    /** Which account a new transaction opens on. Null falls back to the first active account. */
    val defaultAccountId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/**
 * The "Save the Change" round-up rule. Not provisioned on sign-in — a user who
 * never opens the feature never gets a row, so a missing rule reads the same
 * as this default (disabled, no destination).
 */
@Serializable
data class RoundUpRule(
    val enabled: Boolean = false,
    /** Minor-unit multiple to round up to: 1000 (₱10) or 10000 (₱100). */
    val roundTo: Long = 1000,
    val destinationAccountId: String? = null,
    /** Must be a transfer-scope category — a round-up posts as an ordinary transfer. Null stays uncategorized. */
    val categoryId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** A user-defined account type. Everyone starts with a default set. */
@Serializable
data class AccountType(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val archived: Boolean,
    /** How many accounts currently use it — a type in use cannot be deleted. */
    val accountCount: Int,
    val createdAt: String = "",
    val updatedAt: String = "",
)

@Serializable
data class Account(
    val id: String,
    val name: String,
    val typeId: String,
    val typeName: String? = null,
    val currency: String,
    /** Optional http(s) image URL shown beside the account. */
    val logoUrl: String? = null,
    /** Applies an invert filter to the logo in dark mode, for a dark mark that would otherwise disappear. */
    val logoInvertDark: Boolean = false,
    /** Whether this account's own purchases round up under "Save the Change". */
    val roundUpSource: Boolean = false,
    val startingBalance: Long,
    val balance: Long,
    val archived: Boolean,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/** A user-defined label. Unlike a category it changes no figure — it is only there to read a transaction by. */
@Serializable
data class Tag(
    val id: String,
    val name: String,
    val color: String,
    /** How many transactions wear it. A transfer counts once, not once per leg. */
    val transactionCount: Int = 0,
    val createdAt: String = "",
    val updatedAt: String = "",
)

/**
 * What a category is for. [income] and [expense] categorise spending and
 * income; [transfer] categorises movements between the user's own accounts.
 */
@Serializable
enum class CategoryKind {
    income,
    expense,
    transfer,
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val color: String,
    val sortOrder: Int = 0,
    val archived: Boolean,
    /** Null for a top-level category. Nesting is one level deep. */
    val parentId: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
)
