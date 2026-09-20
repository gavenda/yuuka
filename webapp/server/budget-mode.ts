export const BUDGET_MODES = ['fixed', 'monthly'] as const;
export type BudgetMode = (typeof BUDGET_MODES)[number];

/**
 * The `month` a fixed budget is stored under: none. A fixed plan answers every
 * month, so it belongs to no single one, and a NULL month says that outright.
 * Partial unique indexes (`budgets_default_idx`, `budgets_month_idx`) are what
 * keep a category from carrying two plans of the same kind at once.
 */
export const FIXED_BUDGET_MONTH = null;

/** A user's budgeting preference, set once and defaulting to 'fixed'. */
export async function getBudgetMode(db: D1Database, userId: string): Promise<BudgetMode> {
	const row = await db.prepare('SELECT budget_mode FROM users WHERE id = ?').bind(userId).first<{ budget_mode: BudgetMode }>();
	return row?.budget_mode ?? 'fixed';
}

/** The `month` a budget is actually keyed by, given the user's mode. */
export function budgetMonthKey(mode: BudgetMode, month: string): string | null {
	return mode === 'fixed' ? FIXED_BUDGET_MONTH : month;
}

/**
 * Matching a month means `month IS ?`, never `month = ?`: a fixed plan's month
 * is NULL, and `= NULL` matches nothing. SQLite's `IS` is null-safe equality,
 * so one predicate serves both modes and the routes stay mode-agnostic.
 */
export const MONTH_MATCHES = 'month IS ?';
