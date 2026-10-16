import type { CategoryBreakdown } from '@/types';
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
