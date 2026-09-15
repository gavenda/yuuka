import { Hono } from 'hono';
import { notFound } from '../errors';
import { buildUpdate, NOW_SQL } from '../sql';
import { parseJson } from '../validate';
import { requireAuth } from '../middleware/auth';
import { toSettings, type SettingsRow } from '../mappers';
import { settingsUpdateSchema } from '../schemas';
import type { AppEnv } from '../types';

/** Per-user preferences. The row always exists by the time this runs — `requireAuth` provisions it. */
export const settingsRoutes = new Hono<AppEnv>()
	.use('*', requireAuth)
	.get('/', async (c) => {
		const row = await c.env.DB.prepare('SELECT display_currency, created_at, updated_at FROM users WHERE id = ?')
			.bind(c.get('userId'))
			.first<SettingsRow>();

		if (!row) throw notFound('Settings not found.');
		return c.json({ settings: toSettings(row) });
	})
	.patch('/', async (c) => {
		const input = await parseJson(c, settingsUpdateSchema);
		const userId = c.get('userId');

		const { clause, values } = buildUpdate({ display_currency: input.displayCurrency });

		await c.env.DB.prepare(`UPDATE users SET ${clause}, updated_at = ${NOW_SQL} WHERE id = ?`)
			.bind(...values, userId)
			.run();

		const row = await c.env.DB.prepare('SELECT display_currency, created_at, updated_at FROM users WHERE id = ?')
			.bind(userId)
			.first<SettingsRow>();

		return c.json({ settings: toSettings(row!) });
	});
