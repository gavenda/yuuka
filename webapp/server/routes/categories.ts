import { Hono } from 'hono';
import { invalidateAllSummaries } from '../cache';
import { badRequest, conflict, notFound } from '../errors';
import { newId } from '../ids';
import { buildUpdate, isUniqueViolation, NOW_SQL, toSqliteBool } from '../sql';
import { parseJson, parseQuery } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toCategory, type CategoryRow } from '../mappers';
import { categoryCreateSchema, categoryUpdateSchema, listQuerySchema } from '../schemas';
import type { AppEnv } from '../types';

const DUPLICATE_MESSAGE = 'A category with that name already exists for this kind.';

export const categoryRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const { includeArchived } = parseQuery(c, listQuerySchema);
		const { results } = await c.env.DB.prepare(
			// Grouped so the caller can build the tree in one pass: each kind, then
			// each family ordered by the parent's own position, then the parent
			// itself, then its children. Ordering families by parent id would have
			// worked for grouping but put them in an arbitrary order, since ids
			// are hashes.
			`SELECT c.* FROM categories c
			 LEFT JOIN categories p ON p.id = c.parent_id
			 WHERE c.user_id = ? AND (? = 1 OR c.archived = 0)
			 ORDER BY c.kind ASC,
			          COALESCE(p.sort_order, c.sort_order) ASC,
			          COALESCE(p.name, c.name) COLLATE NOCASE ASC,
			          c.parent_id IS NOT NULL ASC,
			          c.sort_order ASC,
			          c.name COLLATE NOCASE ASC`,
		)
			.bind(c.get('userId'), includeArchived ? 1 : 0)
			.all<CategoryRow>();

		return c.json({ categories: results.map(toCategory) });
	})
	.post('/', async (c) => {
		const input = await parseJson(c, categoryCreateSchema);
		const userId = c.get('userId');
		const id = newId('cat');

		// A child inherits its parent's kind, so the two can never drift apart — an
		// "Investments" under "Cashflow" is always a transfer category. The
		// `categories_hierarchy_*` triggers enforce the same thing; this is what
		// turns it into a quiet inheritance rather than an error the caller has to
		// avoid.
		let kind = input.kind;

		if (input.parentId) {
			const parent = await c.env.DB.prepare('SELECT kind, parent_id FROM categories WHERE id = ? AND user_id = ?')
				.bind(input.parentId, userId)
				.first<{ kind: typeof kind; parent_id: string | null }>();

			if (!parent) throw badRequest('Unknown parent category.');
			// One level only: a subcategory cannot itself be a parent.
			if (parent.parent_id) throw badRequest('Categories can only nest one level deep.');

			kind = parent.kind;
		}

		try {
			// Names are unique among siblings, so two parents may each have an
			// "Other", and one person's "Groceries" never collides with another's.
			// The EXISTS guard re-checks the parent in the statement that writes.
			const result = await c.env.DB.prepare(
				`INSERT INTO categories (id, user_id, name, kind, color, sort_order, parent_id)
				 SELECT ?, ?, ?, ?, ?, ?, ?
				 WHERE ? IS NULL
				    OR EXISTS (SELECT 1 FROM categories WHERE id = ? AND user_id = ? AND parent_id IS NULL)`,
			)
				.bind(id, userId, input.name, kind, input.color, input.sortOrder, input.parentId, input.parentId, input.parentId, userId)
				.run();

			if (!result.meta.changes) throw badRequest('Unknown parent category.');
		} catch (error) {
			if (isUniqueViolation(error)) throw conflict(DUPLICATE_MESSAGE);
			throw error;
		}

		const row = await c.env.DB.prepare('SELECT * FROM categories WHERE id = ? AND user_id = ?').bind(id, userId).first<CategoryRow>();
		return c.json({ category: toCategory(row!) }, 201);
	})
	.patch('/:id', async (c) => {
		const id = c.req.param('id');
		const userId = c.get('userId');
		const input = await parseJson(c, categoryUpdateSchema);

		const { clause, values } = buildUpdate({
			name: input.name,
			kind: input.kind,
			color: input.color,
			sort_order: input.sortOrder,
			archived: toSqliteBool(input.archived),
		});

		let changes = 0;
		try {
			const result = await c.env.DB.prepare(`UPDATE categories SET ${clause}, updated_at = ${NOW_SQL} WHERE id = ? AND user_id = ?`)
				.bind(...values, id, userId)
				.run();
			changes = result.meta.changes ?? 0;
		} catch (error) {
			if (isUniqueViolation(error)) throw conflict(DUPLICATE_MESSAGE);
			throw error;
		}

		if (!changes) throw notFound('Category not found.');
		await invalidateAllSummaries(c.env.CACHE, userId);

		const row = await c.env.DB.prepare('SELECT * FROM categories WHERE id = ? AND user_id = ?').bind(id, userId).first<CategoryRow>();
		return c.json({ category: toCategory(row!) });
	})
	.delete('/:id', async (c) => {
		const userId = c.get('userId');

		// Transactions survive: the schema nulls their category_id rather than
		// cascading, so deleting a category never destroys spending history.
		// Subcategories do cascade — they belong to the parent, not the ledger.
		const result = await c.env.DB.prepare('DELETE FROM categories WHERE id = ? AND user_id = ?').bind(c.req.param('id'), userId).run();
		if (!result.meta.changes) throw notFound('Category not found.');

		await invalidateAllSummaries(c.env.CACHE, userId);
		return c.body(null, 204);
	});
