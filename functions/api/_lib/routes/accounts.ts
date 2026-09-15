import { Hono } from 'hono';
import { invalidateAllSummaries } from '../cache';
import { badRequest, conflict, notFound } from '../errors';
import { newId } from '../ids';
import { buildUpdate, NOW_SQL, toSqliteBool } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toAccount, type AccountRow } from '../mappers';
import { accountCreateSchema, accountUpdateSchema, listQuerySchema } from '../schemas';
import type { AppEnv } from '../types';

/**
 * Accounts always carry their derived balance: starting balance plus every
 * posting. The balance join is restricted to the owner's transactions as well,
 * so a stray row could never leak into someone else's total.
 */
const SELECT_WITH_BALANCE = `
	SELECT a.id, a.name, a.type_id, a.currency, a.logo_url, a.starting_balance, a.archived, a.created_at, a.updated_at,
	       ty.name AS type_name,
	       a.starting_balance + COALESCE(SUM(t.amount), 0) AS balance
	FROM accounts a
	JOIN account_types ty ON ty.id = a.type_id
	LEFT JOIN transactions t ON t.account_id = a.id AND t.user_id = a.user_id
`;

export const accountRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { includeArchived } = parseQuery(c, listQuerySchema);
		const { results } = await c.env.DB.prepare(
			`${SELECT_WITH_BALANCE}
			 WHERE a.user_id = ? AND (? = 1 OR a.archived = 0)
			 GROUP BY a.id
			 ORDER BY a.archived ASC, a.name COLLATE NOCASE ASC`,
		)
			.bind(c.get('userId'), includeArchived ? 1 : 0)
			.all<AccountRow>();

		return c.json({ accounts: results.map(toAccount) });
	})
	.get('/:id', async (c) => {
		const row = await c.env.DB.prepare(`${SELECT_WITH_BALANCE} WHERE a.id = ? AND a.user_id = ? GROUP BY a.id`)
			.bind(c.req.param('id'), c.get('userId'))
			.first<AccountRow>();

		// Someone else's account is reported as missing rather than forbidden, so
		// the API never confirms that an id exists for a different owner.
		if (!row) throw notFound('Account not found.');
		return c.json({ account: toAccount(row) });
	})
	.post('/', async (c) => {
		const input = await parseJson(c, accountCreateSchema);
		const userId = c.get('userId');
		const id = newId('acc');

		// Guarded the same way transactions are: the type has to be the caller's,
		// checked in the statement that performs the write.
		const result = await c.env.DB.prepare(
			`INSERT INTO accounts (id, user_id, name, type_id, currency, starting_balance, logo_url)
			 SELECT ?, ?, ?, ?, ?, ?, ?
			 WHERE EXISTS (SELECT 1 FROM account_types WHERE id = ? AND user_id = ?)`,
		)
			.bind(id, userId, input.name, input.typeId, input.currency, input.startingBalance, input.logoUrl ?? null, input.typeId, userId)
			.run();

		if (!result.meta.changes) throw badRequest('Unknown account type.');

		await invalidateAllSummaries(c.env.CACHE, userId);

		const row = await c.env.DB.prepare(`${SELECT_WITH_BALANCE} WHERE a.id = ? AND a.user_id = ? GROUP BY a.id`)
			.bind(id, userId)
			.first<AccountRow>();
		return c.json({ account: toAccount(row!) }, 201);
	})
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, accountUpdateSchema);

		const { clause, values } = buildUpdate({
			name: input.name,
			type_id: input.typeId,
			currency: input.currency,
			logo_url: input.logoUrl,
			starting_balance: input.startingBalance,
			archived: toSqliteBool(input.archived),
		});

		const typeGuard = input.typeId ?? null;
		const result = await c.env.DB.prepare(
			`UPDATE accounts SET ${clause}, updated_at = ${NOW_SQL}
			 WHERE id = ? AND user_id = ?
			   AND (? IS NULL OR EXISTS (SELECT 1 FROM account_types WHERE id = ? AND user_id = ?))`,
		)
			.bind(...values, id, userId, typeGuard, typeGuard, userId)
			.run();

		if (!result.meta.changes) {
			// The row exists and is the caller's, so a no-op means the type was not.
			const owned = await c.env.DB.prepare('SELECT 1 AS present FROM accounts WHERE id = ? AND user_id = ?')
				.bind(id, userId)
				.first<{ present: number }>();
			throw owned ? badRequest('Unknown account type.') : notFound('Account not found.');
		}
		await invalidateAllSummaries(c.env.CACHE, userId);

		const row = await c.env.DB.prepare(`${SELECT_WITH_BALANCE} WHERE a.id = ? AND a.user_id = ? GROUP BY a.id`)
			.bind(id, userId)
			.first<AccountRow>();
		return c.json({ account: toAccount(row!) });
	})
	.delete('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const { includeTransactions } = c.req.query();

		// Deleting cascades to this account's transactions, which is rarely what
		// you want by accident — make the caller opt in once history exists.
		const existing = await c.env.DB.prepare('SELECT COUNT(*) AS count FROM transactions WHERE account_id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ count: number }>();

		if (existing && existing.count > 0 && includeTransactions !== 'true') {
			throw conflict(
				`Account still has ${existing.count} transaction(s). Archive it instead, or repeat with ?includeTransactions=true to delete them too.`,
			);
		}

		const result = await c.env.DB.prepare('DELETE FROM accounts WHERE id = ? AND user_id = ?').bind(id, userId).run();
		if (!result.meta.changes) throw notFound('Account not found.');

		await invalidateAllSummaries(c.env.CACHE, userId);
		return c.body(null, 204);
	});
