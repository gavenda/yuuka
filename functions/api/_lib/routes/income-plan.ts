import { Hono } from 'hono';
import { invalidateSummaries } from '../cache';
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
 * a figure to be a share of. One row per user and month, upserted like a
 * budget; an unset month simply reports zero.
 */
export const incomePlanRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { month = currentMonth() } = parseQuery(c, monthQuerySchema);
		const row = await c.env.DB.prepare('SELECT * FROM income_plans WHERE user_id = ? AND month = ?')
			.bind(c.get('userId'), month)
			.first<IncomePlanRow>();

		return c.json({
			incomePlan: row ? toIncomePlan(row) : { month, amount: 0, mode: 'fixed', grossAmount: null, createdAt: null, updatedAt: null },
		});
	})
	.put('/', async (c) => {
		const input = await parseJson(c, incomePlanUpsertSchema);
		const userId = c.get('userId');
		// Fixed mode has no gross figure to remember, so it's cleared rather than left stale.
		const grossAmount = input.mode === 'gross' ? (input.grossAmount ?? null) : null;

		await c.env.DB.prepare(
			`INSERT INTO income_plans (id, user_id, month, amount, mode, gross_amount)
			 VALUES (?, ?, ?, ?, ?, ?)
			 ON CONFLICT (user_id, month)
			 DO UPDATE SET amount = excluded.amount, mode = excluded.mode, gross_amount = excluded.gross_amount, updated_at = ${NOW_SQL}`,
		)
			.bind(newId('inp'), userId, input.month, input.amount, input.mode, grossAmount)
			.run();

		await invalidateSummaries(c.env.CACHE, userId, [input.month]);

		const row = await c.env.DB.prepare('SELECT * FROM income_plans WHERE user_id = ? AND month = ?')
			.bind(userId, input.month)
			.first<IncomePlanRow>();

		return c.json({ incomePlan: toIncomePlan(row!) });
	});
