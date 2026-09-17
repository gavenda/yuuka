import type { Payee } from '@/types';

/**
 * Picks the suggestions to offer for what has been typed so far.
 *
 * Prefix matches come first — typing "cor" should offer "Corner Market" before
 * "Tesco Corner" — and ties keep the order the server gave, which is
 * most-used-first. With nothing typed, the most-used entries are the suggestion.
 */
export function rankPayees(all: Payee[], typed: string, limit = 8): Payee[] {
	const query = typed.trim().toLowerCase();
	if (!query) return all.slice(0, limit);

	const startsWith = (entry: Payee) => entry.payee.toLowerCase().startsWith(query);

	return all
		.filter((entry) => entry.payee.toLowerCase().includes(query))
		.map((entry, index) => ({ entry, index }))
		.sort((a, b) => Number(startsWith(b.entry)) - Number(startsWith(a.entry)) || a.index - b.index)
		.slice(0, limit)
		.map(({ entry }) => entry);
}

/** True when the only match is what has already been typed, so the list is noise. */
export function isExhausted(matches: Payee[], typed: string): boolean {
	const query = typed.trim().toLowerCase();
	return matches.length === 1 && matches[0].payee.toLowerCase() === query;
}
