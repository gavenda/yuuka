import { Hono } from 'hono';
import { badRequest } from '../errors';
import { toRoundUpRule, type RoundUpRuleRow } from '../mappers';
import { roundUpRuleUpdateSchema } from '../schemas';
import { NOW_SQL, toSqliteBool } from '../sql';
import { parseJson } from '../validate';
import { requireAuth } from '../middleware/auth';
import type { AppEnv } from '../types';

const COLUMNS = 'enabled, round_to, destination_account_id, category_id, created_at, updated_at';

/**
 * The per-user "Save the Change" rule. Unlike `settings`, this row is not
 * provisioned on sign-in — a user who never opens the feature never gets one,
 * so GET synthesises the off default rather than 404ing.
 */
export const roundUpRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const row = await c.env.DB.prepare(`SELECT ${COLUMNS} FROM round_up_rules WHERE user_id = ?`)
			.bind(c.get('userId'))
			.first<RoundUpRuleRow>();

		return c.json({
			roundUpRule: row
				? toRoundUpRule(row)
				: { enabled: false, roundTo: 1000, destinationAccountId: null, categoryId: null, createdAt: null, updatedAt: null },
		});
	})
	.patch('/', async (c) => {
		const input = await parseJson(c, roundUpRuleUpdateSchema);
		const userId = c.get('userId');

		// Same guard as `settings.defaultAccountId`: the destination has to be
		// the caller's own account, checked in the same statement that writes
		// it. The `WHERE` also gates the initial `INSERT`'s `SELECT` — an
		// invalid destination matches nothing, so neither the insert nor the
		// conflict-triggered update below ever runs, and `meta.changes` stays 0.
		const destinationGuard = input.destinationAccountId ?? null;
		// The category, when set, has to be one of the caller's own
		// transfer-scope categories — a round-up posts as an ordinary transfer,
		// so it takes the same Cashflow tree a plain transfer does.
		const categoryGuard = input.categoryId ?? null;
		const result = await c.env.DB.prepare(
			`INSERT INTO round_up_rules (user_id, enabled, round_to, destination_account_id, category_id)
			 SELECT ?, ?, ?, ?, ?
			 WHERE (? IS NULL OR EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?))
			   AND (? IS NULL OR EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND applies_to = 'transfer'))
			 ON CONFLICT (user_id) DO UPDATE SET
			   enabled = COALESCE(?, round_up_rules.enabled),
			   round_to = COALESCE(?, round_up_rules.round_to),
			   destination_account_id = CASE WHEN ? THEN ? ELSE round_up_rules.destination_account_id END,
			   category_id = CASE WHEN ? THEN ? ELSE round_up_rules.category_id END,
			   updated_at = ${NOW_SQL}`,
		)
			.bind(
				userId,
				toSqliteBool(input.enabled) ?? 0,
				input.roundTo ?? 1000,
				input.destinationAccountId ?? null,
				input.categoryId ?? null,
				destinationGuard,
				destinationGuard,
				userId,
				categoryGuard,
				categoryGuard,
				userId,
				toSqliteBool(input.enabled) ?? null,
				input.roundTo ?? null,
				input.destinationAccountId !== undefined ? 1 : 0,
				input.destinationAccountId ?? null,
				input.categoryId !== undefined ? 1 : 0,
				input.categoryId ?? null,
			)
			.run();

		if (!result.meta.changes) throw badRequest('Unknown account or category.');

		const row = await c.env.DB.prepare(`SELECT ${COLUMNS} FROM round_up_rules WHERE user_id = ?`).bind(userId).first<RoundUpRuleRow>();
		return c.json({ roundUpRule: toRoundUpRule(row!) });
	});
