/**
 * What the dashboard works out from a month's summary, kept apart from the view so it can be tested.
 * Mirrors `domain/Dashboard.kt`.
 */

/** The share of the month's income it ended with, in whole percent, or null when nothing came in to be a share of. */
export function savingsRate(net: number, income: number): number | null {
	if (income <= 0) return null;
	return Math.round((net / income) * 100);
}
