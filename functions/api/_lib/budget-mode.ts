export const BUDGET_MODES = ['fixed', 'monthly'] as const;
export type BudgetMode = (typeof BUDGET_MODES)[number];

/**
 * The `month` a fixed budget is stored under. Fixed budgets share the same
 * `budgets` table and `(category_id, month)` unique index as monthly ones —
 * this sentinel is what lets "one row per category, applying to every month"
 * be enforced by that same index rather than a second table. It is never a
 * valid `YYYY-MM`, so it cannot collide with a real month.
 */
export const FIXED_BUDGET_MONTH = 'fixed';

/** A user's budgeting preference, set once and defaulting to 'fixed'. */
export async function getBudgetMode(db: D1Database, userId: string): Promise<BudgetMode> {
	const row = await db.prepare('SELECT budget_mode FROM users WHERE id = ?').bind(userId).first<{ budget_mode: BudgetMode }>();
	return row?.budget_mode ?? 'fixed';
}

/** The `month` a budget is actually keyed by, given the user's mode. */
export function budgetMonthKey(mode: BudgetMode, month: string): string {
	return mode === 'fixed' ? FIXED_BUDGET_MONTH : month;
}
