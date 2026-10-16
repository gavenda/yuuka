import type { Account, CategoryBreakdown, Subscription } from '@/types';
import { today } from '@/lib/dates';
import type { STATUS } from '@/lib/palette';

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

/**
 * How the month's spending stands against its plan. `percent` is unclamped, so an overrun reads past 100.
 * `day` is today's day of the month while `days` runs, and null for a month that has ended or not begun: the
 * pace a month is kept at is only a question while it is under way.
 */
export interface BudgetPace {
	spent: number;
	planned: number;
	percent: number;
	day: number | null;
	days: number;
}

/** The expense categories' spending against their plans, as the Budget screen totals them, or null with nothing planned. */
export function budgetPace(categories: CategoryBreakdown[], month: string, now: string = today()): BudgetPace | null {
	const expenses = categories.filter((entry) => entry.kind === 'expense');
	const planned = expenses.reduce((sum, entry) => sum + entry.planned, 0);
	if (planned <= 0) return null;

	const spent = expenses.reduce((sum, entry) => sum + entry.actual, 0);
	const [year, monthIndex] = month.split('-').map(Number);
	const days = new Date(year, monthIndex, 0).getDate();
	const day = now.slice(0, 7) === month ? Number(now.slice(8, 10)) : null;
	return { spent, planned, percent: Math.round((spent / planned) * 100), day, days };
}

/** Past the plan is critical; spending ahead of the share of the month gone is a warning. */
export function paceHealth(pace: BudgetPace): keyof typeof STATUS {
	if (pace.spent > pace.planned) return 'critical';
	if (pace.day !== null && pace.spent / pace.planned > pace.day / pace.days) return 'warning';
	return 'good';
}

/** Income received against what was planned, in whole percent and unclamped, or null with no plan. */
export function incomeProgress(income: number, plannedIncome: number): number | null {
	if (plannedIncome <= 0) return null;
	return Math.round((income / plannedIncome) * 100);
}

/** The accounts still in use, under their types in alphabetical order, the largest balance first within each. */
export function accountGroups(accounts: Account[]): { type: string; accounts: Account[] }[] {
	const groups = new Map<string, Account[]>();
	for (const account of accounts) {
		if (account.archived) continue;
		const type = account.typeName ?? '';
		groups.set(type, [...(groups.get(type) ?? []), account]);
	}
	return [...groups.entries()]
		.sort(([a], [b]) => a.localeCompare(b, undefined, { sensitivity: 'base' }))
		.map(([type, members]) => ({ type, accounts: members.sort((a, b) => b.balance - a.balance) }));
}

/** Whether the accounts in use hold more than one currency — when net worth, which adds them as they stand, is not one currency's figure. */
export function mixedCurrencies(accounts: Account[]): boolean {
	return new Set(accounts.filter((account) => !account.archived).map((account) => account.currency.toUpperCase())).size > 1;
}

/**
 * How far each category in `current` moved from `previous` (another month's breakdown), keyed by category: a
 * positive change is more than before. A category the other month never had moved from nothing.
 */
export function categoryChanges(current: CategoryBreakdown[], previous: CategoryBreakdown[]): Map<string, number> {
	const before = new Map(previous.map((entry) => [entry.categoryId, entry.actual]));
	return new Map(current.map((entry) => [entry.categoryId, entry.actual - (before.get(entry.categoryId) ?? 0)]));
}

/** `2026-09` -> `September`: the month alone, for a comparison whose year goes without saying. */
export function monthName(month: string): string {
	const [year, monthIndex] = month.split('-').map(Number);
	return new Intl.DateTimeFormat(undefined, { month: 'long' }).format(new Date(year, monthIndex - 1, 1));
}

/**
 * The active subscriptions whose next run falls in `month`, the soonest first: what is still to post in it.
 * Only a question for the month under way — every month after it is the whole list again.
 */
export function stillToPost(subscriptions: Subscription[], month: string): Subscription[] {
	return subscriptions
		.filter((subscription) => subscription.enabled && subscription.nextRunOn.slice(0, 7) === month)
		.sort((a, b) => a.nextRunOn.localeCompare(b.nextRunOn));
}
