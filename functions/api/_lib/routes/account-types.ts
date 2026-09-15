import { Hono } from 'hono';
import { invalidateAllSummaries } from '../cache';
import { conflict, notFound } from '../errors';
import { newId } from '../ids';
import { buildUpdate, isUniqueViolation, NOW_SQL, toSqliteBool } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toAccountType, type AccountTypeRow } from '../mappers';
import { accountTypeCreateSchema, accountTypeUpdateSchema, listQuerySchema } from '../schemas';
import type { AppEnv } from '../types';

const DUPLICATE_MESSAGE = 'You already have an account type with that name.';

/** Each type carries how many accounts use it, so the UI can warn before deleting. */
const SELECT_WITH_USAGE = `
	SELECT t.id, t.name, t.sort_order, t.archived, t.created_at, t.updated_at,
	       COUNT(a.id) AS account_count
	FROM account_types t
	LEFT JOIN accounts a ON a.type_id = t.id AND a.user_id = t.user_id
`;

export const accountTypeRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { includeArchived } = parseQuery(c, listQuerySchema);
		const { results } = await c.env.DB.prepare(
			`${SELECT_WITH_USAGE}
			 WHERE t.user_id = ? AND (? = 1 OR t.archived = 0)
			 GROUP BY t.id
			 ORDER BY t.archived ASC, t.sort_order ASC, t.name COLLATE NOCASE ASC`,
		)
			.bind(c.get('userId'), includeArchived ? 1 : 0)
			.all<AccountTypeRow>();

		return c.json({ accountTypes: results.map(toAccountType) });
	})
	.post('/', async (c) => {
		const input = await parseJson(c, accountTypeCreateSchema);
		const userId = c.get('userId');
		const id = newId('atp');

		try {
			await c.env.DB.prepare('INSERT INTO account_types (id, user_id, name, sort_order) VALUES (?, ?, ?, ?)')
				.bind(id, userId, input.name, input.sortOrder)
				.run();
		} catch (error) {
			if (isUniqueViolation(error)) throw conflict(DUPLICATE_MESSAGE);
			throw error;
		}

		const row = await c.env.DB.prepare(`${SELECT_WITH_USAGE} WHERE t.id = ? AND t.user_id = ? GROUP BY t.id`)
			.bind(id, userId)
			.first<AccountTypeRow>();

		return c.json({ accountType: toAccountType(row!) }, 201);
	})
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, accountTypeUpdateSchema);

		const { clause, values } = buildUpdate({
			name: input.name,
			sort_order: input.sortOrder,
			archived: toSqliteBool(input.archived),
		});

		let changes = 0;
		try {
			// Renaming is safe and instant: accounts reference the type by id, so
			// every account showing this type follows the new name.
			const result = await c.env.DB.prepare(`UPDATE account_types SET ${clause}, updated_at = ${NOW_SQL} WHERE id = ? AND user_id = ?`)
				.bind(...values, id, userId)
				.run();
			changes = result.meta.changes ?? 0;
		} catch (error) {
			if (isUniqueViolation(error)) throw conflict(DUPLICATE_MESSAGE);
			throw error;
		}

		if (!changes) throw notFound('Account type not found.');
		await invalidateAllSummaries(c.env.CACHE, userId);

		const row = await c.env.DB.prepare(`${SELECT_WITH_USAGE} WHERE t.id = ? AND t.user_id = ? GROUP BY t.id`)
			.bind(id, userId)
			.first<AccountTypeRow>();

		return c.json({ accountType: toAccountType(row!) });
	})
	.delete('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');

		const existing = await c.env.DB.prepare(
			`SELECT t.id, COUNT(a.id) AS account_count
			 FROM account_types t
			 LEFT JOIN accounts a ON a.type_id = t.id AND a.user_id = t.user_id
			 WHERE t.id = ? AND t.user_id = ?
			 GROUP BY t.id`,
		)
			.bind(id, userId)
			.first<{ id: string; account_count: number }>();

		if (!existing) throw notFound('Account type not found.');

		// An account must always have a type, so a type in use cannot simply go.
		// Archiving hides it from the picker while leaving those accounts intact.
		if (existing.account_count > 0) {
			throw conflict(
				`${existing.account_count} account(s) still use this type. Move them to another type first, or archive this one to hide it.`,
			);
		}

		await c.env.DB.prepare('DELETE FROM account_types WHERE id = ? AND user_id = ?').bind(id, userId).run();
		return c.body(null, 204);
	});
