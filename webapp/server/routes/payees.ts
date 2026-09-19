import { Hono } from 'hono';
import { notFound } from '../errors';
import { parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toPayee, type PayeeRow } from '../mappers';
import { payeeQuerySchema } from '../schemas';
import type { AppEnv } from '../types';

/**
 * Remembered payees, for the form's autosuggest.
 *
 * Each row carries the account, category and notes it was last filed under, so
 * the client can fill the form from a single choice without another round trip.
 * Names are resolved here too, so a suggestion can be shown with context rather
 * than as a bare string.
 */
const SELECT_ENRICHED = `
	SELECT h.id, h.payee, h.kind, h.account_id, h.to_account_id, h.category_id, h.notes,
	       h.used_count, h.last_used_at,
	       a.name  AS account_name,
	       ta.name AS to_account_name,
	       c.name  AS category_name,
	       c.color AS category_color
	FROM payee_history h
	LEFT JOIN accounts   a  ON a.id  = h.account_id    AND a.user_id  = h.user_id
	LEFT JOIN accounts   ta ON ta.id = h.to_account_id AND ta.user_id = h.user_id
	LEFT JOIN categories c  ON c.id  = h.category_id   AND c.user_id  = h.user_id
`;

export const payeeRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { search, limit } = parseQuery(c, payeeQuerySchema);

		const conditions = ['h.user_id = ?'];
		const values: unknown[] = [c.get('userId')];

		if (search) {
			// Prefix and substring both match, but the ordering below floats the
			// prefix matches to the top — typing "cor" should offer "Corner
			// Market" before "Tesco Corner".
			conditions.push('h.payee LIKE ?');
			values.push(`%${search}%`);
		}

		const { results } = await c.env.DB.prepare(
			`${SELECT_ENRICHED}
			 WHERE ${conditions.join(' AND ')}
			 ORDER BY ${search ? '(h.payee LIKE ? COLLATE NOCASE) DESC,' : ''} h.used_count DESC, h.last_used_at DESC
			 LIMIT ?`,
		)
			.bind(...values, ...(search ? [`${search}%`] : []), limit)
			.all<PayeeRow>();

		return c.json({ payees: results.map(toPayee) });
	})
	.delete('/:id', async (c) => {
		const result = await c.env.DB.prepare('DELETE FROM payee_history WHERE id = ? AND user_id = ?')
			.bind(c.req.param('id'), c.get('userId'))
			.run();

		if (!result.meta.changes) throw notFound('Payee not found.');
		return c.body(null, 204);
	});
