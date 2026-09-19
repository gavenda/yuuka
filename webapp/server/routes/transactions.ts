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

/**
 * Each row carries `running_balance`: its own account's balance immediately
 * after it posted. That total is computed over every one of the user's
 * transactions for the account, not just whichever ones a search, category or
 * month filter would leave in the final result — narrowing the list must not
 * change what "the balance after this one" means. The subquery therefore takes
 * its own `user_id` parameter rather than reusing the outer query's filters.
 */
const SELECT_ENRICHED = `
	SELECT t.id, t.account_id, t.category_id, t.amount, t.occurred_on, t.payee, t.notes,
	       t.transfer_id, t.automated, t.created_at, t.updated_at,
	       a.name AS account_name, c.name AS category_name, c.color AS category_color,
	       b.running_balance
	FROM transactions t
	JOIN accounts a ON a.id = t.account_id
	LEFT JOIN categories c ON c.id = t.category_id
	JOIN (
		SELECT t2.id,
		       a2.starting_balance + SUM(t2.amount) OVER (
		           PARTITION BY t2.account_id
		           ORDER BY t2.occurred_on ASC, t2.created_at ASC
		           ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
		       ) AS running_balance
		FROM transactions t2
		JOIN accounts a2 ON a2.id = t2.account_id
		WHERE t2.user_id = ?
	) b ON b.id = t.id
`;

const MISSING_REFERENCE = 'Unknown account or category.';
const WRONG_SCOPE = 'That category cannot be used here. Spending and income use standard categories; transfers use Cashflow categories.';

/** The synthetic payee "Save the Change" round-ups are posted under. Never fed into `rememberPayee` — it's derived, not typed. */
const ROUND_UP_PAYEE = 'Save the Change';

interface RoundUpEligibility {
	roundTo: number;
	destinationAccountId: string;
	/** The category both legs post under, from the rule. Null stays uncategorised. */
	categoryId: string | null;
}

/**
 * Reads the source account's opt-in and the user's rule in one query. A
 * missing rule row (never configured) and a missing destination both read as
 * null, so both cases fall through to "not eligible" without a separate
 * existence check.
 */
async function loadRoundUpEligibility(db: D1Database, userId: string, accountId: string): Promise<RoundUpEligibility | null> {
	const row = await db
		.prepare(
			`SELECT a.round_up_source AS account_opt_in, r.enabled AS rule_enabled, r.round_to AS round_to,
			        r.destination_account_id AS destination_account_id, r.category_id AS category_id
			 FROM accounts a
			 LEFT JOIN round_up_rules r ON r.user_id = a.user_id
			 WHERE a.id = ? AND a.user_id = ?`,
		)
		.bind(accountId, userId)
		.first<{
			account_opt_in: number;
			rule_enabled: number | null;
			round_to: number | null;
			destination_account_id: string | null;
			category_id: string | null;
		}>();

	if (!row || row.account_opt_in !== 1 || row.rule_enabled !== 1 || !row.destination_account_id) return null;
	// This purchase's own account is the destination — nothing sensible to
	// move for this one purchase, even though the account may still be a
	// legitimate source for purchases made elsewhere.
	if (row.destination_account_id === accountId) return null;

	return { roundTo: row.round_to!, destinationAccountId: row.destination_account_id, categoryId: row.category_id };
}

/** The gap between `amount` and the next whole `roundTo` multiple above it. */
function computeRoundUp(amount: number, roundTo: number): number {
	const absolute = Math.abs(amount);
	return Math.ceil(absolute / roundTo) * roundTo - absolute;
}

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
export async function loadOne(db: D1Database, userId: string, id: string): Promise<TransactionRow | null> {
	return await db.prepare(`${SELECT_ENRICHED} WHERE t.id = ? AND t.user_id = ?`).bind(userId, id, userId).first<TransactionRow>();
}

