import { Hono } from 'hono';
import { budgetMonthKey, FIXED_BUDGET_MONTH, getBudgetMode } from '../budget-mode';
import { invalidateAllSummaries, invalidateSummaries } from '../cache';
import { badRequest, notFound } from '../errors';
import { newId } from '../ids';
import { NOW_SQL } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toBudget, type BudgetRow } from '../mappers';
import { budgetUpsertSchema, monthQuerySchema } from '../schemas';
import { currentMonth } from '../dates';
import type { AppEnv } from '../types';

/** Purges the cached summaries a write to this stored `month` could have affected. */
async function invalidateForStoredMonth(cache: KVNamespace, userId: string, storedMonth: string): Promise<void> {
	if (storedMonth === FIXED_BUDGET_MONTH) await invalidateAllSummaries(cache, userId);
	else await invalidateSummaries(cache, userId, [storedMonth]);
}

export const budgetRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { month } = parseQuery(c, monthQuerySchema);
		const userId = c.get('userId');
		const mode = await getBudgetMode(c.env.DB, userId);

		const { results } = await c.env.DB.prepare('SELECT * FROM budgets WHERE user_id = ? AND month = ? ORDER BY category_id')
			.bind(userId, budgetMonthKey(mode, month ?? currentMonth()))
			.all<BudgetRow>();

		return c.json({ budgets: results.map(toBudget) });
	})
	.put('/', async (c) => {
		const input = await parseJson(c, budgetUpsertSchema);
		const userId = c.get('userId');
		const mode = await getBudgetMode(c.env.DB, userId);
		// In fixed mode every budget shares the same stored month, whatever the
		// caller asked for — that single row is what applies to every month.
		const storedMonth = budgetMonthKey(mode, input.month);

		// A budget is either a fixed amount or a share of the month's planned
		// income, never both — whichever the caller sent, the other column is
		// cleared so a stale value cannot linger and be picked up later.
		const percentBp = input.percent === undefined ? null : Math.round(input.percent * 100);
		const amount = input.amount ?? 0;

		// One planned amount per category per stored month, so setting it twice
		// updates rather than piling up rows. The insert only lands if the
		// category is the caller's, which also rules out budgeting against
		// someone else's category.
		const result = await c.env.DB.prepare(
			// Budgets live on top-level categories only: a subcategory's activity
			// rolls up into its parent's plan, so budgeting both would double-count.
			`INSERT INTO budgets (id, user_id, category_id, month, amount, percent_bp)
			 SELECT ?, ?, ?, ?, ?, ?
			 WHERE EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND parent_id IS NULL)
			 ON CONFLICT (category_id, month)
			 DO UPDATE SET amount = excluded.amount, percent_bp = excluded.percent_bp, updated_at = ${NOW_SQL}`,
		)
			.bind(newId('bdg'), userId, input.categoryId, storedMonth, amount, percentBp, input.categoryId, userId)
			.run();

		if (!result.meta.changes) throw badRequest('Unknown category, or it is a subcategory — budgets are set on the parent.');

		await invalidateForStoredMonth(c.env.CACHE, userId, storedMonth);

		const row = await c.env.DB.prepare('SELECT * FROM budgets WHERE user_id = ? AND category_id = ? AND month = ?')
			.bind(userId, input.categoryId, storedMonth)
			.first<BudgetRow>();

		return c.json({ budget: toBudget(row!) });
	})
	.delete('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');

		const existing = await c.env.DB.prepare('SELECT month FROM budgets WHERE id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ month: string }>();
		if (!existing) throw notFound('Budget not found.');

		await c.env.DB.prepare('DELETE FROM budgets WHERE id = ? AND user_id = ?').bind(id, userId).run();
		await invalidateForStoredMonth(c.env.CACHE, userId, existing.month);
		return c.body(null, 204);
	});
