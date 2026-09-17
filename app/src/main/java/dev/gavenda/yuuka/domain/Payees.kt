package dev.gavenda.yuuka.domain

import dev.gavenda.yuuka.data.model.Payee

/**
 * Picks the suggestions to offer for what has been typed so far.
 *
 * Prefix matches come first — typing "cor" should offer "Corner Market" before
 * "Tesco Corner" — and ties keep the order the server gave, which is
 * most-used-first. With nothing typed, the most-used entries are the suggestion.
 * Mirrors `src/lib/payees.ts`.
 */
fun rankPayees(all: List<Payee>, typed: String, limit: Int = 8): List<Payee> {
    val query = typed.trim().lowercase()
    if (query.isEmpty()) return all.take(limit)

    fun startsWith(entry: Payee) = entry.payee.lowercase().startsWith(query)

    return all
        .filter { it.payee.lowercase().contains(query) }
        .withIndex()
        .sortedWith(compareByDescending<IndexedValue<Payee>> { startsWith(it.value) }.thenBy { it.index })
        .take(limit)
        .map { it.value }
}

/** True when the only match is what has already been typed, so the list is noise. */
fun isExhausted(matches: List<Payee>, typed: String): Boolean {
    val query = typed.trim().lowercase()
    return matches.size == 1 && matches[0].payee.lowercase() == query
}
