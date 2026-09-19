import { Hono } from 'hono';
import { dayOf, firstOccurrenceOnOrAfter, today } from '../dates';
import { badRequest, notFound } from '../errors';
import { newId } from '../ids';
import { toSubscription, type SubscriptionRow } from '../mappers';
import { subscriptionCreateSchema, subscriptionUpdateSchema } from '../schemas';
import { buildUpdate, NOW_SQL, toSqliteBool } from '../sql';
import { parseJson } from '../validate';
import { requireAuth } from '../middleware/auth';
import type { AppEnv } from '../types';

const SELECT_ENRICHED = `
	SELECT s.id, s.account_id, s.category_id, s.amount, s.payee, s.notes, s.start_on, s.day_of_month,
	       s.next_run_on, s.last_run_on, s.enabled, s.created_at, s.updated_at,
	       a.name AS account_name, c.name AS category_name, c.color AS category_color
	FROM subscriptions s
	JOIN accounts a ON a.id = s.account_id
	LEFT JOIN categories c ON c.id = s.category_id
`;

const MISSING_REFERENCE = 'Unknown account or category.';
const WRONG_SCOPE = 'That category cannot be used here. Subscriptions post ordinary transactions, so they use standard categories.';
const PAST_START = 'The start date cannot be in the past.';

async function loadOne(db: D1Database, userId: string, id: string): Promise<SubscriptionRow | null> {
	return await db.prepare(`${SELECT_ENRICHED} WHERE s.id = ? AND s.user_id = ?`).bind(id, userId).first<SubscriptionRow>();
}

/**
 * A subscription posts an ordinary, single-account transaction on a day of the
 * month; the Worker's cron does the posting (`server/subscriptions.ts`). These
 * routes only manage the schedule — nothing here writes a transaction.
 *
 * Deleting one keeps everything it already posted: those are history now.
 */
export const subscriptionRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { results } = await c.env.DB.prepare(
			`${SELECT_ENRICHED}
			 WHERE s.user_id = ?
			 ORDER BY s.enabled DESC, s.next_run_on ASC, s.payee COLLATE NOCASE ASC`,
		)
			.bind(c.get('userId'))
			.all<SubscriptionRow>();

		return c.json({ subscriptions: results.map(toSubscription) });
	})
	.post('/', async (c) => {
		const input = await parseJson(c, subscriptionCreateSchema);
		const userId = c.get('userId');
		const id = newId('sub');

		// Running at 00:00 UTC, a date already gone can never post. Today is fine:
		// the next tick posts it, dated as chosen.
		if (input.startOn < today()) throw badRequest(PAST_START);

		// Ownership of the account and category is checked in the statement that
		// writes, the same as a transaction, so it cannot be overtaken in between.
		const result = await c.env.DB.prepare(
			`INSERT INTO subscriptions (id, user_id, account_id, category_id, amount, payee, notes, start_on, day_of_month, next_run_on)
			 SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
			 WHERE EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?)
			   AND (? IS NULL OR EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND applies_to = 'standard'))`,
		)
			.bind(
				id,
				userId,
				input.accountId,
				input.categoryId,
				input.amount,
				input.payee,
				input.notes,
				input.startOn,
				dayOf(input.startOn),
				input.startOn,
				input.accountId,
				userId,
				input.categoryId,
				input.categoryId,
				userId,
			)
			.run();

		if (!result.meta.changes) throw badRequest(input.categoryId ? WRONG_SCOPE : MISSING_REFERENCE);

		return c.json({ subscription: toSubscription((await loadOne(c.env.DB, userId, id))!) }, 201);
	})
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, subscriptionUpdateSchema);

		const existing = await c.env.DB.prepare('SELECT enabled, day_of_month FROM subscriptions WHERE id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ enabled: number; day_of_month: number }>();
		if (!existing) throw notFound('Subscription not found.');

		// The schedule only moves when asked to. A new start date restarts it; being
		// resumed after a pause skips whatever fell due meanwhile rather than
		// posting a backlog the user never saw coming.
		let schedule: { start_on?: string; day_of_month?: number; next_run_on?: string } = {};
		if (input.startOn !== undefined) {
			if (input.startOn < today()) throw badRequest(PAST_START);
			schedule = { start_on: input.startOn, day_of_month: dayOf(input.startOn), next_run_on: input.startOn };
		} else if (input.enabled === true && existing.enabled === 0) {
			schedule = { next_run_on: firstOccurrenceOnOrAfter(existing.day_of_month, today()) };
		}

		const { clause, values } = buildUpdate({
			account_id: input.accountId,
			category_id: input.categoryId,
			amount: input.amount,
			payee: input.payee,
			notes: input.notes,
			enabled: toSqliteBool(input.enabled),
			...schedule,
		});

		// Re-pointing is only allowed at rows the caller owns; each guard is a
		// no-op when its field is not being changed.
		const accountGuard = input.accountId ?? null;
		const categoryGuard = input.categoryId ?? null;

		const result = await c.env.DB.prepare(
			`UPDATE subscriptions SET ${clause}, updated_at = ${NOW_SQL}
			 WHERE id = ? AND user_id = ?
			   AND (? IS NULL OR EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?))
			   AND (? IS NULL OR EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND applies_to = 'standard'))`,
		)
			.bind(...values, id, userId, accountGuard, accountGuard, userId, categoryGuard, categoryGuard, userId)
			.run();

		if (!result.meta.changes) throw badRequest(input.categoryId ? WRONG_SCOPE : MISSING_REFERENCE);

		return c.json({ subscription: toSubscription((await loadOne(c.env.DB, userId, id))!) });
	})
	.delete('/:id', async (c) => {
		const result = await c.env.DB.prepare('DELETE FROM subscriptions WHERE id = ? AND user_id = ?')
			.bind(c.req.param('id'), c.get('userId'))
			.run();

		if (!result.meta.changes) throw notFound('Subscription not found.');
		return c.body(null, 204);
	});
