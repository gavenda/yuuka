import type { CategoryBreakdown } from '@/types';

/**
 * What the dashboard works out from a month's summary, kept apart from the view so it can be tested.
 * Mirrors `domain/Dashboard.kt`.
 */

/** The share of the month's income it ended with, in whole percent, or null when nothing came in to be a share of. */
export function savingsRate(net: number, income: number): number | null {
	if (income <= 0) return null;
	return Math.round((net / income) * 100);
}

/** The expense categories spent past their plan, the furthest over first. An unplanned category has no plan to be over. */
export function overBudget(categories: CategoryBreakdown[]): CategoryBreakdown[] {
	return categories
		.filter((entry) => entry.kind === 'expense' && entry.planned > 0 && entry.remaining < 0)
		.sort((a, b) => a.remaining - b.remaining);
}
