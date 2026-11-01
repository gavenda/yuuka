package dev.gavenda.yuuka.data.model

/** Stands for "uncategorised" in [TransactionFilters.categoryIds], beside real ids. */
const val UNCATEGORIZED_FILTER_ID = "none"

/**
 * What the transaction list is narrowed to. Every filter is put to the API, which pages what matches, and the
 * same filters read the cached rows back. A filter left empty is not applied; one holding several matches any
 * of them, and every filter given must match.
 */
data class TransactionFilters(
    val month: String? = null,
    val accountIds: Set<String> = emptySet(),
    val categoryIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val search: String? = null,
)
