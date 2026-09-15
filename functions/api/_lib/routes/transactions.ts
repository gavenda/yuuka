import { Hono } from 'hono';
import { invalidateSummaries } from '../cache';
import { payeeKind, rememberPayee } from '../payees';
import { badRequest, notFound } from '../errors';
import { monthOf, monthRange } from '../dates';
import { newId } from '../ids';
import { buildUpdate, NOW_SQL, toSqliteBool } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toTransaction, type TransactionRow } from '../mappers';
import { transactionCreateSchema, transactionQuerySchema, transactionUpdateSchema, transferCreateSchema } from '../schemas';
import type { AppEnv } from '../types';

const SELECT_ENRICHED = `
	SELECT t.id, t.account_id, t.category_id, t.amount, t.occurred_on, t.payee, t.notes,
	       t.transfer_id, t.created_at, t.updated_at,
	       a.name AS account_name, c.name AS category_name, c.color AS category_color
	FROM transactions t
	JOIN accounts a ON a.id = t.account_id
	LEFT JOIN categories c ON c.id = t.category_id
`;

const MISSING_REFERENCE = 'Unknown account or category.';
const WRONG_SCOPE = 'That category cannot be used here. Spending and income use standard categories; transfers use Cashflow categories.';

/**
 * Inserts only if the account, and the category when one is given, belong to
 * the same user and the category may be used here. Doing it as one statement
 * means ownership cannot be checked and then invalidated before the write
 * lands, and a reference to someone else's row is indistinguishable from one
 * that does not exist.
 *
 * The last parameter is the scope the category must have: spending and income
 * take 'standard' categories, transfers take 'transfer' ones. A transaction has
 * a single category column, so it is always one or the other, never both.
 */
const INSERT_GUARDED = `
	INSERT INTO transactions (id, user_id, account_id, category_id, amount, occurred_on, payee, notes, transfer_id)
	SELECT ?, ?, ?, ?, ?, ?, ?, ?, ?
	WHERE EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?)
	  AND (? IS NULL OR EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND applies_to = ?))
`;

/** Re-reads a row with its joined account and category labels. */
async function loadOne(db: D1Database, userId: string, id: string): Promise<TransactionRow | null> {
	return await db.prepare(`${SELECT_ENRICHED} WHERE t.id = ? AND t.user_id = ?`).bind(id, userId).first<TransactionRow>();
}

