package dev.gavenda.yuuka.data.model

/** Stands for "uncategorised" in [TransactionFilters.categoryIds], beside real ids. */
const val UNCATEGORIZED_FILTER_ID = "none"

/**
 * The month and the search are put to the API; the account, category and tag filters only narrow
 * the rows already cached. A filter left empty is not applied; one holding several matches any of
 * them, and every filter given must match.
 */
data class TransactionFilters(
    val month: String? = null,
    val accountIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val search: String? = null,
) {
    /** Whether the cache is narrowed here, which is only right if it holds every row the API has for the month and search. */
    val narrowsCache: Boolean get() = accountIds.isNotEmpty() || categoryIds.isNotEmpty() || tagIds.isNotEmpty()

    /** How many cached rows to show: [loaded] normally, everything when narrowed, so the filter sees rows past the ones loaded so far. */
    fun rowLimit(loaded: Int): Int = if (narrowsCache) Int.MAX_VALUE else loaded
}
