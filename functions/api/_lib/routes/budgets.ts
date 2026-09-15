import { Hono } from 'hono';
import { invalidateSummaries } from '../cache';
import { badRequest, notFound } from '../errors';
import { newId } from '../ids';
import { NOW_SQL } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toBudget, type BudgetRow } from '../mappers';
import { budgetUpsertSchema, monthQuerySchema } from '../schemas';
import { currentMonth } from '../dates';
import type { AppEnv } from '../types';

export const budgetRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { month } = parseQuery(c, monthQuerySchema);
		const { results } = await c.env.DB.prepare('SELECT * FROM budgets WHERE user_id = ? AND month = ? ORDER BY category_id')
			.bind(c.get('userId'), month ?? currentMonth())
			.all<BudgetRow>();

		return c.json({ budgets: results.map(toBudget) });
	})
	.put('/', async (c) => {
		const input = await parseJson(c, budgetUpsertSchema);
		const userId = c.get('userId');

		// One planned amount per category per month, so setting it twice updates
		// rather than piling up rows. The insert only lands if the category is the
		// caller's, which also rules out budgeting against someone else's category.
		const result = await c.env.DB.prepare(
			// Budgets live on top-level categories only: a subcategory's activity
			// rolls up into its parent's plan, so budgeting both would double-count.
			`INSERT INTO budgets (id, user_id, category_id, month, amount)
			 SELECT ?, ?, ?, ?, ?
			 WHERE EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND parent_id IS NULL)
			 ON CONFLICT (category_id, month)
			 DO UPDATE SET amount = excluded.amount, updated_at = ${NOW_SQL}`,
		)
			.bind(newId('bdg'), userId, input.categoryId, input.month, input.amount, input.categoryId, userId)
			.run();

		if (!result.meta.changes) throw badRequest('Unknown category, or it is a subcategory — budgets are set on the parent.');

		await invalidateSummaries(c.env.CACHE, userId, [input.month]);

		const row = await c.env.DB.prepare('SELECT * FROM budgets WHERE user_id = ? AND category_id = ? AND month = ?')
			.bind(userId, input.categoryId, input.month)
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
		await invalidateSummaries(c.env.CACHE, userId, [existing.month]);
		return c.body(null, 204);
	});