export const transactionRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const query = parseQuery(c, transactionQuerySchema);
		const userId = c.get('userId');

		// Ownership is the first condition on every listing, never an optional filter.
		const conditions: string[] = ['t.user_id = ?'];
		const values: unknown[] = [userId];

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
			).bind(userId, ...values, query.limit, query.offset),
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

		// Only an ordinary expense — never a transfer, an edit or a balance
		// adjustment — can trigger "Save the Change"; this is the one place
		// that reads `round_up_rules`.
		let roundUpId: string | null = null;
		if (input.amount < 0) {
			const eligibility = await loadRoundUpEligibility(c.env.DB, userId, input.accountId);
			if (eligibility) {
				const roundUpAmount = computeRoundUp(input.amount, eligibility.roundTo);
				if (roundUpAmount > 0) {
					const transferId = newId('tfr');
					const sourceLegId = newId('txn');
					const destinationLegId = newId('txn');

					// Both legs carry the rule's category, same as an ordinary transfer
					// between the user's own accounts — a round-up is one, so it takes
					// a transfer-scope category rather than a standard one.
					const [outflow, inflow] = await c.env.DB.batch([
						c.env.DB.prepare(INSERT_GUARDED).bind(
							sourceLegId,
							userId,
							input.accountId,
							eligibility.categoryId,
							-roundUpAmount,
							input.occurredOn,
							ROUND_UP_PAYEE,
							'',
							transferId,
							input.accountId,
							userId,
							eligibility.categoryId,
							eligibility.categoryId,
							userId,
							'transfer',
						),
						c.env.DB.prepare(INSERT_GUARDED).bind(
							destinationLegId,
							userId,
							eligibility.destinationAccountId,
							eligibility.categoryId,
							roundUpAmount,
							input.occurredOn,
							ROUND_UP_PAYEE,
							'',
							transferId,
							eligibility.destinationAccountId,
							userId,
							eligibility.categoryId,
							eligibility.categoryId,
							userId,
							'transfer',
						),
					]);

					// The destination account may have been deleted between the read
					// above and this write; treat that race as "no round-up" rather
					// than failing the purchase that triggered it.
					if (outflow.meta.changes && inflow.meta.changes) {
						roundUpId = destinationLegId;
					} else {
						await c.env.DB.prepare('DELETE FROM transactions WHERE transfer_id = ? AND user_id = ?').bind(transferId, userId).run();
					}
				}
			}
		}

		await invalidateSummaries(c.env.CACHE, userId, [monthOf(input.occurredOn)]);

		// A named transaction teaches the form what to offer next time. The
		// round-up's payee is synthetic, so it is never remembered — the same
		// way a transfer's derived "From → To" name isn't.
		await rememberPayee(c.env.DB, userId, {
			payee: input.payee,
			kind: payeeKind(input.amount, null),
			accountId: input.accountId,
			categoryId: input.categoryId,
			notes: input.notes,
		});

		return c.json(
			{
				transaction: toTransaction((await loadOne(c.env.DB, userId, id))!),
				roundUp: roundUpId ? toTransaction((await loadOne(c.env.DB, userId, roundUpId))!) : null,
			},
			201,
		);
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
			.bind(userId, transferId, userId)
			.all<TransactionRow>();

		return c.json({ transferId, transactions: results.map(toTransaction) }, 201);
	})
	.patch('/transfer/:transferId', async (c) => {
		const transferId = c.req.param('transferId');
		const userId = c.get('userId');
		const input = await parseJson(c, transferCreateSchema);

		// Both legs, ordered outflow-then-inflow the same way creation leaves them.
		// Kept in full so a rejected edit can restore them verbatim, not just their id.
		const { results: existing } = await c.env.DB.prepare(
			'SELECT id, account_id, category_id, amount, occurred_on, payee, notes FROM transactions WHERE transfer_id = ? AND user_id = ? ORDER BY amount ASC',
		)
			.bind(transferId, userId)
			.all<{
				id: string;
				account_id: string;
				category_id: string | null;
				amount: number;
				occurred_on: string;
				payee: string;
				notes: string;
			}>();

		if (existing.length !== 2) throw notFound('Transfer not found.');

		let name = input.payee;
		if (!name) {
			const { results } = await c.env.DB.prepare('SELECT id, name FROM accounts WHERE user_id = ? AND id IN (?, ?)')
				.bind(userId, input.fromAccountId, input.toAccountId)
				.all<{ id: string; name: string }>();

			const names = new Map(results.map((row) => [row.id, row.name]));
			const from = names.get(input.fromAccountId);
			const to = names.get(input.toAccountId);
			name = from && to ? `${from} → ${to}` : 'Transfer';
		}

		const [outflow, inflow] = existing;
		const legs = [
			{ id: outflow.id, accountId: input.fromAccountId, amount: -input.amount },
			{ id: inflow.id, accountId: input.toAccountId, amount: input.amount },
		];

		// Same ownership guard as creation, applied per leg: re-pointing a leg to
		// an account or category the caller does not hold is rejected in the same
		// statement that would otherwise write it.
		const results = await c.env.DB.batch(
			legs.map((leg) =>
				c.env.DB.prepare(
					`UPDATE transactions SET account_id = ?, category_id = ?, amount = ?, occurred_on = ?, payee = ?, notes = ?, updated_at = ${NOW_SQL}
					 WHERE id = ? AND user_id = ?
					   AND EXISTS (SELECT 1 FROM accounts WHERE id = ? AND user_id = ?)
					   AND (? IS NULL OR EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND applies_to = 'transfer'))`,
				).bind(
					leg.accountId,
					input.categoryId,
					leg.amount,
					input.occurredOn,
					name,
					input.notes,
					leg.id,
					userId,
					leg.accountId,
					userId,
					input.categoryId,
					input.categoryId,
					userId,
				),
			),
		);

		// A batch does not roll back just because a guard matched zero rows, so a
		// rejected edit could otherwise leave one leg updated and the other not.
		// Put both back exactly as they were rather than risk a desynced pair.
		if (!results[0].meta.changes || !results[1].meta.changes) {
			await c.env.DB.batch(
				existing.map((leg) =>
					c.env.DB.prepare(
						`UPDATE transactions SET account_id = ?, category_id = ?, amount = ?, occurred_on = ?, payee = ?, notes = ?, updated_at = ${NOW_SQL}
						 WHERE id = ? AND user_id = ?`,
					).bind(leg.account_id, leg.category_id, leg.amount, leg.occurred_on, leg.payee, leg.notes, leg.id, userId),
				),
			);
			throw badRequest(input.categoryId ? WRONG_SCOPE : MISSING_REFERENCE);
		}

		await invalidateSummaries(c.env.CACHE, userId, [monthOf(outflow.occurred_on), monthOf(input.occurredOn)]);

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

		const { results: updated } = await c.env.DB.prepare(
			`${SELECT_ENRICHED} WHERE t.transfer_id = ? AND t.user_id = ? ORDER BY t.amount ASC`,
		)
			.bind(userId, transferId, userId)
			.all<TransactionRow>();

		return c.json({ transferId, transactions: updated.map(toTransaction) });
	})
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, transactionUpdateSchema);

		const existing = await c.env.DB.prepare('SELECT occurred_on, automated FROM transactions WHERE id = ? AND user_id = ?')
			.bind(id, userId)
			.first<{ occurred_on: string; automated: number }>();
		if (!existing) throw notFound('Transaction not found.');

		// A subscription posts at 00:00 UTC and nobody chooses that. The date can
		// still move; a time of day cannot be added to it.
		if (existing.automated === 1 && input.occurredOn !== undefined && input.occurredOn.length > 10) {
			throw badRequest('The time of an automated transaction cannot be changed.');
		}

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
