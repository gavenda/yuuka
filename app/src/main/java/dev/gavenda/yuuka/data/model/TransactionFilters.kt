package dev.gavenda.yuuka.data.model

/** `categoryId = "none"` is the API's own sentinel for "uncategorised" — see the `/transactions` route. */
data class TransactionFilters(
    val month: String? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val search: String? = null,
)
