import { Hono } from 'hono';
import { conflict, notFound } from '../errors';
import { alreadyOwned, newId } from '../ids';
import { buildUpdate, isUniqueViolation, NOW_SQL } from '../sql';
import { parseJson } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toTag, type TagRow } from '../mappers';
import { tagCreateSchema, tagUpdateSchema } from '../schemas';
import type { AppEnv } from '../types';

const DUPLICATE_MESSAGE = 'A tag with that name already exists.';

/**
 * A transfer is one movement recorded on two rows, both wearing its tags, so
 * counting rows would say two for what the list shows as one. Counting the
 * transfer id where there is one, and the row's own id where there is not,
 * counts each movement once.
 */
const SELECT_WITH_COUNT = `
	SELECT g.*,
	       (SELECT COUNT(DISTINCT COALESCE(t.transfer_id, t.id))
	        FROM transaction_tags tt JOIN transactions t ON t.id = tt.transaction_id
	        WHERE tt.tag_id = g.id) AS transaction_count
	FROM tags g
`;

export const tagRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { results } = await c.env.DB.prepare(`${SELECT_WITH_COUNT} WHERE g.user_id = ? ORDER BY g.name COLLATE NOCASE ASC`)
			.bind(c.get('userId'))
			.all<TagRow>();

		return c.json({ tags: results.map(toTag) });
	})
	.post('/', async (c) => {
		const input = await parseJson(c, tagCreateSchema);
		const userId = c.get('userId');
		const id = input.id ?? newId('tag');

		// A queued create can arrive twice — the batch landed but its answer did
		// not. Because the client named the row, the repeat is recognisable, and
		// answering with what is already there is what makes a replay safe.
		if (input.id && (await alreadyOwned(c.env.DB, 'tags', id, userId))) {
			const existing = await c.env.DB.prepare(`${SELECT_WITH_COUNT} WHERE g.id = ? AND g.user_id = ?`).bind(id, userId).first<TagRow>();
			return c.json({ tag: toTag(existing!) });
		}

		try {
			await c.env.DB.prepare('INSERT INTO tags (id, user_id, name, color) VALUES (?, ?, ?, ?)')
				.bind(id, userId, input.name, input.color)
				.run();
		} catch (error) {
			if (isUniqueViolation(error)) throw conflict(DUPLICATE_MESSAGE);
			throw error;
		}

		const row = await c.env.DB.prepare(`${SELECT_WITH_COUNT} WHERE g.id = ? AND g.user_id = ?`).bind(id, userId).first<TagRow>();
		return c.json({ tag: toTag(row!) }, 201);
	})
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, tagUpdateSchema);

		const { clause, values } = buildUpdate({ name: input.name, color: input.color });

		let changes = 0;
		try {
			const result = await c.env.DB.prepare(`UPDATE tags SET ${clause}, updated_at = ${NOW_SQL} WHERE id = ? AND user_id = ?`)
				.bind(...values, id, userId)
				.run();
			changes = result.meta.changes ?? 0;
		} catch (error) {
			if (isUniqueViolation(error)) throw conflict(DUPLICATE_MESSAGE);
			throw error;
		}

		if (!changes) throw notFound('Tag not found.');

		const row = await c.env.DB.prepare(`${SELECT_WITH_COUNT} WHERE g.id = ? AND g.user_id = ?`).bind(id, userId).first<TagRow>();
		return c.json({ tag: toTag(row!) });
	})
	.delete('/:id', async (c) => {
		// Only the labels come off: the schema cascades the links, never the
		// transactions, so deleting a tag cannot touch the ledger. Tags change no
		// figure, which is also why the cached summaries stay valid.
		const result = await c.env.DB.prepare('DELETE FROM tags WHERE id = ? AND user_id = ?').bind(c.req.param('id'), c.get('userId')).run();
		if (!result.meta.changes) throw notFound('Tag not found.');

		return c.body(null, 204);
	});