export const transactionRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const query = parseQuery(c, transactionQuerySchema);

		// Ownership is the first condition on every listing, never an optional filter.
		const conditions: string[] = ['t.user_id = ?'];
		const values: unknown[] = [c.get('userId')];

		if (query.month) {
			const { start, end } = monthRange(query.month);
			conditions.push('t.occurred_on >= ? AND t.occurred_on < ?');
			values.push(start, end);
		}
		if (query.from) {
			conditions.push('t.occurred_on >= ?');
			values.push(query.from);
		}
		if (query.to) {
			conditions.push('t.occurred_on <= ?');
			values.push(query.to);
		}
		if (query.accountId) {
			conditions.push('t.account_id = ?');
			values.push(query.accountId);
		}
		if (query.categoryId) {
			conditions.push(query.categoryId === 'none' ? 't.category_id IS NULL' : 't.category_id = ?');
			if (query.categoryId !== 'none') values.push(query.categoryId);
		}
		if (query.search) {
			conditions.push('(t.payee LIKE ? OR t.notes LIKE ?)');
			const pattern = `%${query.search}%`;
			values.push(pattern, pattern);
		}

		const where = `WHERE ${conditions.join(' AND ')}`;

		const [page, count] = await c.env.DB.batch<TransactionRow | { total: number }>([
			c.env.DB.prepare(
				`${SELECT_ENRICHED} ${where}
				 ORDER BY t.occurred_on DESC, t.created_at DESC
				 LIMIT ? OFFSET ?`,
			).bind(...values, query.limit, query.offset),
			c.env.DB.prepare(`SELECT COUNT(*) AS total FROM transactions t ${where}`).bind(...values),
		]);

		const total = (count.results[0] as { total: number } | undefined)?.total ?? 0;

		return c.json({
			transactions: (page.results as TransactionRow[]).map(toTransaction),
			total,
			limit: query.limit,
			offset: query.offset,
		});
	})
	.post('/', async (c) => {
		const input = await parseJson(c, transactionCreateSchema);
		const userId = c.get('userId');
		const id = newId('txn');

		const result = await c.env.DB.prepare(INSERT_GUARDED)
			.bind(
				id,
				userId,
				input.accountId,
				input.categoryId,
				input.amount,
				input.occurredOn,
				input.payee,
				input.notes,
				null,
				input.accountId,
				userId,
				input.categoryId,
				input.categoryId,
				userId,
				'standard',
			)
			.run();

		if (!result.meta.changes) throw badRequest(input.categoryId ? WRONG_SCOPE : MISSING_REFERENCE);

		await invalidateSummaries(c.env.CACHE, userId, [monthOf(input.occurredOn)]);

		// A named transaction teaches the form what to offer next time.
		await rememberPayee(c.env.DB, userId, {
			payee: input.payee,
			kind: payeeKind(input.amount, null),
			accountId: input.accountId,
			categoryId: input.categoryId,
			notes: input.notes,
		});

		return c.json({ transaction: toTransaction((await loadOne(c.env.DB, userId, id))!) }, 201);
	})
	.post('/transfer', async (c) => {
		const input = await parseJson(c, transferCreateSchema);
		const userId = c.get('userId');
		const transferId = newId('tfr');

		// A transfer's payee reads as its name. Left blank it describes itself,
		// which is more use in a list than the word "Transfer" repeated.
		let name = input.payee;
		if (!name) {
			const { results } = await c.env.DB.prepare('SELECT id, name FROM accounts WHERE user_id = ? AND id IN (?, ?)')
				.bind(userId, input.fromAccountId, input.toAccountId)
				.all<{ id: string; name: string }>();

			const names = new Map(results.map((row) => [row.id, row.name]));
			const from = names.get(input.fromAccountId);
			const to = names.get(input.toAccountId);

			// If either is missing the guarded insert below rejects the request
			// anyway; fall back rather than composing a half-name.
			name = from && to ? `${from} → ${to}` : 'Transfer';
		}

		// Both legs carry the same category: a transfer is one movement recorded
		// twice, and categorising it lets an investment contribution be budgeted.
		// It is still excluded from income and spending — moving your own money
		// is neither.
		const legs = [
			{ id: newId('txn'), accountId: input.fromAccountId, amount: -input.amount },
			{ id: newId('txn'), accountId: input.toAccountId, amount: input.amount },
		];

		const [outflow, inflow] = await c.env.DB.batch(
			legs.map((leg) =>
				c.env.DB.prepare(INSERT_GUARDED).bind(
					leg.id,
					userId,
					leg.accountId,
					input.categoryId,
					leg.amount,
					input.occurredOn,
					name,
					input.notes,
					transferId,
					leg.accountId,
					userId,
					input.categoryId,
					input.categoryId,
					userId,
					'transfer',
				),
			),
		);

		// Either account not being the caller's leaves a half-written transfer, so
		// undo it rather than leaving one side of the movement behind.
		if (!outflow.meta.changes || !inflow.meta.changes) {
			await c.env.DB.prepare('DELETE FROM transactions WHERE transfer_id = ? AND user_id = ?').bind(transferId, userId).run();
			throw badRequest(input.categoryId ? WRONG_SCOPE : MISSING_REFERENCE);
		}

		await invalidateSummaries(c.env.CACHE, userId, [monthOf(input.occurredOn)]);

		// Only a name the user actually typed is worth remembering — the composed
		// "From → To" is derived, so suggesting it back would be noise.
		if (input.payee) {
			await rememberPayee(c.env.DB, userId, {
				payee: input.payee,
				kind: 'transfer',
				accountId: input.fromAccountId,
				toAccountId: input.toAccountId,
				categoryId: input.categoryId,
				notes: input.notes,
			});
		}

		const { results } = await c.env.DB.prepare(`${SELECT_ENRICHED} WHERE t.transfer_id = ? AND t.user_id = ? ORDER BY t.amount ASC`)
			.bind(transferId, userId)
			.all<TransactionRow>();

		return c.json({ transferId, transactions: results.map(toTransaction) }, 201);
	})
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, transactionUpdateSchema);

		const existing = await c.env.DB.prepare('SELECT occurred_on FROM transactions WHERE id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ occurred_on: string }>();
		if (!existing) throw notFound('Transaction not found.');

		const { clause, values } = buildUpdate({
			account_id: input.accountId,
			category_id: input.categoryId,
			amount: input.amount,
			occurred_on: input.occurredOn,
			payee: input.payee,
			notes: input.notes,
		});

		// Re-pointing a transaction is only allowed at rows the caller owns; the
		// guards are no-ops when the field is not being changed.
		const accountGuard = input.accountId ?? null;
		const categoryGuard = input.categoryId ?? null;

		const result = await c.env.DB.prepare(
			`UPDATE transactions SET ${clause}, updated_at = ${NOW_SQL}
			 WHERE id = ? AND user_id = ?
			   AND (? IS NULL OR EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?))
			   AND (
			       ? IS NULL
			       OR EXISTS (
			           SELECT 1 FROM categories
			           WHERE id = ? AND user_id = ?
			             AND applies_to = CASE WHEN transactions.transfer_id IS NULL THEN 'standard' ELSE 'transfer' END
			       )
			   )`,
		)
			.bind(...values, id, userId, accountGuard, accountGuard, userId, categoryGuard, categoryGuard, userId)
			.run();

		// The row exists and belongs to the caller, so a no-op means a reference
		// pointed somewhere they cannot reach, or a category of the wrong scope.
		if (!result.meta.changes) throw badRequest(input.categoryId ? WRONG_SCOPE : MISSING_REFERENCE);

		// Moving a transaction across a month boundary makes two months stale.
		await invalidateSummaries(c.env.CACHE, userId, [monthOf(existing.occurred_on), monthOf(input.occurredOn ?? existing.occurred_on)]);

		const updated = (await loadOne(c.env.DB, userId, id))!;

		// Editing is saving, so the history follows the row's final state rather
		// than only what the patch happened to mention.
		await rememberPayee(c.env.DB, userId, {
			payee: updated.payee,
			kind: payeeKind(updated.amount, updated.transfer_id),
			accountId: updated.account_id,
			categoryId: updated.category_id,
			notes: updated.notes,
		});

		return c.json({ transaction: toTransaction(updated) });
	})
	.delete('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');

		const existing = await c.env.DB.prepare('SELECT occurred_on, transfer_id FROM transactions WHERE id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ occurred_on: string; transfer_id: string | null }>();

		if (!existing) throw notFound('Transaction not found.');

		// A transfer is one movement recorded twice; deleting half would leave
		// both accounts wrong, so take the whole pair.
		if (existing.transfer_id) {
			await c.env.DB.prepare('DELETE FROM transactions WHERE transfer_id = ? AND user_id = ?').bind(existing.transfer_id, userId).run();
		} else {
			await c.env.DB.prepare('DELETE FROM transactions WHERE id = ? AND user_id = ?').bind(id, userId).run();
		}

		await invalidateSummaries(c.env.CACHE, userId, [monthOf(existing.occurred_on)]);
		return c.body(null, 204);
	});
