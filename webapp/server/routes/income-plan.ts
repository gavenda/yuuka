import { Hono } from 'hono';
import { budgetMonthKey, FIXED_BUDGET_MONTH, getBudgetMode, MONTH_MATCHES } from '../budget-mode';
import { invalidateAllSummaries, invalidateSummaries } from '../cache';
import { newId } from '../ids';
import { NOW_SQL } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toIncomePlan, type IncomePlanRow } from '../mappers';
import { incomePlanUpsertSchema, monthQuerySchema } from '../schemas';
import { currentMonth } from '../dates';
import type { AppEnv } from '../types';

/**
 * The total income a user plans for a month, so percentage-based budgets have
 * a figure to be a share of. One row per user and stored month, upserted like
 * a budget — and keyed by the same `budgetMonthKey()`, so a fixed
 * plan answers every month with the same figure instead of resetting when the
 * caller switches months; an unset month simply reports zero.
 */
export const incomePlanRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { month = currentMonth() } = parseQuery(c, monthQuerySchema);
		const userId = c.get('userId');
		const mode = await getBudgetMode(c.env.DB, userId);
		const storedMonth = budgetMonthKey(mode, month);

		const row = await c.env.DB.prepare(`SELECT * FROM income_plans WHERE user_id = ? AND ${MONTH_MATCHES}`)
			.bind(userId, storedMonth)
			.first<IncomePlanRow>();

		return c.json({
			incomePlan: row
				? toIncomePlan(row)
				: { month: mode === 'fixed' ? null : month, amount: 0, mode: 'fixed', grossAmount: null, createdAt: null, updatedAt: null },
		});
	})
	.put('/', async (c) => {
		const input = await parseJson(c, incomePlanUpsertSchema);
		const userId = c.get('userId');
		const budgetMode = await getBudgetMode(c.env.DB, userId);
		// In fixed mode every planned income shares the same stored month,
		// whatever the caller asked for — that single row applies to every month.
		const storedMonth = budgetMonthKey(budgetMode, input.month);
		// Fixed mode has no gross figure to remember, so it's cleared rather than left stale.
		const grossAmount = input.mode === 'gross' ? (input.grossAmount ?? null) : null;

		await c.env.DB.prepare(
			`INSERT INTO income_plans (id, user_id, month, amount, mode, gross_amount)
			 VALUES (?, ?, ?, ?, ?, ?)
			 ON CONFLICT ${storedMonth === null ? '(user_id) WHERE month IS NULL' : '(user_id, month) WHERE month IS NOT NULL'}
			 DO UPDATE SET amount = excluded.amount, mode = excluded.mode, gross_amount = excluded.gross_amount, updated_at = ${NOW_SQL}`,
		)
			.bind(newId('inp'), userId, storedMonth, input.amount, input.mode, grossAmount)
			.run();

		if (storedMonth === FIXED_BUDGET_MONTH) await invalidateAllSummaries(c.env.CACHE, userId);
		else await invalidateSummaries(c.env.CACHE, userId, [storedMonth]);

		const row = await c.env.DB.prepare(`SELECT * FROM income_plans WHERE user_id = ? AND ${MONTH_MATCHES}`)
			.bind(userId, storedMonth)
			.first<IncomePlanRow>();

		return c.json({ incomePlan: toIncomePlan(row!) });
	});
